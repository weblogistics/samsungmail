package com.coursework.unifiedmail.data.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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
 * Saves downloaded attachment bytes to app-scoped external storage — no runtime permission
 * needed on any supported API level, unlike shared/MediaStore locations — and hands back a
 * `content://` URI (via FileProvider) suitable for an `ACTION_VIEW` intent. Also copies files
 * picked for outgoing messages into private internal storage (see [copyForOutbox]).
 */
@Singleton
class AttachmentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun save(attachment: DownloadedAttachment): Uri {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = uniqueFile(dir, attachment.fileName)
        file.writeBytes(attachment.bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
}
