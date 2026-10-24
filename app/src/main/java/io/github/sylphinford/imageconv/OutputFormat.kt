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

import android.graphics.Bitmap
import android.os.Build

/** 支持的输出格式 */
enum class OutputFormat(val ext: String, val mime: String, val labelRes: Int) {
    JPG("jpg", "image/jpeg", R.string.format_jpg),
    PNG("png", "image/png", R.string.format_png),
    WEBP("webp", "image/webp", R.string.format_webp);

    /** 该格式是否需要质量参数 */
    val supportsQuality: Boolean
        get() = this != PNG

    /**
     * 该格式允许的最大单边像素数，0 表示没有规格限制。
     *
     * WebP 的宽高各用 14 bit 存储，任一边超过 16383 时编码器会直接失败
     * （`Bitmap.compress` 返回 false），因此需要在编码前拦下来。
     * JPG 的上限是 65535，PNG 没有规格限制，实际都受内存约束，不需要检查。
     */
    val maxDimension: Int
        get() = if (this == WEBP) WEBP_MAX_DIMENSION else 0

    companion object {
        const val WEBP_MAX_DIMENSION = 16383
    }

    fun compressFormat(webpLossless: Boolean): Bitmap.CompressFormat = when (this) {
        JPG -> Bitmap.CompressFormat.JPEG
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (webpLossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            // Android 11 以下没有独立的无损枚举，quality = 100 即为无损
            Bitmap.CompressFormat.WEBP
        }
    }

    /**
     * 实际传给 Bitmap.compress 的质量值。
     * PNG 忽略质量；Android 11 以下的无损 WEBP 用 100 表示无损。
     */
    fun effectiveQuality(quality: Int, webpLossless: Boolean): Int = when {
        this == PNG -> 100
        this == WEBP && webpLossless && Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> 100
        else -> quality.coerceIn(1, 100)
    }
}

/** 一次转换任务的参数 */
data class ConvertOptions(
    val format: OutputFormat,
    val quality: Int,
    val webpLossless: Boolean
)
