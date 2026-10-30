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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.sylphinford.imageconv.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var store: OutputStore
    private lateinit var engine: ConversionEngine
    private lateinit var adapter: ImageAdapter

    private val items = mutableListOf<ImageItem>()
    private var job: Job? = null
    private var busy = false
    private var currentFormat = OutputFormat.JPG
    private var webpLossless = false

    // ------------------------------------------------------------ 选择器

    private val pickImages = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { persistRead(it) }
            addUris(uris)
        }
    }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        store.treeUri = uri
        prefs.treeUri = uri
        updateDirLabel()
        toast(getString(R.string.toast_dir_changed))
    }

    private val requestWrite = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startConversion() else toast(getString(R.string.toast_need_storage))
    }

    // ------------------------------------------------------------ 生命周期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        store = OutputStore(this).also { it.treeUri = prefs.treeUri }
        engine = ConversionEngine(this)

        adapter = ImageAdapter(items) { index ->
            if (index in items.indices) {
                items.removeAt(index)
                adapter.notifyItemRemoved(index)
                updateUi()
            }
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        setupControls()
        restoreState()
        handleIntent(intent)
        updateUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ 控件

    private fun setupControls() {
        binding.formatGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentFormat = when (checkedId) {
                R.id.btnPng -> OutputFormat.PNG
                R.id.btnWebp -> OutputFormat.WEBP
                else -> OutputFormat.JPG
            }
            prefs.format = currentFormat
            updateOptionRows()
        }

        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            webpLossless = checkedId == R.id.btnLossless
            prefs.webpLossless = webpLossless
            updateOptionRows()
        }

        binding.qualitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val quality = progress + 1
                binding.qualityValue.text = getString(R.string.quality_value, quality)
                if (fromUser) prefs.quality = quality
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.btnAdd.setOnClickListener { launchSaf { pickImages.launch(arrayOf("image/*")) } }
        binding.btnConvert.setOnClickListener { startConversion() }
        binding.btnClear.setOnClickListener { clearItems() }
        binding.btnClearDone.setOnClickListener { clearConverted() }
        binding.btnDir.setOnClickListener { showDirDialog() }

        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_licenses) {
                startActivity(Intent(this, LicensesActivity::class.java))
                true
            } else {
                false
            }
        }
    }

    private fun restoreState() {
        currentFormat = prefs.format
        webpLossless = prefs.webpLossless

        binding.formatGroup.check(
            when (currentFormat) {
                OutputFormat.JPG -> R.id.btnJpg
                OutputFormat.PNG -> R.id.btnPng
                OutputFormat.WEBP -> R.id.btnWebp
            }
        )
        binding.modeGroup.check(if (webpLossless) R.id.btnLossless else R.id.btnLossy)

        val quality = prefs.quality
        binding.qualitySeek.progress = quality - 1
        binding.qualityValue.text = getString(R.string.quality_value, quality)

        updateOptionRows()
        updateDirLabel()
    }

    private fun updateOptionRows() {
        val isWebp = currentFormat == OutputFormat.WEBP
        binding.modeRow.isVisible = isWebp

        val qualityRelevant = currentFormat != OutputFormat.PNG && !(isWebp && webpLossless)
        binding.qualityRow.isVisible = qualityRelevant
        binding.qualityHint.isVisible = !qualityRelevant
        if (!qualityRelevant) {
            binding.qualityHint.setText(
                if (currentFormat == OutputFormat.PNG) {
                    R.string.hint_png_no_quality
                } else {
                    R.string.hint_lossless_no_quality
                }
            )
        }
    }

    private fun updateDirLabel() {
        binding.dirText.text = store.label()
    }

    /** 极少数精简系统里没有文件选择器，别让 launch 抛异常把应用带走 */
    private fun launchSaf(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            toast(getString(R.string.toast_no_picker))
        }
    }

    private fun updateUi() {
        val doneCount = items.count { it.state == ImageItem.State.DONE }
        binding.emptyState.isVisible = items.isEmpty()
        binding.countText.text = getString(R.string.count_selected, items.size)
        binding.btnClear.isVisible = items.isNotEmpty() && !busy
        binding.btnClearDone.isVisible = doneCount > 0 && !busy
        binding.btnClearDone.text = getString(R.string.btn_clear_done, doneCount)
        binding.btnConvert.isEnabled = items.isNotEmpty() && !busy
        binding.btnAdd.isEnabled = !busy
        binding.btnDir.isEnabled = !busy
    }

    private fun showDirDialog() {
        val labels = arrayOf(getString(R.string.btn_change_dir), getString(R.string.btn_reset_dir))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.label_output_dir)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    launchSaf { pickFolder.launch(null) }
                } else {
                    store.treeUri = null
                    prefs.treeUri = null
                    updateDirLabel()
                    toast(getString(R.string.toast_dir_reset))
                }
            }
            .show()
    }

    // ------------------------------------------------------------ 接收外部文件

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> extractStream(intent)?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> uris.addAll(extractStreams(intent))
            Intent.ACTION_VIEW -> intent.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) {
            val clip = intent.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
        }
        if (uris.isEmpty()) return

        uris.forEach { persistRead(it) }
        addUris(uris)
    }

    @Suppress("DEPRECATION")
    private fun extractStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun extractStreams(intent: Intent): List<Uri> {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        return list ?: emptyList()
    }

    /** 分享进来的 URI 通常不可持久化，失败无所谓，读权限在本次任务内有效 */
    private fun persistRead(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------ 列表维护

    private fun addUris(uris: List<Uri>) {
        val fresh = uris.filter { uri -> items.none { it.uri == uri } }
        if (fresh.isEmpty()) {
            toast(getString(R.string.toast_dup))
            return
        }
        lifecycleScope.launch {
            binding.progressArea.isVisible = true
            binding.progress.isIndeterminate = true
            binding.statusText.setText(R.string.status_reading)

            val loaded = withContext(Dispatchers.IO) {
                fresh.map { uri -> runCatching { readMeta(uri) } }
            }
            items.addAll(loaded.mapNotNull { it.getOrNull() })
            adapter.notifyDataSetChanged()

            binding.progress.isIndeterminate = false
            if (!busy) binding.progressArea.isVisible = false

            val failed = loaded.count { it.isFailure }
            if (failed > 0) toast(getString(R.string.toast_some_failed, failed))
            updateUi()
        }
    }

    private fun readMeta(uri: Uri): ImageItem {
        var name: String? = null
        var size = -1L
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException(getString(R.string.err_decode))
        }

        val displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "image"
        return ImageItem(uri, displayName, size, bounds.outWidth, bounds.outHeight)
    }

    private fun clearItems() {
        if (busy) return
        items.clear()
        adapter.notifyDataSetChanged()
        binding.progressArea.isVisible = false
        updateUi()
        toast(getString(R.string.toast_cleared))
    }

    /**
     * 只移除转换成功的条目，失败的（和未转换的）留在列表里，
     * 方便换个格式重试时不必手动挑。已经写出的输出文件不受影响。
     */
    private fun clearConverted() {
        if (busy) return
        val done = items.count { it.state == ImageItem.State.DONE }
        if (done == 0) return
        items.removeAll { it.state == ImageItem.State.DONE }
        adapter.notifyDataSetChanged()
        if (items.isEmpty()) binding.progressArea.isVisible = false
        updateUi()
        toast(getString(R.string.toast_cleared_done, done))
    }

    // ------------------------------------------------------------ 转换

    private fun startConversion() {
        if (busy) return
        if (items.isEmpty()) {
            toast(getString(R.string.toast_no_image))
            return
        }
        // Android 9 及以下写公共 Pictures 目录需要存储权限
        if (store.treeUri == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        val options = ConvertOptions(
            format = currentFormat,
            quality = binding.qualitySeek.progress + 1,
            webpLossless = webpLossless
        )

        busy = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateUi()

        items.forEach {
            it.state = ImageItem.State.PENDING
            it.message = null
            it.outputBytes = -1L
        }
        adapter.notifyDataSetChanged()

        binding.progressArea.isVisible = true
        binding.progress.isIndeterminate = false
        binding.progress.max = items.size
        binding.progress.progress = 0
        binding.statusText.text = ""

        job = lifecycleScope.launch {
            var ok = 0
            var failed = 0
            items.forEachIndexed { index, item ->
                item.state = ImageItem.State.RUNNING
                adapter.notifyItemChanged(index)
                binding.statusText.text = getString(
                    R.string.status_converting, index + 1, items.size, item.name
                )

                val result = withContext(Dispatchers.IO) {
                    runCatching { engine.convert(item, options, store) }
                }

                if (result.isSuccess) {
                    item.state = ImageItem.State.DONE
                    item.message = result.getOrNull()?.outputName
                    item.outputBytes = result.getOrNull()?.bytes ?: -1L
                    ok++
                } else {
                    item.state = ImageItem.State.FAILED
                    item.message = friendlyMessage(result.exceptionOrNull())
                    failed++
                }
                adapter.notifyItemChanged(index)
                binding.progress.progress = index + 1
            }

            busy = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            updateDirLabel()

            val message = if (failed == 0) {
                getString(R.string.status_done_all, ok, store.label())
            } else {
                getString(R.string.status_done_partial, ok, failed, store.label())
            }
            binding.statusText.text = message
            toast(message)
            updateUi()
        }
    }

    private fun friendlyMessage(error: Throwable?): String = when (error) {
        null -> getString(R.string.err_encode)
        is OutOfMemoryError -> getString(R.string.err_too_large)
        is IOException -> error.message ?: getString(R.string.err_write)
        else -> error.message ?: getString(R.string.err_encode)
    }

    // ------------------------------------------------------------ 杂项

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
