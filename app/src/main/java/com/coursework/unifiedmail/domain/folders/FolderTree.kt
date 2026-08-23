package com.coursework.unifiedmail.domain.folders

import com.coursework.unifiedmail.data.local.FolderEntity

data class FolderTreeNode(
    val folder: FolderEntity,
    val children: List<FolderTreeNode>,
    val depth: Int,
)

/**
 * Builds a folder hierarchy from a flat list, purely from `FolderEntity.parentFullName` (derived
 * at sync time from the server's own hierarchy delimiter — see MailRepository.parentFullName) —
 * no Room/Android dependency, same as ConversationThreading, so this is plain-JUnit-testable.
 */
object FolderTree {

    fun build(folders: List<FolderEntity>): List<FolderTreeNode> {
        // A folder can reference a parent that was itself filtered out of the synced list (e.g.
        // Gmail's "[Gmail]" container doesn't hold messages, so ImapClientImpl.fetchFolders never
        // returns it, but "[Gmail]/Sent Mail" still names it as a parent) — treat that case as
        // top-level rather than silently dropping the folder from the tree.
        val knownFullNames = folders.map { it.fullName }.toSet()
        val byParent = folders.groupBy { folder -> folder.parentFullName?.takeIf { it in knownFullNames } }

        fun buildLevel(parentFullName: String?, depth: Int): List<FolderTreeNode> =
            byParent[parentFullName].orEmpty()
                .sortedBy { it.displayName.lowercase() }
                .map { folder -> FolderTreeNode(folder, buildLevel(folder.fullName, depth + 1), depth) }

        return buildLevel(null, 0)
    }

    /** Depth-first flattening for a LazyColumn, skipping a node's subtree while it's collapsed. */
    fun flattenVisible(nodes: List<FolderTreeNode>, collapsedFullNames: Set<String>): List<FolderTreeNode> =
        nodes.flatMap { node ->
            val subtree = if (node.folder.fullName in collapsedFullNames) emptyList() else flattenVisible(node.children, collapsedFullNames)
            listOf(node) + subtree
        }

    /**
     * The account's actual Trash/Archive/Spam folders, for a "special folders" section — its own
     * explicit Settings choice always wins; a kind left at "auto-detect" (null) falls back to the
     * single best name match, using the same heuristic as MailRepository's own
     * findTrashFolderKey/findArchiveFolderKey/findSpamFolderKey, so this list agrees with what
     * Move-to-Trash/Archive actually do. Deliberately picks at most ONE folder per kind — matching
     * every folder that merely contains "sent"/"spam"/etc previously flooded this section with
     * lookalikes (two "Sent", two "Spam", an unrelated folder that happened to match).
     */
    fun resolveSpecialFolders(
        folders: List<FolderEntity>,
        trashFolderFullName: String?,
        archiveFolderFullName: String?,
        spamFolderFullName: String?,
    ): List<FolderEntity> {
        fun resolve(configured: String?, heuristic: (FolderEntity) -> Boolean): FolderEntity? =
            configured?.let { name -> folders.firstOrNull { it.fullName == name } }
                ?: folders.firstOrNull(heuristic)

        return listOfNotNull(
            resolve(trashFolderFullName) { it.fullName.contains("trash", ignoreCase = true) || it.fullName.contains("deleted", ignoreCase = true) },
            resolve(archiveFolderFullName) { it.fullName.contains("archive", ignoreCase = true) },
            resolve(spamFolderFullName) { it.fullName.contains("spam", ignoreCase = true) || it.fullName.contains("junk", ignoreCase = true) },
        ).distinctBy { it.id }
    }
}
