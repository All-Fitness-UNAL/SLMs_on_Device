package com.edu.unal.haspima.slmsondevice.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edu.unal.haspima.slmsondevice.R
import com.edu.unal.haspima.slmsondevice.data.InferenceSettings
import com.edu.unal.haspima.slmsondevice.data.ModelInfo
import com.edu.unal.haspima.slmsondevice.data.ModelRepository
import com.edu.unal.haspima.slmsondevice.data.SettingsStore
import com.edu.unal.haspima.slmsondevice.data.TransferProgress
import com.edu.unal.haspima.slmsondevice.llm.GenerationEvent
import com.edu.unal.haspima.slmsondevice.llm.GenerationMetrics
import com.edu.unal.haspima.slmsondevice.llm.LlmEngine
import com.edu.unal.haspima.slmsondevice.llm.LoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Sender { USER, ASSISTANT }

data class ChatMessage(
    val id: Long,
    val sender: Sender,
    val text: String,
    val thought: String = "",
    val metrics: GenerationMetrics? = null,
    val streaming: Boolean = false,
    val error: Boolean = false,
    val stopped: Boolean = false,
)

sealed interface ModelState {
    data object None : ModelState
    data class Loading(val name: String) : ModelState
    data class Ready(val file: File, val result: LoadResult) : ModelState
    data class Failed(val message: String) : ModelState
}

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val model: ModelState = ModelState.None,
    val settings: InferenceSettings = InferenceSettings(),
    val localFiles: List<File> = emptyList(),
    val transfers: Map<String, TransferProgress> = emptyMap(),
    val online: Boolean = false,
    /** Mensaje corto para mostrar en un Snackbar. */
    val notice: String? = null,
)

