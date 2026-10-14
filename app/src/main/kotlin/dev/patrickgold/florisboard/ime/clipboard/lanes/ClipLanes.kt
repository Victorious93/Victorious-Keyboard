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

package dev.patrickgold.florisboard.ime.clipboard.lanes

import dev.patrickgold.florisboard.ime.clipboard.history.SensitiveDetector
import dev.patrickgold.florisboard.ime.clipboard.history.SensitiveReason
import kotlinx.coroutines.flow.MutableStateFlow

/** Which clipboard a clip belongs to. The two stores are not interchangeable. */
enum class ClipLane {
    REGULAR,
    VAULT,
}

/** Why a clip was placed in the vault. This is a label, never the clip text. */
enum class VaultReason {
    PASSWORD_FIELD,
    ANDROID_SENSITIVE,
    DETECTED,
    MOVED,
    /** Written by a backup import. Older files never use this name. */
    IMPORTED,
}

/**
 * Kind of secret the opt-in detector reported. The finding is only this label.
 * The matched characters are not kept.
 */
enum class DetectedSecret {
    CARD,
    IBAN,
    API_TOKEN,
    PRIVATE_KEY,
    OTP,
}

/** How long unpinned vault items are kept. [NEVER] disables expiry. Pins are always kept. */
enum class VaultLifetime {
    HOUR_1,
    HOUR_6,
    HOUR_24,
    DAYS_7,
    NEVER,
    ;

    fun ttlMs(): Long = when (this) {
        HOUR_1 -> 3_600_000L
        HOUR_6 -> 6 * 3_600_000L
        HOUR_24 -> 24 * 3_600_000L
        DAYS_7 -> 7 * 24 * 3_600_000L
        NEVER -> VaultExpiry.NEVER
    }

    companion object {
        val DEFAULT = HOUR_24
    }
}

sealed class ClipRoute {
    data object Regular : ClipRoute()

    data class Vault(val reason: VaultReason, val detected: DetectedSecret?) : ClipRoute()

    /** Not stored in either clipboard. */
    data object Dropped : ClipRoute()
}

data class ClipRouteInput(
    val text: String,
    val fromPasswordField: Boolean = false,
    val androidSensitive: Boolean = false,
    /** Opt-in. When on, detected cards, IBANs, keys, tokens and OTP codes go to the vault. */
    val detectSecrets: Boolean = false,
    /**
     * Existing regular-history switch. When detection-to-vault is off and this is off, a detected
     * secret is dropped instead of being written to the regular clipboard.
     */
    val storeDetectedInRegular: Boolean = false,
)

object ClipRouter {
    fun route(input: ClipRouteInput): ClipRoute {
        if (input.text.isBlank()) return ClipRoute.Dropped
        if (input.fromPasswordField) return ClipRoute.Vault(VaultReason.PASSWORD_FIELD, null)
        if (input.androidSensitive) return ClipRoute.Vault(VaultReason.ANDROID_SENSITIVE, null)
        val detected = SensitiveDetector.detectedSecret(input.text)
        if (detected != null && input.detectSecrets) {
            return ClipRoute.Vault(VaultReason.DETECTED, detected)
        }
        if (detected != null && !input.storeDetectedInRegular) return ClipRoute.Dropped
        return ClipRoute.Regular
    }

    /** A manual move follows the user's choice and does not run detection again. */
    fun manualMove(to: ClipLane): ClipLane = to
}

object VaultExpiry {
    const val NEVER = -1L

    /** Pinned items stay. A negative [ttlMs] means never expire. */
    fun isDue(createdAtMs: Long, nowMs: Long, ttlMs: Long, pinned: Boolean): Boolean {
        if (pinned || ttlMs < 0L) return false
        return nowMs - createdAtMs >= ttlMs
    }
}

object VaultLock {
    const val DEFAULT_TIMEOUT_MS = 5L * 60 * 1000

    /**
     * Locked when a credential is required and [nowMs] is at or past [unlockedUntilMs].
     * [unlockedUntilMs] of 0 means never unlocked in this process.
     */
    fun isLocked(requireCredential: Boolean, unlockedUntilMs: Long, nowMs: Long): Boolean {
        if (!requireCredential) return false
        return nowMs >= unlockedUntilMs
    }
}

object ClipVisibility {
    /** Vault clips are never suggestion-bar candidates. Regular clips are. */
    fun showAsSuggestion(lane: ClipLane): Boolean = lane == ClipLane.REGULAR

    fun laneOfSensitiveClip(sensitive: Boolean): ClipLane = if (sensitive) ClipLane.VAULT else ClipLane.REGULAR
}

object VaultExportPolicy {
    /**
     * Vault items are left out of every backup and export unless the user opts in and the
     * container is the password-encrypted format.
     */
    fun include(encryptedContainer: Boolean, explicitOptIn: Boolean): Boolean =
        encryptedContainer && explicitOptIn
}

/** In-memory unlock window. Process death locks the vault again. */
object VaultSession {
    val unlockedUntilMs = MutableStateFlow(0L)

    fun unlock(nowMs: Long, timeoutMs: Long) {
        unlockedUntilMs.value = nowMs + timeoutMs.coerceAtLeast(1_000L)
    }

    fun lock() {
        unlockedUntilMs.value = 0L
    }
}

/** List label that does not reveal any character of the secret. */
object VaultMask {
    fun bullets(charCount: Int): String = "•".repeat(charCount.coerceIn(4, 12))
}
