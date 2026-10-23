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
import androidx.core.net.toUri
import android.net.Uri

/** 轻量本地配置 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("syl_imageconv", Context.MODE_PRIVATE)

    var format: OutputFormat
        get() = runCatching {
            OutputFormat.valueOf(sp.getString(KEY_FORMAT, OutputFormat.JPG.name)!!)
        }.getOrDefault(OutputFormat.JPG)
        set(value) = sp.edit().putString(KEY_FORMAT, value.name).apply()

    var quality: Int
        get() = sp.getInt(KEY_QUALITY, 90).coerceIn(1, 100)
        set(value) = sp.edit().putInt(KEY_QUALITY, value.coerceIn(1, 100)).apply()

    var webpLossless: Boolean
        get() = sp.getBoolean(KEY_WEBP_LOSSLESS, false)
        set(value) = sp.edit().putBoolean(KEY_WEBP_LOSSLESS, value).apply()

    var treeUri: Uri?
        get() = sp.getString(KEY_TREE_URI, null)?.let { runCatching { it.toUri() }.getOrNull() }
        set(value) = sp.edit().putString(KEY_TREE_URI, value?.toString()).apply()

    private companion object {
        const val KEY_FORMAT = "format"
        const val KEY_QUALITY = "quality"
        const val KEY_WEBP_LOSSLESS = "webp_lossless"
        const val KEY_TREE_URI = "tree_uri"
    }
}
