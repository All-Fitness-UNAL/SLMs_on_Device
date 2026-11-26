package com.edu.unal.haspima.slmsondevice.data

import android.content.Context
import androidx.core.content.edit

enum class BackendChoice { CPU, GPU }

/** Parámetros de inferencia configurables desde la pantalla de Ajustes. */
data class InferenceSettings(
    val backend: BackendChoice = BackendChoice.CPU,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 512,
    val systemPrompt: String = "Eres un asistente útil que responde en español de forma breve y clara.",
    /** Modo razonamiento (Qwen3): el modelo "piensa" antes de responder. */
    val thinking: Boolean = false,
    val hfToken: String = "",
)

/** Persistencia simple con SharedPreferences (sin dependencias extra). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): InferenceSettings {
        val d = InferenceSettings()
        return InferenceSettings(
            backend = runCatching { BackendChoice.valueOf(prefs.getString("backend", d.backend.name)!!) }
                .getOrDefault(d.backend),
            temperature = prefs.getFloat("temperature", d.temperature),
            topK = prefs.getInt("topK", d.topK),
            topP = prefs.getFloat("topP", d.topP),
            maxOutputTokens = prefs.getInt("maxOutputTokens", d.maxOutputTokens),
            systemPrompt = prefs.getString("systemPrompt", d.systemPrompt) ?: d.systemPrompt,
            thinking = prefs.getBoolean("thinking", d.thinking),
            hfToken = prefs.getString("hfToken", "") ?: "",
        )
    }

    fun save(s: InferenceSettings) = prefs.edit {
        putString("backend", s.backend.name)
        putFloat("temperature", s.temperature)
        putInt("topK", s.topK)
        putFloat("topP", s.topP)
        putInt("maxOutputTokens", s.maxOutputTokens)
        putString("systemPrompt", s.systemPrompt)
        putBoolean("thinking", s.thinking)
        putString("hfToken", s.hfToken)
    }

    var lastModelPath: String?
        get() = prefs.getString("lastModelPath", null)
        set(v) = prefs.edit { putString("lastModelPath", v) }
}
