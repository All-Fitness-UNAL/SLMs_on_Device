package com.edu.unal.haspima.slmsondevice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.edu.unal.haspima.slmsondevice.data.BackendChoice
import com.edu.unal.haspima.slmsondevice.data.InferenceSettings
import java.util.Locale
import kotlin.math.roundToInt

/** Ajustes de inferencia. Se editan en un borrador y se aplican con "Guardar". */
@Composable
fun SettingsScreen(current: InferenceSettings, onSave: (InferenceSettings) -> Unit, modifier: Modifier = Modifier) {
    var draft by remember(current) { mutableStateOf(current) }

    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)

        Text("Backend de ejecución", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            BackendChoice.entries.forEachIndexed { i, b ->
                SegmentedButton(
                    selected = draft.backend == b,
                    onClick = { draft = draft.copy(backend = b) },
                    shape = SegmentedButtonDefaults.itemShape(i, BackendChoice.entries.size),
                ) { Text(b.name) }
            }
        }
        Hint("GPU usa OpenCL si el teléfono lo soporta; si falla, la app vuelve a CPU automáticamente. Cambiar el backend recarga el modelo.")

        LabeledSlider("Temperatura", String.format(Locale.US, "%.2f", draft.temperature),
            draft.temperature, 0f..2f, steps = 39) { draft = draft.copy(temperature = it) }
        LabeledSlider("Top-K", draft.topK.toString(),
            draft.topK.toFloat(), 1f..100f, steps = 98) { draft = draft.copy(topK = it.roundToInt()) }
        LabeledSlider("Top-P", String.format(Locale.US, "%.2f", draft.topP),
            draft.topP, 0.05f..1f, steps = 18) { draft = draft.copy(topP = it) }
        LabeledSlider("Máx. tokens de salida", draft.maxOutputTokens.toString(),
            draft.maxOutputTokens.toFloat(), 64f..2048f, steps = 30) {
            draft = draft.copy(maxOutputTokens = (it / 64).roundToInt() * 64)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Modo razonamiento (Qwen3)", style = MaterialTheme.typography.titleSmall)
                Hint("El modelo piensa antes de responder: mejor calidad, más tokens y más latencia.")
            }
            Switch(checked = draft.thinking, onCheckedChange = { draft = draft.copy(thinking = it) })
        }

        OutlinedTextField(
            value = draft.systemPrompt,
            onValueChange = { draft = draft.copy(systemPrompt = it) },
            label = { Text("Prompt de sistema") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        OutlinedTextField(
            value = draft.hfToken,
            onValueChange = { draft = draft.copy(hfToken = it.trim()) },
            label = { Text("Token de Hugging Face (opcional)") },
            supportingText = { Text("Solo para modelos restringidos como Gemma 3. Se guarda en el dispositivo.") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(draft) }, enabled = draft != current) { Text("Guardar") }
            TextButton(onClick = { draft = InferenceSettings(hfToken = current.hfToken) }) { Text("Valores por defecto") }
        }
        Hint("Al guardar se inicia una conversación nueva con los parámetros elegidos.")
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
