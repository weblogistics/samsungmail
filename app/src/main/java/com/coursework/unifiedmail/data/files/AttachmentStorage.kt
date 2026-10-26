package com.coursework.unifiedmail.data.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.coursework.unifiedmail.data.remote.DownloadedAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A file picked for an outgoing message, copied into this app's private storage — see [AttachmentStorage.copyForOutbox]. */
data class PickedAttachment(
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val localFilePath: String,
)

/**
 * Just the cache-eviction surface of [AttachmentStorage] — narrower so [MailRepository], which
 * needs to evict a deleted message's cached attachments but otherwise has no reason to touch
 * Android file/MediaStore APIs, can be unit-tested against a fake instead of a real [Context].
 */
interface AttachmentCache {
    /** Deletes the cached file for one attachment, if any — call once a message (or just this
     * attachment) is gone for good, so a stale copy doesn't linger on disk forever. */
    fun deleteCached(attachmentId: String)

    /** Deletes every cached attachment file belonging to any message of this account — for
     * whole-account removal, where deleting one attachment at a time would mean first reading
     * back every attachment row that's about to be deleted anyway. */
    fun deleteCachedForAccount(accountId: String)
}

/**
 * Saves downloaded attachment bytes to app-scoped external storage — no runtime permission
 * needed on any supported API level, unlike shared/MediaStore locations — and hands back a
 * `content://` URI (via FileProvider) suitable for an `ACTION_VIEW` intent. Also copies files
 * picked for outgoing messages into private internal storage (see [copyForOutbox]).
 */
