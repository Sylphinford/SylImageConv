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
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 列表缩略图加载，带内存缓存 */
object ThumbnailLoader {

    private const val TARGET_PX = 240

    private val cache = object : LruCache<String, Bitmap>(64) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun load(context: Context, uri: Uri, target: ImageView) {
        val key = uri.toString()
        target.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            target.setImageBitmap(cached)
            return
        }
        target.setImageResource(R.drawable.ic_image_placeholder)
        val appContext = context.applicationContext
        scope.launch {
            val bitmap = runCatching { decode(appContext, uri) }.getOrNull()
            if (bitmap != null) cache.put(key, bitmap)
            withContext(Dispatchers.Main) {
                if (target.tag == key && bitmap != null) {
                    target.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_PX || bounds.outHeight / (sample * 2) >= TARGET_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }
}
