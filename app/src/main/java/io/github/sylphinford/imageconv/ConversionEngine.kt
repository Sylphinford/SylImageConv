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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException

/**
 * 单张图片的解码 -> 方向校正 -> 编码 -> 落盘。
 * 全部方法都是阻塞的，必须在 IO 线程调用。
 */
class ConversionEngine(private val context: Context) {

    data class Outcome(val outputName: String, val downsampled: Boolean, val bytes: Long)

    fun convert(item: ImageItem, options: ConvertOptions, store: OutputStore): Outcome {
        val decoded = decode(item.uri)
        var bitmap = applyOrientation(decoded.bitmap, item.uri)
        // JPEG 没有 alpha 通道，带透明区域时需要先铺白底，否则透明处会变黑
        if (options.format == OutputFormat.JPG && bitmap.hasAlpha()) {
            bitmap = flatten(bitmap)
        }

        val format = options.format.compressFormat(options.webpLossless)
        val quality = options.format.effectiveQuality(options.quality, options.webpLossless)
        val sink = store.open(item.outputName(options.format), options.format.mime)

        var success = false
        try {
            sink.stream.use { out ->
                if (!bitmap.compress(format, quality, out)) {
                    throw IOException(context.getString(R.string.err_encode))
                }
                out.flush()
            }
            success = true
        } catch (e: IOException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw IOException(context.getString(R.string.err_too_large))
        } catch (e: Exception) {
            throw IOException(e.message ?: context.getString(R.string.err_write), e)
        } finally {
            sink.finish(success)
        }
        return Outcome(sink.displayName, decoded.downsampled, sink.sizeBytes())
    }

    // ---------------------------------------------------------------- 解码

    private class Decoded(val bitmap: Bitmap, val downsampled: Boolean)

    private fun decode(uri: Uri): Decoded {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (e: Exception) {
            throw IOException(context.getString(R.string.err_decode), e)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException(context.getString(R.string.err_decode))
        }

        // 超大图先按 1/2、1/4 … 降采样重试，避免 OOM
        var sample = 1
        while (sample <= 16) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            try {
                val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: throw IOException(context.getString(R.string.err_decode))
                return Decoded(bitmap, sample > 1)
            } catch (e: OutOfMemoryError) {
                sample *= 2
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException(context.getString(R.string.err_decode), e)
            }
        }
        throw IOException(context.getString(R.string.err_too_large))
    }

    // ---------------------------------------------------------------- 方向

    private fun applyOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            throw IOException(context.getString(R.string.err_too_large))
        }
    }

    // ---------------------------------------------------------------- 合成

    private fun flatten(bitmap: Bitmap): Bitmap {
        return try {
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            out
        } catch (e: OutOfMemoryError) {
            throw IOException(context.getString(R.string.err_too_large))
        }
    }
}
