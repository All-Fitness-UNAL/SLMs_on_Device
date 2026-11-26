package com.edu.unal.haspima.slmsondevice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.edu.unal.haspima.slmsondevice.data.BackendChoice
import com.edu.unal.haspima.slmsondevice.data.InferenceSettings
import java.util.Locale
import kotlin.math.roundToInt

/** Ajustes de inferencia organizados en tarjetas. Se aplican al guardar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    current: InferenceSettings,
    onSave: (InferenceSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
) {
    var draft by remember(current) { mutableStateOf(current) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        TopAppBar(
            title = {
                Text("Ajustes de Inferencia", fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al chat")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sección 1: Backend de ejecución
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hardware y Backend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        BackendChoice.entries.forEachIndexed { i, b ->
                            SegmentedButton(
                                selected = draft.backend == b,
                                onClick = { draft = draft.copy(backend = b) },
                                shape = SegmentedButtonDefaults.itemShape(i, BackendChoice.entries.size),
                            ) { Text(b.name) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Hint("GPU usa OpenCL si el procesador lo soporta; si falla, la app vuelve a CPU automáticamente. Cambiar el backend recargará el modelo.")
                }
            }

            // Sección 2: Parámetros de muestreo (sampling)
            Card(
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Parámetros de Muestreo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    LabeledSlider(
                        label = "Temperatura",
                        valueText = String.format(Locale.US, "%.2f", draft.temperature),
                        value = draft.temperature,
                        range = 0f..2f,
                        steps = 39,
                    ) { draft = draft.copy(temperature = it) }

                    LabeledSlider(
                        label = "Top-K",
                        valueText = draft.topK.toString(),
                        value = draft.topK.toFloat(),
                        range = 1f..100f,
                        steps = 98,
                    ) { draft = draft.copy(topK = it.roundToInt()) }

                    LabeledSlider(
                        label = "Top-P",
                        valueText = String.format(Locale.US, "%.2f", draft.topP),
                        value = draft.topP,
                        range = 0.05f..1f,
                        steps = 18,
                    ) { draft = draft.copy(topP = it) }

                    LabeledSlider(
                        label = "Máx. tokens de salida",
                        valueText = draft.maxOutputTokens.toString(),
                        value = draft.maxOutputTokens.toFloat(),
                        range = 64f..2048f,
                        steps = 30,
                    ) { draft = draft.copy(maxOutputTokens = (it / 64).roundToInt() * 64) }
                }
            }

            // Sección 3: Comportamiento y Prompt
            Card(
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Comportamiento del Modelo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Modo Razonamiento (Qwen3)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Hint("El modelo piensa antes de responder: mejor calidad, más tokens generados.")
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
                }
            }

            // Sección 4: Hugging Face Token
            Card(
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Autenticación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.hfToken,
                        onValueChange = { draft = draft.copy(hfToken = it.trim()) },
                        label = { Text("Token de Hugging Face (opcional)") },
                        supportingText = { Text("Requerido para modelos restringidos como Gemma 3. Se guarda localmente.") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Acciones inferiores
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { onSave(draft) },
                        enabled = draft != current,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Guardar y aplicar", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { draft = InferenceSettings(hfToken = current.hfToken) }) {
                        Text("Restablecer")
                    }
                }
            }

            Hint("Al guardar se iniciará una conversación nueva con los parámetros elegidos.")
        }
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
            Text(valueText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
