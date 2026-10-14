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

import dev.patrickgold.florisboard.ime.clipboard.history.ClipEntry
import dev.patrickgold.florisboard.ime.clipboard.history.HistoryPolicy
import dev.patrickgold.florisboard.ime.clipboard.history.InsertPlan
import dev.patrickgold.florisboard.ime.clipboard.history.SensitiveDetector
import dev.patrickgold.florisboard.ime.clipboard.history.SensitiveReason
import dev.patrickgold.florisboard.ime.clipboard.vault.ClipboardBackupService
import dev.patrickgold.florisboard.ime.clipboard.vault.ClipboardEntryRepository
import dev.patrickgold.florisboard.ime.clipboard.vault.ExportFormat
import dev.patrickgold.florisboard.ime.clipboard.vault.ExportOptions
import dev.patrickgold.florisboard.ime.clipboard.vault.HistorySnapshot
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportMode
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportPlan
import dev.patrickgold.florisboard.ime.clipboard.backup.ImportPlanner
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.util.Base64

class ClipLanesTest : FunSpec({
    val day = 24L * 60 * 60 * 1000
    val card = "4111 1111 1111 1111"
    val iban = "GB82" + "WEST" + "12345698765432"
    val otp = "otp " + "482913"

    test("password fields and Android's sensitive flag go to the vault") {
        val password = ClipRouter.route(ClipRouteInput("hello", fromPasswordField = true))
        password shouldBe ClipRoute.Vault(VaultReason.PASSWORD_FIELD, null)
        val flagged = ClipRouter.route(ClipRouteInput("hello", androidSensitive = true))
        flagged shouldBe ClipRoute.Vault(VaultReason.ANDROID_SENSITIVE, null)
        ClipRouter.route(ClipRouteInput("   ", fromPasswordField = true)) shouldBe ClipRoute.Dropped
    }

    test("opt-in detection routes secrets to the vault and leaves ordinary text alone") {
        val detected = ClipRouter.route(ClipRouteInput(card, detectSecrets = true))
        detected shouldBe ClipRoute.Vault(VaultReason.DETECTED, DetectedSecret.CARD)
        ClipRouter.route(ClipRouteInput(iban, detectSecrets = true)) shouldBe
            ClipRoute.Vault(VaultReason.DETECTED, DetectedSecret.IBAN)
        ClipRouter.route(ClipRouteInput(otp, detectSecrets = true)) shouldBe
            ClipRoute.Vault(VaultReason.DETECTED, DetectedSecret.OTP)
        ClipRouter.route(ClipRouteInput("Meet at 5pm", detectSecrets = true)) shouldBe ClipRoute.Regular
        ClipRouter.route(ClipRouteInput("482913", detectSecrets = true)) shouldBe ClipRoute.Regular
    }

    test("with detection off, secrets stay out of the regular clipboard unless the user allows it") {
        ClipRouter.route(ClipRouteInput(card)) shouldBe ClipRoute.Dropped
        ClipRouter.route(ClipRouteInput(card, storeDetectedInRegular = true)) shouldBe ClipRoute.Regular
        ClipRouter.route(ClipRouteInput("notes for later")) shouldBe ClipRoute.Regular
    }

    test("a manual move follows the chosen lane") {
        ClipRouter.manualMove(ClipLane.VAULT) shouldBe ClipLane.VAULT
        ClipRouter.manualMove(ClipLane.REGULAR) shouldBe ClipLane.REGULAR
    }

    test("iban and otp detection report a reason and not the matched text") {
        SensitiveDetector.check(iban) shouldBe SensitiveReason.IBAN
        SensitiveDetector.check("GB82 WEST 1234 5698 7654 32") shouldBe SensitiveReason.IBAN
        SensitiveDetector.check("not an iban GB00XXXX") shouldBe null
        SensitiveDetector.check(otp) shouldBe SensitiveReason.OTP
        SensitiveDetector.check("verification code: 100200") shouldBe SensitiveReason.OTP
        SensitiveDetector.check("482913") shouldBe null
        SensitiveDetector.detectedSecret(iban) shouldBe DetectedSecret.IBAN
        SensitiveReason.entries.forEach { it.name.contains("482913") shouldBe false }
    }

    test("unpinned vault items expire, pins and never do not") {
        val created = 1_000L
        VaultExpiry.isDue(created, created + day, day, pinned = false) shouldBe true
        VaultExpiry.isDue(created, created + day - 1, day, pinned = false) shouldBe false
        VaultExpiry.isDue(created, created + day * 30, day, pinned = true) shouldBe false
        VaultExpiry.isDue(created, created + day * 30, VaultExpiry.NEVER, pinned = false) shouldBe false
        VaultLifetime.DEFAULT.ttlMs() shouldBe day
        VaultLifetime.NEVER.ttlMs() shouldBe VaultExpiry.NEVER
    }

    test("the vault locks again after the timeout unless the requirement is off") {
        VaultLock.isLocked(requireCredential = false, unlockedUntilMs = 0, nowMs = 50) shouldBe false
        VaultLock.isLocked(requireCredential = true, unlockedUntilMs = 100, nowMs = 99) shouldBe false
        VaultLock.isLocked(requireCredential = true, unlockedUntilMs = 100, nowMs = 100) shouldBe true
        VaultLock.isLocked(requireCredential = true, unlockedUntilMs = 0, nowMs = 1) shouldBe true
    }

    test("vault items are not suggestion candidates and stay out of exports") {
        ClipVisibility.showAsSuggestion(ClipLane.VAULT) shouldBe false
        ClipVisibility.showAsSuggestion(ClipLane.REGULAR) shouldBe true
        ClipVisibility.laneOfSensitiveClip(sensitive = true) shouldBe ClipLane.VAULT
        VaultExportPolicy.include(encryptedContainer = false, explicitOptIn = true) shouldBe false
        VaultExportPolicy.include(encryptedContainer = true, explicitOptIn = false) shouldBe false
        VaultExportPolicy.include(encryptedContainer = true, explicitOptIn = true) shouldBe true
        VaultMask.bullets(2) shouldBe "••••"
        VaultMask.bullets(40).length shouldBe 12
    }

    test("the vault file stores ciphertext, expires unpinned rows, and refuses a damaged file") {
        val dir = File("build/tmp/vault-lanes").apply { mkdirs() }
        val file = File(dir, "sensitive-clipboard.vault")
        file.delete()
        val vault = SensitiveVault(file, PrefixCipher(), policy = { HistoryPolicy(maxEntries = 2) }, clock = { 10 })
        val secret = "VAULTPLAINTEXTTOKEN"
        vault.offer(secret, VaultReason.DETECTED) shouldBe InsertPlan.Add(emptyList())
        file.readText() shouldNotContain secret
        vault.loadPlain().single().text shouldBe secret

        vault.setPinned(vault.loadPlain().single().id, true)
        vault.deleteExpired(nowMs = 10 + day, ttlMs = day) shouldBe 0
        vault.loadPlain().single().pinned shouldBe true

        vault.offer("second", VaultReason.MOVED)
        vault.deleteExpired(nowMs = 10 + day, ttlMs = day) shouldBe 1
        vault.loadPlain().map { it.text } shouldBe listOf(secret)

        val imported = "IMPORTEDSECRETTOKEN"
        val existingId = vault.loadPlain().single().id
        val plan = ImportPlanner.plan(
            vault.loadPlain().map { it.toEntry() },
            listOf(
                ClipEntry(id = existingId, text = secret, createdAtMs = 10, pinned = true),
                ClipEntry(id = "new-row", text = imported, createdAtMs = 11),
            ),
            ImportMode.MERGE,
        )
        vault.applyImport(plan)
        file.readText() shouldNotContain imported
        val after = vault.loadPlain().associateBy { it.id }
        after.getValue(existingId).reason shouldBe VaultReason.DETECTED
        after.getValue(existingId).pinned shouldBe true
        after.getValue("new-row").reason shouldBe VaultReason.IMPORTED
        after.getValue("new-row").text shouldBe imported

        val damaged = File(dir, "damaged.vault")
        damaged.writeText("not a vault")
        val broken = SensitiveVault(damaged, PrefixCipher())
        shouldThrow<VaultFileUnreadableException> { broken.offer("x", VaultReason.MOVED) }
        damaged.readText() shouldBe "not a vault"
    }

    test("an encrypted export includes the vault only when the user opts in") {
        val regular = ClipEntry(id = "reg", text = "shopping list", createdAtMs = 1)
        val secret = ClipEntry(id = "sec", text = "VAULTPLAINTEXTTOKEN", createdAtMs = 2)
        val repo = object : ClipboardEntryRepository {
            override suspend fun snapshot() = HistorySnapshot(listOf(regular), emptySet())
            override suspend fun apply(plan: ImportPlan) = Unit
        }
        val service = ClipboardBackupService(repo, "test")
        val password = "correct horse battery".toCharArray()
        val without = service.export(ExportOptions(ExportFormat.ENCRYPTED_ZIP, password, vaultEntries = listOf(secret)))
        without.bytes.toString(Charsets.ISO_8859_1) shouldNotContain secret.text
        without.skippedSensitive shouldBe 0

        val withVault = service.export(
            ExportOptions(
                ExportFormat.ENCRYPTED_ZIP,
                "correct horse battery".toCharArray(),
                includeVault = true,
                vaultEntries = listOf(secret),
            ),
        )
        service.inspect(withVault.bytes, "correct horse battery".toCharArray()).parsed.entries.map { it.text }
            .toSet() shouldBe setOf("shopping list", secret.text)

        val json = service.export(
            ExportOptions(ExportFormat.JSON, includeVault = true, vaultEntries = listOf(secret)),
        )
        json.bytes.toString(Charsets.UTF_8) shouldNotContain secret.text
        json.skippedSensitive shouldBe 1
        service.inspect(json.bytes, null).parsed.entries.map { it.id } shouldBe listOf("reg")
    }
})

private class PrefixCipher : VaultTextCipher {
    override fun encrypt(plain: String, context: String): String {
        val raw = "$context\u0000$plain".toByteArray(Charsets.UTF_8)
        return "vkenc1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    override fun decrypt(stored: String, context: String): String {
        val raw = String(Base64.getUrlDecoder().decode(stored.removePrefix("vkenc1:")), Charsets.UTF_8)
        val split = raw.split('\u0000', limit = 2)
        if (split.size != 2 || split[0] != context) error("aad")
        return split[1]
    }
}