@Singleton
class AttachmentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : AttachmentCache {
    /**
     * Saves [attachment]'s bytes under a location keyed by [attachmentId] (see
     * [AttachmentEntity.id][com.coursework.unifiedmail.data.local.AttachmentEntity.id] — stable
     * across repeat opens of the same attachment, not reused for a different one) rather than
     * [uniqueFile]'s collision-renaming — so opening the same attachment twice reuses this same
     * file (see [cached]) instead of re-downloading it and piling up "file (1)", "file (2)", ...
     * copies that were never actually different files.
     */
    fun save(attachment: DownloadedAttachment, attachmentId: String): Uri {
        val file = cacheFile(attachmentId, attachment.fileName)
        file.writeBytes(attachment.bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * A previous [save] of this exact attachment, if its bytes are still on disk — lets the
     * caller skip re-downloading from the server just to open something already opened once.
     * [declaredMimeType] only matters if the file's own content doesn't self-identify (see
     * [viewerMimeType]), same as on a fresh download.
     */
    fun cached(attachmentId: String, fileName: String, declaredMimeType: String?): Pair<Uri, String>? {
        val file = cacheFile(attachmentId, fileName)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to viewerMimeType(DownloadedAttachment(fileName, declaredMimeType, file.readBytes()))
    }

    /** Raw bytes of a previous [save] of this attachment, if still on disk — e.g. for the
     * explicit "Download" action, which needs actual bytes to copy rather than just a Uri. */
    fun cachedBytes(attachmentId: String, fileName: String): ByteArray? {
        val file = cacheFile(attachmentId, fileName)
        return if (file.exists()) file.readBytes() else null
    }

    override fun deleteCached(attachmentId: String) {
        cacheDir(attachmentId).deleteRecursively()
    }

    override fun deleteCachedForAccount(accountId: String) {
        // Cached files are keyed by attachment id ("accountId|folderKey|uid|index", sanitized —
        // see cacheDir), so every one of this account's cache directories shares this prefix.
        // Matching on that is cheaper than reading back every attachment row just to delete it.
        val prefix = "${sanitize(accountId)}_"
        cacheRoot().listFiles()?.forEach { dir -> if (dir.name.startsWith(prefix)) dir.deleteRecursively() }
    }

    private fun cacheRoot(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "attachment_cache")

    /** One directory per attachment id — [attachmentId] can contain path separators (e.g. a
     * nested IMAP folder name), so it's sanitized into a single safe path segment rather than
     * used raw. */
    private fun cacheDir(attachmentId: String): File = File(cacheRoot(), sanitize(attachmentId))

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun cacheFile(attachmentId: String, fileName: String): File {
        val dir = cacheDir(attachmentId).apply { mkdirs() }
        return File(dir, fileName)
    }

    /**
     * The MIME type to hand a viewer for [attachment]. Checked in order of how trustworthy each
     * signal actually is:
     *  1. The file's own magic-number header, when it's a type we can recognize that way (a real
     *     PDF always starts with `%PDF-`, say) — this is the one signal a sender can't get wrong,
     *     since it's not metadata they wrote, it's the content itself.
     *  2. The type [MimeTypeMap] derives from the file extension — senders routinely declare
     *     something generic (`application/octet-stream`) or just nonstandard (`application/x-pdf`
     *     instead of `application/pdf`) that no specific viewer's intent filter claims, so Android
     *     falls back to its broad "every app that handles arbitrary files" chooser instead of the
     *     one app that actually opens that file type.
     *  3. The sender-declared type, only once neither of the above resolved anything (e.g. no
     *     recognizable extension on the filename either).
     */
    fun viewerMimeType(attachment: DownloadedAttachment): String {
        sniffMimeType(attachment.bytes)?.let { return it }
        val extension = attachment.fileName.substringAfterLast('.', "").lowercase()
        val fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (fromExtension != null) return fromExtension
        return attachment.mimeType?.lowercase()?.takeIf { it.isNotBlank() && it !in GENERIC_MIME_TYPES }
            ?: "application/octet-stream"
    }

    /** Just enough magic-number sniffing to catch the type that most commonly trips up [viewerMimeType] — a PDF with unreliable sender metadata. */
    private fun sniffMimeType(bytes: ByteArray): String? =
        if (bytes.size >= 5 && bytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") "application/pdf" else null

    /**
     * Saves to the device's real, shared Downloads collection — reachable from the Files app or
     * any other app, unlike [save]'s app-scoped location — for the explicit long-press "Download"
     * action on an attachment (as opposed to "Open", which only needs a content:// URI to hand a
     * viewer and uses [save]). MediaStore.Downloads needs no runtime permission from API 29
     * onward; below that this just falls back to [save] rather than requesting
     * WRITE_EXTERNAL_STORAGE for a shrinking minority of supported devices.
     */
    fun saveToDownloads(attachment: DownloadedAttachment, attachmentId: String): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return save(attachment, attachmentId)

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, attachment.fileName)
            put(MediaStore.Downloads.MIME_TYPE, viewerMimeType(attachment))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create a Downloads entry for ${attachment.fileName}")
        resolver.openOutputStream(uri)?.use { it.write(attachment.bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /**
     * Copies bytes from a picker-supplied `content://` [uri] into this app's private storage
     * (not external — outgoing attachments are never opened by another app) so they survive
     * until [MailRepository.flushOutbox] actually sends them, even across an app restart. Returns
     * null if the resolver can't open the Uri (e.g. the source app revoked the grant).
     */
    suspend fun copyForOutbox(uri: Uri, outboxId: String): PickedAttachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val fileName = queryDisplayName(uri) ?: "attachment"
        val mimeType = resolver.getType(uri)
        val dir = File(context.filesDir, "outbox_attachments/$outboxId").apply { mkdirs() }
        val file = uniqueFile(dir, fileName)
        val copied = resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        if (copied == null) null else PickedAttachment(fileName = fileName, mimeType = mimeType, sizeBytes = file.length(), localFilePath = file.absolutePath)
    }

    /** Removes a completed/abandoned outbox item's private attachment copies. */
    fun deleteOutboxAttachments(outboxId: String) {
        File(context.filesDir, "outbox_attachments/$outboxId").deleteRecursively()
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    /** Appends " (1)", " (2)", ... before the extension on a name collision, rather than overwriting. */
    private fun uniqueFile(dir: File, fileName: String): File {
        val dotIndex = fileName.lastIndexOf('.')
        val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var candidate = File(dir, fileName)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($counter)$extension")
            counter++
        }
        return candidate
    }

    private companion object {
        val GENERIC_MIME_TYPES = setOf("application/octet-stream", "application/x-download", "application/download", "application/unknown", "binary/octet-stream")
    }
}
