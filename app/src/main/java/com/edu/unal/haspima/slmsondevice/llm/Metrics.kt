package com.edu.unal.haspima.slmsondevice.llm

import kotlin.math.ceil

/**
 * Métricas de una respuesta.
 * Si [exact] es true, los conteos de tokens vienen del runtime (BenchmarkInfo de LiteRT-LM);
 * si es false son estimados (≈ 4 caracteres por token).
 */
data class GenerationMetrics(
    val ttftMs: Long,
    val totalMs: Long,
    val decodeTokens: Int,
    val decodeTokensPerSecond: Double,
    val prefillTokens: Int? = null,
    val prefillTokensPerSecond: Double? = null,
    val exact: Boolean,
)

object Metrics {

    /** Heurística típica para tokenizadores BPE: ~4 caracteres por token. */
    const val CHARS_PER_TOKEN = 4.0

    fun estimateTokens(text: String): Int =
        if (text.isEmpty()) 0 else ceil(text.length / CHARS_PER_TOKEN).toInt()

    /**
     * Velocidad de decodificación en tokens/s.
     * El primer token sale de la fase de prefill, así que se mide desde el primer token
     * hasta el final y se cuentan los (tokens - 1) restantes.
     */
    fun decodeTokensPerSecond(tokens: Int, firstTokenNanos: Long, endNanos: Long): Double {
        val seconds = (endNanos - firstTokenNanos) / 1e9
        if (tokens <= 1 || seconds <= 0.0) return 0.0
        return (tokens - 1) / seconds
    }

    fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000
}
