package com.coursework.unifiedmail.data.repository

import com.coursework.unifiedmail.data.files.PickedAttachment
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.AttachmentDao
import com.coursework.unifiedmail.data.local.AttachmentEntity
import com.coursework.unifiedmail.data.local.ConversationSummary
import com.coursework.unifiedmail.data.local.DraftDao
import com.coursework.unifiedmail.data.local.DraftEntity
import com.coursework.unifiedmail.data.local.FolderDao
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.local.MessageDao
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.local.OutboxAttachmentDao
import com.coursework.unifiedmail.data.local.OutboxAttachmentEntity
import com.coursework.unifiedmail.data.local.OutboxDao
import com.coursework.unifiedmail.data.local.OutboxEntity
import com.coursework.unifiedmail.data.local.OutboxStatus
import com.coursework.unifiedmail.data.remote.DownloadedAttachment
import com.coursework.unifiedmail.data.remote.ImapClient
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.ImapMessageInfo
import com.coursework.unifiedmail.data.remote.OutgoingAttachment
import com.coursework.unifiedmail.data.remote.OutgoingMessage
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.remote.SmtpSender
import com.coursework.unifiedmail.data.remote.describeMailError
import com.coursework.unifiedmail.data.settings.AppSettingsProvider
import com.coursework.unifiedmail.domain.threading.ConversationThreading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncOutcome {
    data class Success(val newMessageCount: Int) : SyncOutcome()
    data class Failure(val reason: String) : SyncOutcome()
}

sealed class SendOutcome {
    data object Success : SendOutcome()
    data class Failure(val reason: String) : SendOutcome()
}

sealed class MoveOutcome {
    /** [destinationFolderKey] lets a caller offer Undo without re-deriving which folder (e.g. Trash/Archive) the move actually used. */
    data class Success(val destinationFolderKey: String) : MoveOutcome()
    data class Failure(val reason: String) : MoveOutcome()
}

sealed class UndoMoveOutcome {
    data object Success : UndoMoveOutcome()
    data class Failure(val reason: String) : UndoMoveOutcome()
}

data class DraftContent(
    val accountId: String,
    val to: String,
    val cc: String,
    val bcc: String,
    val subject: String,
    val bodyHtml: String,
    val quotedHtml: String? = null,
    val inReplyToMessageIdHeader: String? = null,
    val referencesHeader: String? = null,
)

data class ComposeDraft(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val bodyHtml: String? = null,
    val inReplyToMessageIdHeader: String? = null,
    val referencesHeader: String? = null,
)

/**
 * Folder list is cached in full for every account; message sync is per-folder, driven by
 * whichever folder a screen is actually showing (INBOX is just the default/most common case,
 * not a hardcoded assumption). Also owns outgoing mail and move/trash operations — anything that
 * needs an account's password lives here so there's one place that knows how to load it.
 */
