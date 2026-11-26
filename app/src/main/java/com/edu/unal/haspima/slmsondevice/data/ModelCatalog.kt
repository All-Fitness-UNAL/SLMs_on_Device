package com.edu.unal.haspima.slmsondevice.data

import androidx.annotation.StringRes
import com.edu.unal.haspima.slmsondevice.R
import java.util.Locale

/**
 * Descripción de un modelo descargable en formato .litertlm (LiteRT-LM).
 * Los tamaños se verificaron con `curl -sIL` contra Hugging Face (nov. 2026).
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val params: String,
    val quantization: String,
    val sizeBytes: Long,
    val url: String,
    val fileName: String,
    val license: String,
    /** true si Hugging Face exige iniciar sesión y aceptar la licencia (token). */
    val gated: Boolean = false,
    @StringRes val notesResId: Int? = null,
    val notes: String = "",
)

object ModelCatalog {

    private const val HF = "https://huggingface.co/litert-community"

    val models: List<ModelInfo> = listOf(
        ModelInfo(
            id = "qwen3-0.6b-int8",
            displayName = "Qwen3 0.6B",
            params = "0.6 B",
            quantization = "INT8 dinámico (pesos), KV float, ctx 4096",
            sizeBytes = 613_722_264,
            url = "$HF/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            fileName = "Qwen3-0.6B.litertlm",
            license = "Apache 2.0",
            notesResId = R.string.model_note_qwen3_06b_int8,
            notes = "Recomendado por defecto. Soporta modo razonamiento (thinking).",
        ),
        ModelInfo(
            id = "qwen3-0.6b-int4",
            displayName = "Qwen3 0.6B (INT4)",
            params = "0.6 B",
            quantization = "INT4 bloque-32 (pesos), activ. FP32, ctx 4096",
            sizeBytes = 343_490_560,
            url = "$HF/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            license = "Apache 2.0",
            notesResId = R.string.model_note_qwen3_06b_int4,
            notes = "Más pequeño; útil para comparar cuantización INT8 vs INT4. GPU requiere LiteRT-LM ≥ 0.19.",
        ),
        ModelInfo(
            id = "qwen2.5-1.5b-q8",
            displayName = "Qwen2.5 1.5B Instruct",
            params = "1.5 B",
            quantization = "INT8, ctx 4096",
            sizeBytes = 1_597_922_856,
            url = "$HF/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            license = "Apache 2.0",
            notesResId = R.string.model_note_qwen25_15b,
            notes = "Mejor calidad, más lento. Requiere ≥ 6 GB de RAM.",
        ),
        ModelInfo(
            id = "gemma3-1b-int4",
            displayName = "Gemma 3 1B IT",
            params = "1 B",
            quantization = "INT4, ctx 4096",
            sizeBytes = 584_417_280,
            url = "$HF/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
            fileName = "gemma3-1b-it-int4.litertlm",
            license = "Gemma Terms of Use",
            gated = true,
            notesResId = R.string.model_note_gemma3_1b,
            notes = "Repositorio restringido: acepte la licencia en Hugging Face y use un token, o importe el archivo.",
        ),
    )

    val default: ModelInfo get() = models.first()

    fun byFileName(name: String): ModelInfo? = models.firstOrNull { it.fileName == name }
}

/** Formatea bytes en MB/GB legibles (base 1000, como Hugging Face). */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.0f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format(Locale.US, "%.0f KB", bytes / 1e3)
    else -> "$bytes B"
}

/**
 * Nombre de archivo seguro para un modelo importado: sin rutas, solo caracteres
 * permitidos y siempre con extensión .litertlm.
 */
fun sanitizeModelFileName(raw: String?): String {
    val base = raw?.substringAfterLast('/')?.trim().orEmpty()
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trimStart('.')
    val name = base.ifEmpty { "modelo_importado" }
    return if (name.endsWith(".litertlm", ignoreCase = true)) name else "$name.litertlm"
}
