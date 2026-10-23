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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.sylphinford.imageconv.databinding.ActivityLicensesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 开源许可页面。
 *
 * 展示 assets/licenses 下的第三方组件清单与 Apache 2.0 协议全文，
 * 用于履行 Apache License 2.0 第 4 条的分发义务。
 */
class LicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }

        lifecycleScope.launch {
            binding.licenseText.text = withContext(Dispatchers.IO) { buildText() }
        }
    }

    private fun buildText(): String {
        val notice = readAsset("licenses/NOTICE.txt")
        val thirdParty = readAsset("licenses/third_party_licenses.txt")
        val apache = readAsset("licenses/apache-2.0.txt")
        if (notice == null && thirdParty == null && apache == null) {
            return getString(R.string.err_licenses)
        }

        return buildString {
            if (notice != null) append(notice)
            if (thirdParty != null) {
                if (isNotEmpty()) append("\n\n")
                append(thirdParty)
            }
            if (apache != null) {
                if (isNotEmpty()) append("\n\n")
                append("=".repeat(60)).append('\n')
                append("Apache License, Version 2.0\n")
                append("=".repeat(60)).append('\n')
                append(apache)
            }
        }
    }

    private fun readAsset(name: String): String? =
        runCatching { assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
}
