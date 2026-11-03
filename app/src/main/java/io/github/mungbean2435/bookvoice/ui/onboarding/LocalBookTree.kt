package io.github.mungbean2435.bookvoice.ui.onboarding

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/**
 * 从 SAF 文件夹 URI 里找出 TXT / EPUB。
 *
 * 不引 DocumentFile 依赖，直接用 DocumentsContract 递归；限制目录和文件数量，
 * 避免用户误选根目录时无限展开。单个子树读取失败就跳过，不阻塞其它文件。
 */
internal fun localBookUrisInTree(
    context: Context,
    treeUri: Uri,
    maxFiles: Int = 200,
    maxDirs: Int = 500
): List<Uri> {
    val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return emptyList()
    val resolver = context.contentResolver
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    val queue = ArrayDeque<String>().apply { add(rootId) }
    val out = ArrayList<Uri>()
    var visitedDirs = 0

    while (queue.isNotEmpty() && out.size < maxFiles && visitedDirs < maxDirs) {
        val parentId = queue.removeFirst()
        visitedDirs++
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext() && out.size < maxFiles) {
                    val id = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol).orEmpty()
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> queue.add(id)
                        name.endsWith(".txt", ignoreCase = true) ||
                            name.endsWith(".epub", ignoreCase = true) ->
                            out.add(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                    }
                }
            }
        }
    }
    return out
}
