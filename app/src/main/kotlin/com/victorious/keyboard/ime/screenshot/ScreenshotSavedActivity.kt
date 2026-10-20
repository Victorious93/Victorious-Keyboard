/*
 * Copyright (C) 2026 Victorious Keyboard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.victorious.keyboard.ime.screenshot

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.victorious.keyboard.R

/**
 * Shown after the keyboard has hidden itself. A toast cannot carry a Copy button, so this small dialog does.
 * Not exported. The image URI is not logged.
 */
class ScreenshotSavedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null || uri.scheme != "content") {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(R.string.screenshot__saved_body)
            .setPositiveButton(R.string.screenshot__copy) { _, _ -> copyImage(uri) }
            .setNegativeButton(R.string.screenshot__done) { _, _ -> }
            .setOnDismissListener { finish() }
            .show()
    }

    private fun copyImage(uri: Uri) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newUri(contentResolver, "image/png", uri))
        Toast.makeText(this, R.string.screenshot__copied, Toast.LENGTH_SHORT).show()
    }
}
