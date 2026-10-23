/*
 * Copyright 2026 Sylphinford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.sylphinford.imageconv

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * 负责把转换结果写到磁盘。
 *
 * 两种落点：
 * 1. 默认写入系统相册的 Pictures/SylImageConv（Android 10+ 走 MediaStore，相册立即可见）
 * 2. 用户在界面上自选的目录（SAF 树 URI），写失败时自动回退到方案 1
 */
class OutputStore(private val context: Context) {

    var treeUri: Uri? = null

    /** 上一次写 SAF 目录失败的原因，供界面提示 */
    var lastFallbackReason: String? = null
        private set

    fun label(): String {
        val tree = treeUri ?: return context.getString(R.string.dir_default)
        val name = runCatching { DocumentFile.fromTreeUri(context, tree)?.name }.getOrNull()
        return name ?: context.getString(R.string.dir_default)
    }

    fun open(displayName: String, mime: String): Sink {
        val tree = treeUri
        if (tree != null) {
            try {
                return openInTree(tree, displayName, mime)
            } catch (e: Exception) {
                lastFallbackReason = e.message
                treeUri = null
            }
        }
        return openInGallery(displayName, mime)
    }

    // ---------------------------------------------------------------- 相册

    private fun openInGallery(name: String, mime: String): Sink =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openInMediaStore(name, mime)
        } else {
            openInLegacyDir(name, mime)
        }

    private fun openInMediaStore(name: String, mime: String): Sink {
        val resolver = context.contentResolver
        val unique = uniqueMediaStoreName(name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, unique)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException(context.getString(R.string.err_write))
        val stream = resolver.openOutputStream(uri)
            ?: run {
                resolver.delete(uri, null, null)
                throw IOException(context.getString(R.string.err_write))
            }
        return Sink(stream, unique, { querySize(uri) }) { success ->
            if (success) {
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            } else {
                resolver.delete(uri, null, null)
            }
        }
    }

    /** 查询 MediaStore 里刚写完的文件大小 */
    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }.getOrDefault(-1L)

    private fun openInLegacyDir(name: String, mime: String): Sink {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER_NAME)
        if (!dir.exists() && !dir.mkdirs()) throw IOException(context.getString(R.string.err_write))
        val file = uniqueFile(dir, name)
        val stream = FileOutputStream(file)
        return Sink(stream, file.name, { file.length() }) { success ->
            if (success) {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            } else {
                file.delete()
            }
        }
    }

    private fun uniqueMediaStoreName(name: String): String {
        var candidate = name
        var index = 1
        while (mediaNameExists(candidate)) {
            candidate = withIndex(name, index++)
        }
        return candidate
    }

    private fun mediaNameExists(name: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(name, "$RELATIVE_PATH/")
        return runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, args, null
            )?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var index = 1
        while (file.exists()) {
            file = File(dir, withIndex(name, index++))
        }
        return file
    }

    // ---------------------------------------------------------------- SAF

    private fun openInTree(tree: Uri, name: String, mime: String): Sink {
        val dir = DocumentFile.fromTreeUri(context, tree)
            ?: throw IOException(context.getString(R.string.err_write))
        if (!dir.canWrite()) throw IOException(context.getString(R.string.err_write))
        val document = createUniqueDocument(dir, name, mime)
            ?: throw IOException(context.getString(R.string.err_write))
        val stream = context.contentResolver.openOutputStream(document.uri)
            ?: run {
                document.delete()
                throw IOException(context.getString(R.string.err_write))
            }
        return Sink(stream, document.name ?: name, { document.length() }) { success ->
            if (!success) document.delete()
        }
    }

    private fun createUniqueDocument(dir: DocumentFile, name: String, mime: String): DocumentFile? {
        var candidate = name
        var index = 1
        while (dir.findFile(candidate) != null) {
            if (index > 500) return null
            candidate = withIndex(name, index++)
        }
        return dir.createFile(mime, candidate)
    }

    // ---------------------------------------------------------------- 公共

    private fun withIndex(name: String, index: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) "$name ($index)" else "${name.substring(0, dot)} ($index)${name.substring(dot)}"
    }

    class Sink internal constructor(
        val stream: OutputStream,
        val displayName: String,
        private val sizeProvider: () -> Long,
        private val onFinish: (Boolean) -> Unit
    ) {
        /** 必须在关闭流之后调用，用于提交或清理 */
        fun finish(success: Boolean) {
            runCatching { onFinish(success) }
        }

        /** 输出文件的实际大小，取不到时返回 -1 */
        fun sizeBytes(): Long = runCatching { sizeProvider() }.getOrDefault(-1L)
    }

    private companion object {
        const val FOLDER_NAME = "SylImageConv"
        const val RELATIVE_PATH = "Pictures/$FOLDER_NAME"
    }
}
