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

import android.net.Uri

/** 列表里的一张待转换图片 */
data class ImageItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    var state: State = State.PENDING,
    var message: String? = null,
    /** 转换后的文件大小，未转换或取不到时为 -1 */
    var outputBytes: Long = -1L
) {
    enum class State { PENDING, RUNNING, DONE, FAILED }

    /** 生成输出文件名：原文件名 + 新扩展名，去掉非法字符 */
    fun outputName(format: OutputFormat): String {
        val raw = name.substringBeforeLast('.', name).ifBlank { "image" }
        val safe = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trim('.')
            .ifBlank { "image" }
        return "$safe.${format.ext}"
    }
}