@Singleton
class MailRepository @Inject constructor(
    private val accountRepository: AccountRepository,
    private val imapClient: ImapClient,
    private val smtpSender: SmtpSender,
    private val folderDao: FolderDao,
    private val messageDao: MessageDao,
    private val outboxDao: OutboxDao,
    private val attachmentDao: AttachmentDao,
    private val outboxAttachmentDao: OutboxAttachmentDao,
    private val draftDao: DraftDao,
    // Injected (see RepositoryModule.provideApplicationScope) rather than created here so tests
    // can supply a deterministic dispatcher instead of a real background thread pool.
    private val repositoryScope: CoroutineScope,
    private val settingsProvider: AppSettingsProvider,
) {
    // Serializes syncFolder per (accountId, folderKey): SyncWorker's periodic backstop and
    // MailIdleService's IDLE-triggered sync can both target the same folder concurrently, and
    // without this a race on the stale lastSyncedUid can double-fetch and double-notify for the
    // same "new" mail. Keyed by folder id rather than a single global lock so unrelated
    // accounts/folders still sync in parallel.
    private val folderSyncMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(folderId: String): Mutex = folderSyncMutexes.computeIfAbsent(folderId) { Mutex() }

    fun observeInbox(accountId: String): Flow<List<ConversationSummary>> = observeFolder(accountId, INBOX_FOLDER_KEY)

    fun observeFolder(accountId: String, folderKey: String): Flow<List<ConversationSummary>> =
        messageDao.observeConversations(accountId, folderKey)

    fun observeConversationMessages(accountId: String, folderKey: String, conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeConversationMessages(accountId, folderKey, conversationId)

    /** INBOX conversations across every active account, newest first — the app's home view. */
    fun observeUnifiedInbox(): Flow<List<ConversationSummary>> =
        messageDao.observeUnifiedConversations(INBOX_FOLDER_KEY)

    fun searchUnifiedInbox(
        query: String,
        hasAttachmentOnly: Boolean = false,
        unreadOnly: Boolean = false,
        flaggedOnly: Boolean = false,
        fromQuery: String = "",
    ): Flow<List<ConversationSummary>> =
        messageDao.searchUnified(INBOX_FOLDER_KEY, query, hasAttachmentOnly, unreadOnly, flaggedOnly, fromQuery)

    /** Local search scoped to one account/folder — used by the per-account Inbox screen's search field. */
    fun searchFolder(
        accountId: String,
        folderKey: String,
        query: String,
        hasAttachmentOnly: Boolean = false,
        unreadOnly: Boolean = false,
        flaggedOnly: Boolean = false,
    ): Flow<List<ConversationSummary>> =
        messageDao.searchInFolder(accountId, folderKey, query, hasAttachmentOnly, unreadOnly, flaggedOnly)

    /**
     * Explicit, user-initiated fallback for when [searchFolder]'s local results don't cover it —
     * server-side search of one account/folder (same underlying IMAP search as
     * [searchServerUnified], just not fanned out across every account). Matches get upserted so
     * [searchFolder]'s own reactive query picks them up automatically.
     */
    suspend fun searchServerFolder(accountId: String, folderKey: String, query: String): SyncOutcome {
        val account = accountRepository.getAccount(accountId) ?: return SyncOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId) ?: return SyncOutcome.Failure("Missing saved credentials")
        val folder = folderDao.getForAccount(accountId).firstOrNull { localFolderKey(it.fullName) == folderKey }
            ?: return SyncOutcome.Failure("Folder not found")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        val matches = imapClient.searchFolder(imapConfig, folder.fullName, query)
            .getOrElse { return SyncOutcome.Failure(describeMailError(it)) }
        if (matches.isNotEmpty()) {
            persistMessages(accountId, folderKey, matches)
        }
        return SyncOutcome.Success(matches.size)
    }

    /**
     * Explicit, user-initiated fallback for when [searchUnifiedInbox]'s local results come up
     * empty — searches every active account's INBOX on the server (same scope local search
     * already uses) and upserts any matches into the local cache. Deliberately doesn't touch
     * `lastSyncedUid`: a match can be older than the folder's normal sync window, and this isn't
     * a real sync pass. Once upserted, [searchUnifiedInbox]'s own reactive query picks the new
     * rows up automatically — nothing else needs to know search results came from the server.
     */
    suspend fun searchServerUnified(query: String): SyncOutcome = coroutineScope {
        val accounts = accountRepository.getAllAccountsOnce().filter { it.isActive }
        val results = accounts
            .map { account -> async { account to searchAccountInbox(account.id, query) } }
            .awaitAll()

        val failures = results.mapNotNull { (account, result) ->
            result.exceptionOrNull()?.let { "${account.displayName}: ${describeMailError(it)}" }
        }
        if (failures.isNotEmpty()) {
            return@coroutineScope SyncOutcome.Failure(failures.joinToString("\n"))
        }

        val matches = results.sumOf { (_, result) -> result.getOrThrow() }
        SyncOutcome.Success(matches)
    }

    /** Returns the number of matches upserted, or a failure — see [searchServerUnified]. */
    private suspend fun searchAccountInbox(accountId: String, query: String): Result<Int> {
        val account = accountRepository.getAccount(accountId) ?: return Result.success(0)
        val password = accountRepository.getPassword(accountId) ?: return Result.success(0)
        val folder = folderDao.getForAccount(accountId)
            .firstOrNull { localFolderKey(it.fullName) == INBOX_FOLDER_KEY } ?: return Result.success(0)

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        return imapClient.searchFolder(imapConfig, folder.fullName, query).map { matches ->
            if (matches.isNotEmpty()) {
                messageDao.upsertAll(matches.map { it.toEntity(accountId, INBOX_FOLDER_KEY) })
            }
            matches.size
        }
    }

    suspend fun getInboxMessage(accountId: String, uid: Long): MessageEntity? =
        getMessage(accountId, INBOX_FOLDER_KEY, uid)

    suspend fun getMessage(accountId: String, folderKey: String, uid: Long): MessageEntity? =
        messageDao.getByUid(accountId, folderKey, uid)

    suspend fun setMessageRead(accountId: String, folderKey: String, uid: Long, isRead: Boolean) {
        messageDao.markRead(messageId(accountId, folderKey, uid), isRead)
        // Local state is authoritative for the UI immediately; the server push happens in the
        // background so a toggle-read tap or opening a message doesn't stall on a network round
        // trip. Best-effort: no retry queue exists yet, so a failure here (offline, server
        // rejects the flag change, ...) is swallowed rather than surfaced — the local read state
        // still reflects the user's intent even if the server never finds out.
        repositoryScope.launch {
            runCatching { pushSeenFlag(accountId, folderKey, uid, isRead) }
        }
    }

    /** Marks a message as replied-to, locally and (best-effort) on the server — called after a reply successfully sends. */
    suspend fun markAnswered(accountId: String, folderKey: String, uid: Long) {
        messageDao.markAnswered(messageId(accountId, folderKey, uid), true)
        repositoryScope.launch {
            runCatching {
                val (imapConfig, folder) = resolveImapContext(accountId, folderKey) ?: return@runCatching
                imapClient.setAnsweredFlag(imapConfig, folder.fullName, uid)
            }
        }
    }

    private suspend fun pushSeenFlag(accountId: String, folderKey: String, uid: Long, isRead: Boolean) {
        val (imapConfig, folder) = resolveImapContext(accountId, folderKey) ?: return
        imapClient.setSeenFlag(imapConfig, folder.fullName, uid, isRead)
    }

    /** Marks every currently-unread message in this folder read — e.g. the notification's "Mark all read" action. */
    suspend fun markAllReadInFolder(accountId: String, folderKey: String) {
        for (message in messageDao.getUnread(accountId, folderKey)) {
            setMessageRead(accountId, folderKey, message.uid, true)
        }
    }

    suspend fun setMessageFlagged(accountId: String, folderKey: String, uid: Long, isFlagged: Boolean) {
        messageDao.markFlagged(messageId(accountId, folderKey, uid), isFlagged)
        // Same best-effort background push as setMessageRead — see its comment.
        repositoryScope.launch {
            runCatching { pushFlaggedFlag(accountId, folderKey, uid, isFlagged) }
        }
    }

    private suspend fun pushFlaggedFlag(accountId: String, folderKey: String, uid: Long, isFlagged: Boolean) {
        val (imapConfig, folder) = resolveImapContext(accountId, folderKey) ?: return
        imapClient.setFlaggedFlag(imapConfig, folder.fullName, uid, isFlagged)
    }

    private suspend fun resolveImapContext(accountId: String, folderKey: String): Pair<ImapConfig, FolderEntity>? {
        val account = accountRepository.getAccount(accountId) ?: return null
        val password = accountRepository.getPassword(accountId) ?: return null
        val folder = folderDao.getForAccount(accountId).firstOrNull { localFolderKey(it.fullName) == folderKey } ?: return null
        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )
        return imapConfig to folder
    }

    /** Removes a message from the local cache only — does not touch the server. */
    suspend fun removeMessageLocally(accountId: String, folderKey: String, uid: Long) {
        messageDao.deleteById(messageId(accountId, folderKey, uid))
    }

    /** Folders cached for this account (from the most recent sync) — for folder browsing/move-to pickers. */
    suspend fun getFolders(accountId: String): List<FolderEntity> = folderDao.getForAccount(accountId)

    suspend fun getAttachments(accountId: String, folderKey: String, uid: Long): List<AttachmentEntity> =
        attachmentDao.getForMessage(messageId(accountId, folderKey, uid))

    /**
     * Resets every cached folder's sync watermark for this account so the next sync of each one
     * re-fetches everything within the sync window, instead of only messages newer than what's
     * already cached. Normal sync is a pure UID-delta ([syncFolder] only ever asks for
     * UID > lastSyncedUid), so a message already cached before some new piece of per-message data
     * was added to the schema (e.g. attachment metadata) would otherwise never pick it up.
     */
    suspend fun resetSyncProgress(accountId: String) {
        folderDao.resetLastSyncedUid(accountId)
    }

    /** Deletes the account plus every row cached locally for it — a real account removal, not just credentials. */
    suspend fun deleteAccountAndCache(account: AccountEntity) {
        accountRepository.deleteAccount(account)
        messageDao.deleteForAccount(account.id)
        folderDao.deleteForAccount(account.id)
        attachmentDao.deleteForAccount(account.id)
    }

    suspend fun downloadAttachment(accountId: String, folderKey: String, uid: Long, attachmentIndex: Int): Result<DownloadedAttachment> {
        val (imapConfig, folder) = resolveImapContext(accountId, folderKey)
            ?: return Result.failure(IllegalStateException("Account or folder not found"))
        return imapClient.fetchAttachment(imapConfig, folder.fullName, uid, attachmentIndex)
    }

    suspend fun syncAccount(accountId: String): SyncOutcome = syncFolder(accountId, INBOX_FOLDER_KEY)

    /**
     * Syncs one folder by its local key (see [localFolderKey]) — always refreshes the account's
     * cached folder list first, since that's cheap and keeps folder browsing/move-to pickers
     * current.
     */
    suspend fun syncFolder(accountId: String, folderKey: String): SyncOutcome {
        val account = accountRepository.getAccount(accountId)
            ?: return SyncOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId)
            ?: return SyncOutcome.Failure("Missing saved credentials")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        val folders = imapClient.fetchFolders(imapConfig).getOrElse {
            return SyncOutcome.Failure(describeMailError(it))
        }
        folderDao.insertAllIfAbsent(
            folders.map { folder ->
                FolderEntity(
                    id = folderId(accountId, localFolderKey(folder.fullName)),
                    accountId = accountId,
                    fullName = folder.fullName,
                    displayName = folder.displayName,
                    parentFullName = parentFullName(folder.fullName, folder.separator),
                )
            },
        )

        val targetFolder = folders.firstOrNull { localFolderKey(it.fullName) == folderKey }
            ?: return SyncOutcome.Failure("Folder not found on this account")

        val targetFolderId = folderId(accountId, folderKey)

        // Serialized per folder: SyncWorker's periodic backstop and MailIdleService's IDLE push
        // can both call this for the same account/folder at nearly the same time. Without the
        // lock both could read the same stale lastSyncedUid before either writes it back,
        // double-fetch the same "new" messages, and double-notify for mail the user already saw.
        return mutexFor(targetFolderId).withLock {
            val existingLastUid = folderDao.getById(targetFolderId)?.lastSyncedUid ?: 0L
            // Only actually consulted for a folder's very first sync (sinceUid <= 0) — every
            // later sync is a UID delta regardless of this setting.
            val syncWindowDays = settingsProvider.currentSettings().syncWindowDays

            val fetchResult = imapClient.fetchFolderMessages(
                config = imapConfig,
                folderFullName = targetFolder.fullName,
                sinceUid = existingLastUid,
                syncWindowDays = syncWindowDays,
            ).getOrElse {
                return@withLock SyncOutcome.Failure(describeMailError(it))
            }

            if (fetchResult.messages.isNotEmpty()) {
                persistMessages(accountId, folderKey, fetchResult.messages)
            }
            folderDao.updateLastSyncedUid(targetFolderId, fetchResult.newLastSyncedUid)

            // Prunes local rows for messages deleted/moved on the server via another client.
            // presentUids is null for fakes/tests that don't model it — skip reconciliation then
            // rather than treating "no data" as "folder is empty".
            fetchResult.presentUids?.let { presentUids ->
                messageDao.deleteMissing(accountId, folderKey, presentUids)
            }

            SyncOutcome.Success(fetchResult.messages.size)
        }
    }

    /** Shared by [syncFolder] and [loadOlderMessages] — upserts messages plus their attachment metadata. */
    private suspend fun persistMessages(accountId: String, folderKey: String, messages: List<ImapMessageInfo>) {
        messageDao.upsertAll(messages.map { it.toEntity(accountId, folderKey) })
        for (info in messages) {
            val id = messageId(accountId, folderKey, info.uid)
            attachmentDao.deleteForMessage(id)
            if (info.attachments.isNotEmpty()) {
                attachmentDao.upsertAll(
                    info.attachments.mapIndexed { index, attachment ->
                        AttachmentEntity(
                            id = "$id|$index",
                            messageId = id,
                            indexInMessage = index,
                            fileName = attachment.fileName,
                            mimeType = attachment.mimeType,
                            sizeBytes = attachment.sizeBytes,
                        )
                    },
                )
            }
        }
    }

    /**
     * "Load older mail" — pages backward past whatever [syncFolder] already cached, using the
     * lowest currently-cached UID as the boundary. Purely additive: never touches
     * `lastSyncedUid`, so it can't interfere with the normal forward delta sync.
     */
    suspend fun loadOlderMessages(accountId: String, folderKey: String): SyncOutcome {
        val account = accountRepository.getAccount(accountId) ?: return SyncOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId) ?: return SyncOutcome.Failure("Missing saved credentials")
        val folder = folderDao.getForAccount(accountId).firstOrNull { localFolderKey(it.fullName) == folderKey }
            ?: return SyncOutcome.Failure("Folder not found")
        val lowestCachedUid = messageDao.getMinUid(accountId, folderKey)
            ?: return SyncOutcome.Failure("Sync this folder at least once before loading older mail")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        return mutexFor(folder.id).withLock {
            val older = imapClient.fetchOlderMessages(imapConfig, folder.fullName, beforeUid = lowestCachedUid, limit = LOAD_OLDER_PAGE_SIZE)
                .getOrElse { return@withLock SyncOutcome.Failure(describeMailError(it)) }

            if (older.isNotEmpty()) {
                persistMessages(accountId, folderKey, older)
            }
            SyncOutcome.Success(older.size)
        }
    }

    /**
     * Moves a message to another folder on the server. The moved message gets a new UID in the
     * destination folder (server-assigned) — rather than guess it, this just drops the local row
     * for the source folder; the destination folder's own next sync picks the message up fresh.
     */
    suspend fun moveMessage(accountId: String, folderKey: String, uid: Long, targetFolderKey: String): MoveOutcome {
        val account = accountRepository.getAccount(accountId)
            ?: return MoveOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId)
            ?: return MoveOutcome.Failure("Missing saved credentials")

        val folders = folderDao.getForAccount(accountId)
        val sourceFolder = folders.firstOrNull { localFolderKey(it.fullName) == folderKey }
            ?: return MoveOutcome.Failure("Source folder not found")
        val targetFolder = folders.firstOrNull { localFolderKey(it.fullName) == targetFolderKey }
            ?: return MoveOutcome.Failure("Target folder not found")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        val result = imapClient.moveMessage(imapConfig, sourceFolder.fullName, uid, targetFolder.fullName)
        return if (result.isSuccess) {
            removeMessageLocally(accountId, folderKey, uid)
            MoveOutcome.Success(targetFolderKey)
        } else {
            MoveOutcome.Failure(describeMailError(result.exceptionOrNull() ?: Throwable("Could not move message")))
        }
    }

    /**
     * Reverses a move: looks up the message's new server-assigned UID in [currentFolderKey] (the
     * move's destination) directly by its Message-ID header — [moveMessage] never tracks that
     * UID itself, since the destination folder's own next sync was always expected to pick the
     * message up fresh. Deliberately a targeted [ImapClient.findMessageByMessageId] rather than
     * a normal [syncFolder]: a plain re-sync bounds its first-sync fetch by the configured sync
     * window/message-count cap using the message's *original* received date (preserved by IMAP
     * COPY), which has nothing to do with how recently it landed in this folder — a message
     * older than that window would never be found even though it's the newest arrival here.
     */
    suspend fun undoMove(accountId: String, originalFolderKey: String, currentFolderKey: String, messageIdHeader: String?): UndoMoveOutcome {
        if (messageIdHeader.isNullOrBlank()) {
            return UndoMoveOutcome.Failure("Can't undo — this message has no Message-ID to find it by")
        }
        val account = accountRepository.getAccount(accountId) ?: return UndoMoveOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId) ?: return UndoMoveOutcome.Failure("Missing saved credentials")
        val folder = folderDao.getForAccount(accountId).firstOrNull { localFolderKey(it.fullName) == currentFolderKey }
            ?: return UndoMoveOutcome.Failure("Folder not found")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        val found = imapClient.findMessageByMessageId(imapConfig, folder.fullName, messageIdHeader)
            .getOrElse { return UndoMoveOutcome.Failure(describeMailError(it)) }
            ?: return UndoMoveOutcome.Failure("Couldn't find the message to undo — it may have been moved again")

        persistMessages(accountId, currentFolderKey, listOf(found))
        return when (val result = moveMessage(accountId, currentFolderKey, found.uid, originalFolderKey)) {
            is MoveOutcome.Success -> UndoMoveOutcome.Success
            is MoveOutcome.Failure -> UndoMoveOutcome.Failure(result.reason)
        }
    }

    /**
     * Moves a message to this account's Trash folder — a real server-side move, not just a local
     * cache removal. Trash is identified by the account's configured `trashFolderFullName` when
     * set (see `EditAccountScreen`), otherwise falls back to name-guessing (no IMAP SPECIAL-USE
     * extension support, which not every server advertises); fails clearly rather than silently
     * no-opping if none is found.
     */
    suspend fun moveMessageToTrash(accountId: String, folderKey: String, uid: Long): MoveOutcome {
        val trashFolderKey = findTrashFolderKey(accountId)
            ?: return MoveOutcome.Failure("No Trash folder found on this account")
        return moveMessage(accountId, folderKey, uid, trashFolderKey)
    }

    /** Same idea as [moveMessageToTrash], for the account's configured/guessed Archive folder. */
    suspend fun moveMessageToArchive(accountId: String, folderKey: String, uid: Long): MoveOutcome {
        val archiveFolderKey = findArchiveFolderKey(accountId)
            ?: return MoveOutcome.Failure("No Archive folder found on this account")
        return moveMessage(accountId, folderKey, uid, archiveFolderKey)
    }

    /** Public (not private) so callers like "is this folder Trash?" UI checks and [emptyTrash] can reuse the same resolution. */
    suspend fun findTrashFolderKey(accountId: String): String? {
        val configured = accountRepository.getAccount(accountId)?.trashFolderFullName
        if (configured != null) return localFolderKey(configured)
        return folderDao.getForAccount(accountId)
            .firstOrNull { it.fullName.contains("trash", ignoreCase = true) || it.fullName.contains("deleted", ignoreCase = true) }
            ?.let { localFolderKey(it.fullName) }
    }

    private suspend fun findArchiveFolderKey(accountId: String): String? {
        val configured = accountRepository.getAccount(accountId)?.archiveFolderFullName
        if (configured != null) return localFolderKey(configured)
        return folderDao.getForAccount(accountId)
            .firstOrNull { it.fullName.contains("archive", ignoreCase = true) }
            ?.let { localFolderKey(it.fullName) }
    }

    /** Same idea as [findTrashFolderKey]/[findArchiveFolderKey], for the account's configured/guessed Spam or Junk folder. */
    suspend fun findSpamFolderKey(accountId: String): String? {
        val configured = accountRepository.getAccount(accountId)?.spamFolderFullName
        if (configured != null) return localFolderKey(configured)
        return folderDao.getForAccount(accountId)
            .firstOrNull { it.fullName.contains("spam", ignoreCase = true) || it.fullName.contains("junk", ignoreCase = true) }
            ?.let { localFolderKey(it.fullName) }
    }

    /**
     * Permanently deletes every message in this account's Trash folder — a real server-side
     * expunge, not just a local cache clear. Prunes the local cache for that folder too via the
     * same "nothing is present" trick [syncFolder] uses for normal deletion reconciliation.
     */
    suspend fun emptyTrash(accountId: String): SyncOutcome {
        val trashFolderKey = findTrashFolderKey(accountId) ?: return SyncOutcome.Failure("No Trash folder found on this account")
        val account = accountRepository.getAccount(accountId) ?: return SyncOutcome.Failure("Account not found")
        val password = accountRepository.getPassword(accountId) ?: return SyncOutcome.Failure("Missing saved credentials")
        val folder = folderDao.getForAccount(accountId).firstOrNull { localFolderKey(it.fullName) == trashFolderKey }
            ?: return SyncOutcome.Failure("Folder not found")

        val imapConfig = ImapConfig(
            host = account.imapHost,
            port = account.imapPort,
            security = account.imapSecurity,
            username = account.username,
            password = password,
        )

        imapClient.emptyFolder(imapConfig, folder.fullName).getOrElse { return SyncOutcome.Failure(describeMailError(it)) }
        messageDao.deleteMissing(accountId, trashFolderKey, presentUids = emptyList())
        return SyncOutcome.Success(0)
    }

    /**
     * Writes a draft to the outbox as PENDING; the caller is responsible for triggering a send.
     * [id] can be supplied by the caller (rather than generated here) so attachments copied to
     * disk ahead of send time — see [attachFilesToOutbox] — can be tagged with the same id their
     * outbox row will end up using.
     */
    suspend fun queueMessageForSending(accountId: String, draft: ComposeDraft, id: String = UUID.randomUUID().toString()): String {
        outboxDao.insert(
            OutboxEntity(
                id = id,
                accountId = accountId,
                toAddresses = draft.to.joinToString(";"),
                ccAddresses = draft.cc.takeIf { it.isNotEmpty() }?.joinToString(";"),
                bccAddresses = draft.bcc.takeIf { it.isNotEmpty() }?.joinToString(";"),
                subject = draft.subject,
                body = draft.body,
                bodyHtml = draft.bodyHtml,
                inReplyToMessageIdHeader = draft.inReplyToMessageIdHeader,
                referencesHeader = draft.referencesHeader,
                status = OutboxStatus.PENDING,
                errorMessage = null,
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** All saved drafts across every account, newest-edited first — for the Drafts list screen. */
    fun observeDrafts(): Flow<List<DraftEntity>> = draftDao.observeAll()

    suspend fun getDraft(draftId: String): DraftEntity? = draftDao.getById(draftId)

    /** Upserts a draft under [draftId] — called on a debounce from ComposeViewModel as the user types, not just explicitly. */
    suspend fun saveDraft(draftId: String, content: DraftContent) {
        draftDao.upsert(
            DraftEntity(
                id = draftId,
                accountId = content.accountId,
                to = content.to,
                cc = content.cc,
                bcc = content.bcc,
                subject = content.subject,
                bodyHtml = content.bodyHtml,
                quotedHtml = content.quotedHtml,
                inReplyToMessageIdHeader = content.inReplyToMessageIdHeader,
                referencesHeader = content.referencesHeader,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteDraft(draftId: String) = draftDao.delete(draftId)

    /** Records files already copied into private storage (see AttachmentStorage.copyForOutbox) against a queued outbox item. */
    suspend fun attachFilesToOutbox(outboxId: String, files: List<PickedAttachment>) {
        if (files.isEmpty()) return
        outboxAttachmentDao.insertAll(
            files.map { file ->
                OutboxAttachmentEntity(
                    id = UUID.randomUUID().toString(),
                    outboxId = outboxId,
                    fileName = file.fileName,
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    localFilePath = file.localFilePath,
                )
            },
        )
    }

    /**
     * Attempts to send every PENDING/FAILED outbox item. Called both right after composing (via
     * a network-constrained one-time work request) and on every periodic sync, so a message
     * queued while offline still goes out once connectivity returns even if the app was closed.
     */
    suspend fun flushOutbox(): Boolean {
        var allSucceeded = true
        for (item in outboxDao.getUnsent()) {
            if (sendOutboxItem(item) is SendOutcome.Failure) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }

    private suspend fun sendOutboxItem(item: OutboxEntity): SendOutcome {
        val account = accountRepository.getAccount(item.accountId)
        if (account == null) {
            outboxDao.updateStatus(item.id, OutboxStatus.FAILED, "Account not found")
            return SendOutcome.Failure("Account not found")
        }
        val password = accountRepository.getPassword(item.accountId)
        if (password == null) {
            outboxDao.updateStatus(item.id, OutboxStatus.FAILED, "Missing saved credentials")
            return SendOutcome.Failure("Missing saved credentials")
        }

        val smtpConfig = SmtpConfig(
            host = account.smtpHost,
            port = account.smtpPort,
            security = account.smtpSecurity,
            username = account.username,
            password = password,
        )
        val outboxAttachments = outboxAttachmentDao.getForOutbox(item.id)
        val outgoing = OutgoingMessage(
            fromAddress = account.emailAddress,
            fromPersonal = account.displayName,
            to = item.toAddresses.splitAddresses(),
            cc = item.ccAddresses?.splitAddresses() ?: emptyList(),
            bcc = item.bccAddresses?.splitAddresses() ?: emptyList(),
            subject = item.subject,
            body = item.body,
            bodyHtml = item.bodyHtml,
            inReplyTo = item.inReplyToMessageIdHeader,
            references = item.referencesHeader,
            attachments = outboxAttachments.map { attachment ->
                OutgoingAttachment(
                    fileName = attachment.fileName,
                    mimeType = attachment.mimeType,
                    bytes = File(attachment.localFilePath).readBytes(),
                )
            },
        )

        val result = smtpSender.send(smtpConfig, outgoing)
        return if (result.isSuccess) {
            outboxDao.updateStatus(item.id, OutboxStatus.SENT, null)
            if (outboxAttachments.isNotEmpty()) {
                outboxAttachmentDao.deleteForOutbox(item.id)
                for (attachment in outboxAttachments) {
                    runCatching { File(attachment.localFilePath).delete() }
                }
                runCatching { File(outboxAttachments.first().localFilePath).parentFile?.delete() }
            }
            SendOutcome.Success
        } else {
            val reason = describeMailError(result.exceptionOrNull() ?: Throwable("Could not send message"))
            outboxDao.updateStatus(item.id, OutboxStatus.FAILED, reason)
            SendOutcome.Failure(reason)
        }
    }

    private fun String.splitAddresses(): List<String> = split(";").map { it.trim() }.filter { it.isNotBlank() }

    private fun ImapMessageInfo.toEntity(accountId: String, folderKey: String) = MessageEntity(
        id = messageId(accountId, folderKey, uid),
        accountId = accountId,
        folderName = folderKey,
        uid = uid,
        messageIdHeader = messageIdHeader,
        conversationId = ConversationThreading.computeConversationId(
            messageIdHeader = messageIdHeader,
            inReplyToHeader = inReplyToHeader,
            referencesHeader = referencesHeader,
            subject = subject,
        ),
        subject = subject,
        fromAddress = fromAddress,
        fromPersonal = fromPersonal,
        toAddresses = toAddresses,
        ccAddresses = ccAddresses,
        sentDateEpochMillis = sentDateEpochMillis,
        receivedDateEpochMillis = receivedDateEpochMillis,
        isRead = isRead,
        isFlagged = isFlagged,
        hasAttachments = hasAttachments,
        bodyPreview = bodyPreview,
        bodyText = bodyText,
        bodyHtml = bodyHtml,
        isAnswered = isAnswered,
    )

    private fun folderId(accountId: String, folderKey: String) = "$accountId|$folderKey"

    private fun messageId(accountId: String, folderKey: String, uid: Long) = "$accountId|$folderKey|$uid"

    private fun localFolderKey(fullName: String): String = Companion.localFolderKey(fullName)

    /** Splits on the last hierarchy delimiter — no delimiter found means a top-level folder. */
    private fun parentFullName(fullName: String, separator: Char): String? {
        val index = fullName.lastIndexOf(separator)
        return if (index <= 0) null else fullName.substring(0, index)
    }

    companion object {
        const val INBOX_FOLDER_KEY = "INBOX"

        /** Page size for [loadOlderMessages] — same order of magnitude as ImapClientImpl's initial-sync cap. */
        const val LOAD_OLDER_PAGE_SIZE = 50

        // The server's "INBOX" folder isn't guaranteed to come back with that exact casing;
        // folder rows for the inbox are always keyed by this normalized constant so lookups stay
        // consistent. Every other folder is keyed by its own server-reported fullName. Public so
        // the UI layer (e.g. the folder list screen building a nav key) doesn't have to duplicate
        // this matching rule.
        fun localFolderKey(fullName: String): String =
            if (fullName.equals("INBOX", ignoreCase = true)) INBOX_FOLDER_KEY else fullName
    }
}
