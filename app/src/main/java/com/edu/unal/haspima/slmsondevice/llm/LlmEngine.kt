package com.edu.unal.haspima.slmsondevice.llm

import com.edu.unal.haspima.slmsondevice.data.BackendChoice
import com.edu.unal.haspima.slmsondevice.data.InferenceSettings
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.File

/** Resultado de cargar un modelo. */
data class LoadResult(val loadMs: Long, val backend: BackendChoice, val warning: String? = null)

/** Eventos del streaming de una respuesta. */
sealed interface GenerationEvent {
    /** Fragmento nuevo (delta) del texto; [thought] = razonamiento si el modelo lo emite. */
    data class Chunk(val text: String, val thought: String?) : GenerationEvent
    data class Done(val metrics: GenerationMetrics, val stopped: Boolean) : GenerationEvent
}

/**
 * Envoltura delgada sobre LiteRT-LM: cargar / descargar el modelo y generar como Flow.
 * Todo el trabajo pesado corre fuera del hilo principal (Dispatchers.Default).
 */
class LlmEngine(private val cacheDir: File) : AutoCloseable {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    @Volatile private var stopRequested = false

    val isLoaded: Boolean get() = conversation != null

    /** Carga el modelo (bloqueante en C++, por eso withContext). Si la GPU falla, reintenta en CPU. */
    @OptIn(ExperimentalApi::class)
    suspend fun load(modelFile: File, settings: InferenceSettings): LoadResult =
        withContext(Dispatchers.Default) {
            close()
            ExperimentalFlags.enableBenchmark = true // habilita conteos exactos de tokens
            val start = System.nanoTime()
            var backend = settings.backend
            var warning: String? = null
            val newEngine = try {
                createEngine(modelFile, backend)
            } catch (e: Exception) {
                if (backend == BackendChoice.CPU) throw e
                warning = "GPU no disponible (${e.message?.take(80)}). Se usó CPU."
                backend = BackendChoice.CPU
                createEngine(modelFile, backend)
            }
            engine = newEngine
            conversation = newEngine.createConversation(conversationConfig(settings))
            LoadResult(Metrics.nanosToMillis(System.nanoTime() - start), backend, warning)
        }

    private fun createEngine(modelFile: File, backend: BackendChoice): Engine {
        val config = EngineConfig(
            modelPath = modelFile.absolutePath,
            backend = if (backend == BackendChoice.GPU) Backend.GPU() else Backend.CPU(),
            cacheDir = cacheDir.absolutePath, // acelera la segunda carga
        )
        val e = Engine(config)
        try {
            e.initialize()
        } catch (t: Throwable) {
            runCatching { e.close() }
            throw t
        }
        return e
    }

    private fun conversationConfig(s: InferenceSettings) = ConversationConfig(
        systemInstruction = s.systemPrompt.takeIf { it.isNotBlank() }?.let { Contents.of(it) },
        samplerConfig = SamplerConfig(
            topK = s.topK, topP = s.topP.toDouble(), temperature = s.temperature.toDouble()
        ),
        maxOutputToken = s.maxOutputTokens,
        thinkingConfig = ThinkingConfig(enableThinking = s.thinking),
        extraContext = mapOf("enable_thinking" to s.thinking),
    )

    /** Nueva conversación (borra el historial / KV-cache) con los ajustes actuales. */
    suspend fun resetConversation(settings: InferenceSettings) = withContext(Dispatchers.Default) {
        val e = engine ?: return@withContext
        conversation?.close()
        conversation = e.createConversation(conversationConfig(settings))
    }

    /** Genera la respuesta token a token y al final emite las métricas. */
    fun generate(prompt: String): Flow<GenerationEvent> = flow {
        val conv = conversation ?: error("No hay un modelo cargado")
        stopRequested = false
        val start = System.nanoTime()
        var firstToken = 0L
        val text = StringBuilder()
        try {
            conv.sendMessageAsync(prompt).collect { message ->
                if (firstToken == 0L) firstToken = System.nanoTime()
                val piece = message.toString()
                val thought = message.channels["thought"]
                text.append(piece).append(thought.orEmpty())
                emit(GenerationEvent.Chunk(piece, thought))
            }
        } catch (e: Exception) {
            // Al detener, el runtime termina el stream con error "Process cancelled".
            if (e is CancellationException || !stopRequested) throw e
        }
        val metrics = buildMetrics(conv, text.toString(), start, firstToken, System.nanoTime())
        emit(GenerationEvent.Done(metrics, stopped = stopRequested))
    }.onCompletion { cause ->
        // Si el colector se cancela, detenemos también el trabajo nativo.
        if (cause is CancellationException) stop()
    }.flowOn(Dispatchers.Default)

    /** Pide al runtime detener la generación en curso (el Flow termina con error "cancelled"). */
    fun stop() {
        stopRequested = true
        runCatching { conversation?.cancelProcess() }
    }

    @OptIn(ExperimentalApi::class)
    private fun buildMetrics(conv: Conversation, text: String, start: Long, first: Long, end: Long): GenerationMetrics {
        val firstOrEnd = if (first == 0L) end else first
        val ttft = Metrics.nanosToMillis(firstOrEnd - start)
        val total = Metrics.nanosToMillis(end - start)
        // Preferimos los conteos exactos del runtime; si no están disponibles, estimamos.
        val bench = runCatching { conv.getBenchmarkInfo() }.getOrNull()
        return if (bench != null && bench.lastDecodeTokenCount > 0) {
            GenerationMetrics(
                ttftMs = ttft, totalMs = total,
                decodeTokens = bench.lastDecodeTokenCount,
                decodeTokensPerSecond = bench.lastDecodeTokensPerSecond,
                prefillTokens = bench.lastPrefillTokenCount,
                prefillTokensPerSecond = bench.lastPrefillTokensPerSecond,
                exact = true,
            )
        } else {
            val tokens = Metrics.estimateTokens(text)
            GenerationMetrics(
                ttftMs = ttft, totalMs = total, decodeTokens = tokens,
                decodeTokensPerSecond = Metrics.decodeTokensPerSecond(tokens, firstOrEnd, end),
                exact = false,
            )
        }
    }

    /** Libera memoria nativa (modelo + KV-cache). */
    override fun close() {
        stop()
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
    }
}
