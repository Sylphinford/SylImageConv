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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.sylphinford.imageconv.databinding.ItemImageBinding

class ImageAdapter(
    private val items: List<ImageItem>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.Holder>() {

    class Holder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val binding = holder.binding

        binding.title.text = item.name
        val failedReason = item.message
        binding.subtitle.text = when {
            item.state == ImageItem.State.FAILED && !failedReason.isNullOrBlank() -> failedReason
            item.outputBytes > 0 -> context.getString(
                R.string.item_meta_converted,
                item.width,
                item.height,
                formatSize(item.sizeBytes),
                formatSize(item.outputBytes)
            )
            else -> context.getString(
                R.string.item_meta,
                item.width,
                item.height,
                formatSize(item.sizeBytes)
            )
        }

        val state = binding.state
        when (item.state) {
            ImageItem.State.PENDING -> state.visibility = View.GONE
            ImageItem.State.RUNNING -> {
                state.visibility = View.VISIBLE
                state.setText(R.string.state_running)
                state.setTextColor(ContextCompat.getColor(context, R.color.state_running))
            }
            ImageItem.State.DONE -> {
                state.visibility = View.VISIBLE
                state.setText(R.string.state_done)
                state.setTextColor(ContextCompat.getColor(context, R.color.state_ok))
            }
            ImageItem.State.FAILED -> {
                state.visibility = View.VISIBLE
                state.setText(R.string.state_failed)
                state.setTextColor(ContextCompat.getColor(context, R.color.state_error))
            }
        }

        binding.btnRemove.setOnClickListener {
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION) onRemove(index)
        }

        ThumbnailLoader.load(context, item.uri, binding.thumb)
    }
}
