package com.coursework.unifiedmail.domain.folders

import com.coursework.unifiedmail.data.local.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTreeTest {

    private fun folder(fullName: String, parentFullName: String? = null) = FolderEntity(
        id = fullName,
        accountId = "acct-1",
        fullName = fullName,
        displayName = fullName.substringAfterLast('/'),
        parentFullName = parentFullName,
    )

    @Test
    fun `top-level folders have no children by default`() {
        val tree = FolderTree.build(listOf(folder("INBOX"), folder("Sent")))

        assertEquals(2, tree.size)
        assertEquals(0, tree[0].depth)
        assertEquals(emptyList<Any>(), tree[0].children)
    }

    @Test
    fun `nested folders attach under their parent at increasing depth`() {
        val tree = FolderTree.build(
            listOf(
                folder("Work"),
                folder("Work/Projects", parentFullName = "Work"),
                folder("Work/Projects/2024", parentFullName = "Work/Projects"),
            ),
        )

        assertEquals(1, tree.size)
        val work = tree.single()
        assertEquals("Work", work.folder.fullName)
        assertEquals(0, work.depth)

        val projects = work.children.single()
        assertEquals("Work/Projects", projects.folder.fullName)
        assertEquals(1, projects.depth)

        val year = projects.children.single()
        assertEquals("Work/Projects/2024", year.folder.fullName)
        assertEquals(2, year.depth)
    }

    @Test
    fun `a folder referencing a parent that was filtered out of sync falls back to top-level`() {
        // e.g. Gmail's "[Gmail]" container doesn't hold messages so never gets synced itself.
        val tree = FolderTree.build(listOf(folder("[Gmail]/Sent Mail", parentFullName = "[Gmail]")))

        assertEquals(1, tree.size)
        assertEquals("[Gmail]/Sent Mail", tree.single().folder.fullName)
        assertEquals(0, tree.single().depth)
    }

    @Test
    fun `flattenVisible skips a collapsed node's subtree but keeps the node itself`() {
        val tree = FolderTree.build(
            listOf(folder("Work"), folder("Work/Projects", parentFullName = "Work")),
        )

        val expanded = FolderTree.flattenVisible(tree, collapsedFullNames = emptySet())
        assertEquals(listOf("Work", "Work/Projects"), expanded.map { it.folder.fullName })

        val collapsed = FolderTree.flattenVisible(tree, collapsedFullNames = setOf("Work"))
        assertEquals(listOf("Work"), collapsed.map { it.folder.fullName })
    }

    @Test
    fun `resolveSpecialFolders prefers the account's configured folder over the name heuristic`() {
        val folders = listOf(folder("Deleted Items"), folder("My Archive"), folder("Junk Email"))

        val resolved = FolderTree.resolveSpecialFolders(
            folders,
            trashFolderFullName = "Deleted Items",
            archiveFolderFullName = null,
            spamFolderFullName = null,
        )

        assertEquals(listOf("Deleted Items", "My Archive", "Junk Email"), resolved.map { it.fullName })
    }

    @Test
    fun `resolveSpecialFolders never returns more than one folder per kind`() {
        // Two folders both look like "Sent"/"Spam" — a plain "contains" heuristic over every
        // folder would previously surface both; this must pick a single winner per kind.
        val folders = listOf(folder("Spam"), folder("Junk"), folder("Archive"))

        val resolved = FolderTree.resolveSpecialFolders(
            folders,
            trashFolderFullName = null,
            archiveFolderFullName = null,
            spamFolderFullName = null,
        )

        assertEquals(listOf("Archive", "Spam"), resolved.map { it.fullName })
    }

    @Test
    fun `resolveSpecialFolders returns nothing for kinds with no configured folder and no name match`() {
        val folders = listOf(folder("Work"))

        val resolved = FolderTree.resolveSpecialFolders(folders, null, null, null)

        assertEquals(emptyList<String>(), resolved.map { it.fullName })
    }
}
