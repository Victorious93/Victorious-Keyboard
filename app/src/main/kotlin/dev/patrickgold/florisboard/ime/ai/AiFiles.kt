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

package dev.patrickgold.florisboard.ime.ai

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Model files and settings under noBackupFilesDir/ai. Nothing here is logged. */
class AiFiles(private val root: File) {
    val modelsDir: File get() = File(root, "models")

    fun ensure() {
        modelsDir.mkdirs()
    }

    fun readConfig(processors: Int): AiConfig {
        val file = File(root, "config.json")
        if (!file.isFile) return AiConfig(threads = AiConfigRules.defaultThreads(processors))
        return try {
            AiConfigCodec.decode(file.readText(), processors)
        } catch (_: IOException) {
            AiConfig(threads = AiConfigRules.defaultThreads(processors))
        }
    }

    fun writeConfig(config: AiConfig) {
        ensure()
        writeAtomic(File(root, "config.json"), AiConfigCodec.encode(AiConfigRules.clamp(config)))
    }

    fun readModels(): List<ImportedModel> {
        val file = File(root, "models.json")
        if (!file.isFile) return emptyList()
        return try {
            ModelIndexCodec.decode(file.readText()).filter { modelFile(it.id)?.isFile == true }
        } catch (_: IOException) {
            emptyList()
        }
    }

    fun writeModels(models: List<ImportedModel>) {
        ensure()
        writeAtomic(File(root, "models.json"), ModelIndexCodec.encode(models))
    }

    fun modelFile(id: String): File? {
        val name = ModelImport.fileName(id) ?: return null
        return File(modelsDir, name)
    }

    fun delete(id: String, models: List<ImportedModel>): List<ImportedModel> {
        modelFile(id)?.delete()
        File(modelsDir, "$id.gguf.partial").delete()
        return models.filter { it.id != id }
    }

    fun import(
        displayName: String,
        declaredSize: Long?,
        freeBytes: Long,
        existing: List<ImportedModel>,
        acknowledged: Boolean,
        input: InputStream,
        streamCap: Long = ModelImport.MAX_BYTES,
    ): ImportOutcome {
        val decision = ModelImport.decide(declaredSize, freeBytes, existing.size, acknowledged)
        if (decision != ImportDecision.ACCEPT) return ImportOutcome.Blocked(decision)
        ensure()
        val id = UUID.randomUUID().toString()
        val finalName = ModelImport.fileName(id) ?: return ImportOutcome.Blocked(ImportDecision.REJECT_NAME)
        val partial = File(modelsDir, "$finalName.partial")
        val dest = File(modelsDir, finalName)
        var written = 0L
        val header = ByteArray(4)
        var headerFilled = 0
        try {
            input.use { source ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        if (headerFilled < 4) {
                            val take = minOf(4 - headerFilled, n)
                            buffer.copyInto(header, headerFilled, 0, take)
                            headerFilled += take
                            if (headerFilled == 4 && !GgufHeader.matches(header)) throw BadMagic()
                        }
                        written += n
                        if (written > streamCap) throw TooLarge()
                        out.write(buffer, 0, n)
                    }
                    try {
                        out.channel.force(true)
                    } catch (_: IOException) {
                        // The rename below is what makes the file visible.
                    }
                }
            }
        } catch (_: BadMagic) {
            partial.delete()
            return ImportOutcome.Blocked(ImportDecision.REJECT_MAGIC)
        } catch (_: TooLarge) {
            partial.delete()
            return ImportOutcome.Blocked(ImportDecision.REJECT_TOO_LARGE)
        } catch (_: IOException) {
            partial.delete()
            return ImportOutcome.Blocked(ImportDecision.REJECT_EMPTY)
        }
        if (written <= 0L || !GgufHeader.matches(partial.readHeader())) {
            partial.delete()
            return ImportOutcome.Blocked(if (written <= 0L) ImportDecision.REJECT_EMPTY else ImportDecision.REJECT_MAGIC)
        }
        if (!partial.renameTo(dest)) {
            partial.delete()
            return ImportOutcome.Blocked(ImportDecision.REJECT_EMPTY)
        }
        val name = displayName.trim().filter { !it.isISOControl() }.take(120).ifEmpty { "model.gguf" }
        val model = ImportedModel(id, name, written, System.currentTimeMillis())
        return ImportOutcome.Copied(model)
    }

    private fun File.readHeader(): ByteArray {
        return try {
            inputStream().use { it.readNBytes(4) }
        } catch (_: IOException) {
            ByteArray(0)
        }
    }

    private fun writeAtomic(dest: File, text: String) {
        val partial = File(dest.parentFile, dest.name + ".partial")
        partial.writeText(text)
        if (!partial.renameTo(dest)) {
            dest.writeText(text)
            partial.delete()
        }
    }

    private class BadMagic : Exception()
    private class TooLarge : Exception()
}

sealed class ImportOutcome {
    data class Copied(val model: ImportedModel) : ImportOutcome()
    data class Blocked(val decision: ImportDecision) : ImportOutcome()
}
