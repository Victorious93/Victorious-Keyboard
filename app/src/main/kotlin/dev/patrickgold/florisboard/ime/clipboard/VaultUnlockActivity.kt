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

package dev.patrickgold.florisboard.ime.clipboard

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultLock
import dev.patrickgold.florisboard.ime.clipboard.lanes.VaultSession

/**
 * Asks for the device biometric or screen lock, then opens the in-memory vault window.
 * The activity is not exported. It does not receive clipboard text.
 */
class VaultUnlockActivity : ComponentActivity() {
    private var asked = false

    private val confirm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val timeout = intent.getLongExtra(EXTRA_TIMEOUT_MS, VaultLock.DEFAULT_TIMEOUT_MS)
            VaultSession.unlock(System.currentTimeMillis(), timeout)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (asked) return
        asked = true
        val keyguard = getSystemService(KeyguardManager::class.java)
        val prompt = keyguard?.createConfirmDeviceCredentialIntent(
            getString(R.string.clipboard__vault_unlock_title),
            getString(R.string.clipboard__vault_unlock_message),
        )
        if (prompt == null) {
            finish()
            return
        }
        confirm.launch(prompt)
    }

    companion object {
        const val EXTRA_TIMEOUT_MS = "timeoutMs"
    }
}