/** Estado de toda la app: modelos, ajustes y chat. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val repository = ModelRepository(app)
    private val settingsStore = SettingsStore(app)
    private val llm = LlmEngine(app.cacheDir)

    private val _state = MutableStateFlow(UiState(settings = settingsStore.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var generationJob: Job? = null
    private var stopRequested = false
    private val transferJobs = mutableMapOf<String, Job>()
    private var nextId = 0L

    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            _state.update { it.copy(online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) }

        override fun onLost(network: Network) = _state.update { it.copy(online = false) }
    }

    init {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        refreshFiles()
        // Recarga automáticamente el último modelo usado.
        settingsStore.lastModelPath?.let(::File)?.takeIf { it.exists() }?.let(::loadModel)
    }

    // ------------------------------------------------------------------ Modelo

    fun loadModel(file: File) {
        if (_state.value.model is ModelState.Loading) return
        stop()
        _state.update { it.copy(model = ModelState.Loading(file.name)) }
        viewModelScope.launch {
            generationJob?.join() // no liberar el modelo mientras genera
            try {
                val result = llm.load(file, _state.value.settings)
                settingsStore.lastModelPath = file.absolutePath
                _state.update {
                    it.copy(
                        model = ModelState.Ready(file, result),
                        messages = emptyList(),
                        notice = result.warning,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                llm.close()
                fail(getApplication<Application>().getString(R.string.notice_oom))
            } catch (t: Throwable) {
                llm.close()
                val errDetails = t.message ?: t.javaClass.simpleName
                fail(getApplication<Application>().getString(R.string.notice_load_error, file.name, errDetails))
            }
        }
    }

    private fun fail(msg: String) = _state.update { it.copy(model = ModelState.Failed(msg), notice = msg) }

    fun unloadModel() {
        stop()
        llm.close()
        _state.update { it.copy(model = ModelState.None) }
    }

    fun refreshFiles() = _state.update { it.copy(localFiles = repository.localModels()) }

    fun download(model: ModelInfo) {
        if (transferJobs[model.id]?.isActive == true) return
        transferJobs[model.id] = viewModelScope.launch {
            try {
                repository.download(model, _state.value.settings.hfToken).collect { p ->
                    _state.update { it.copy(transfers = it.transfers + (model.id to p)) }
                }
                _state.update { it.copy(notice = getApplication<Application>().getString(R.string.notice_downloaded, model.displayName)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(notice = getApplication<Application>().getString(R.string.notice_download_paused)) }
            } catch (t: Throwable) {
                _state.update { it.copy(notice = t.message ?: getApplication<Application>().getString(R.string.notice_download_error)) }
            } finally {
                _state.update { it.copy(transfers = it.transfers - model.id) }
                refreshFiles()
            }
        }
    }

    fun cancelDownload(model: ModelInfo) = transferJobs.remove(model.id)?.cancel()

    fun importModel(uri: Uri) {
        val key = "import"
        if (transferJobs[key]?.isActive == true) return
        transferJobs[key] = viewModelScope.launch {
            try {
                repository.import(uri).collect { p -> _state.update { it.copy(transfers = it.transfers + (key to p)) } }
                _state.update { it.copy(notice = getApplication<Application>().getString(R.string.notice_imported)) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(notice = getApplication<Application>().getString(R.string.notice_import_failed, t.message ?: "")) }
            } finally {
                _state.update { it.copy(transfers = it.transfers - key) }
                refreshFiles()
            }
        }
    }

    fun deleteFile(file: File) {
        val ready = _state.value.model as? ModelState.Ready
        if (ready?.file == file) unloadModel()
        repository.delete(file)
        refreshFiles()
    }

    // ------------------------------------------------------------------ Ajustes

    fun updateSettings(newSettings: InferenceSettings) {
        val old = _state.value.settings
        settingsStore.save(newSettings)
        _state.update { it.copy(settings = newSettings) }
        val ready = _state.value.model as? ModelState.Ready ?: return
        when {
            // El backend se fija al crear el Engine: hay que recargar.
            old.backend != newSettings.backend -> loadModel(ready.file)
            // Muestreo / prompt de sistema se fijan al crear la Conversation.
            old.copy(hfToken = "") != newSettings.copy(hfToken = "") -> newConversation()
        }
    }

    // ------------------------------------------------------------------ Chat

    fun send(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || _state.value.generating || !llm.isLoaded) return
        val replyId = ++nextId
        _state.update {
            it.copy(
                generating = true,
                messages = it.messages +
                        ChatMessage(++nextId, Sender.USER, text) +
                        ChatMessage(replyId, Sender.ASSISTANT, "", streaming = true),
            )
        }
        stopRequested = false
        generationJob = viewModelScope.launch {
            try {
                llm.generate(text).collect { event ->
                    when (event) {
                        is GenerationEvent.Chunk -> editMessage(replyId) {
                            it.copy(text = it.text + event.text, thought = it.thought + event.thought.orEmpty())
                        }
                        is GenerationEvent.Done -> editMessage(replyId) {
                            it.copy(metrics = event.metrics, stopped = event.stopped)
                        }
                    }
                }
            } catch (e: CancellationException) {
                editMessage(replyId) { it.copy(stopped = true) }
            } catch (t: Throwable) {
                if (stopRequested) {
                    editMessage(replyId) { it.copy(stopped = true) }
                } else {
                    editMessage(replyId) { it.copy(text = it.text + "\n[Error: ${t.message}]", error = true) }
                }
            } finally {
                editMessage(replyId) { it.copy(streaming = false) }
                _state.update { it.copy(generating = false) }
            }
        }
    }

    /** Detiene la generación: primero el runtime nativo; si no responde, cancela la corrutina. */
    fun stop() {
        val job = generationJob ?: return
        stopRequested = true
        llm.stop()
        viewModelScope.launch {
            delay(2_000)
            if (job.isActive) job.cancel()
        }
    }

    /** Borra el chat y crea una Conversation nueva (vacía el contexto del modelo). */
    fun newConversation() {
        stop()
        viewModelScope.launch {
            generationJob?.join()
            runCatching { llm.resetConversation(_state.value.settings) }
                .onFailure { e -> _state.update { it.copy(notice = getApplication<Application>().getString(R.string.notice_generic_error, e.message ?: "")) } }
            _state.update { it.copy(messages = emptyList()) }
        }
    }

    fun noticeShown() = _state.update { it.copy(notice = null) }

    private fun editMessage(id: Long, change: (ChatMessage) -> ChatMessage) = _state.update { s ->
        s.copy(messages = s.messages.map { if (it.id == id) change(it) else it })
    }

    override fun onCleared() {
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        llm.close() // detiene la generación y libera la memoria nativa
    }
}
