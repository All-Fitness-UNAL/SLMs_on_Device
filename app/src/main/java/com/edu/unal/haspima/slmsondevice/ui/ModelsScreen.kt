package com.edu.unal.haspima.slmsondevice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.unal.haspima.slmsondevice.data.ModelCatalog
import com.edu.unal.haspima.slmsondevice.data.ModelInfo
import com.edu.unal.haspima.slmsondevice.data.TransferProgress
import com.edu.unal.haspima.slmsondevice.data.formatBytes
import java.io.File

/** Catálogo de modelos: descargar, importar, cargar y borrar. */
@Composable
fun ModelsScreen(vm: ChatViewModel, state: UiState, modifier: Modifier = Modifier) {
    var toDelete by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importModel)
    }
    val activeFile = (state.model as? ModelState.Ready)?.file
    val loadingName = (state.model as? ModelState.Loading)?.name
    val catalogNames = ModelCatalog.models.map { it.fileName }.toSet()
    val others = state.localFiles.filter { it.name !in catalogNames }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Modelos", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Los modelos se ejecutan 100 % en el dispositivo con LiteRT-LM. " +
                        "Solo se necesita internet para descargarlos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Importar desde archivo", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Elija un .litertlm (p. ej. Gemma 3 descargado con su cuenta de Hugging Face). " +
                                "También puede copiarlo con adb a:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(
                            vm.repository.modelsDir.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.padding(4.dp))
                    val importing = state.transfers["import"]
                    if (importing != null) {
                        ProgressLine("Importando…", importing)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Importar modelo (.litertlm)") }
                            TextButton(onClick = vm::refreshFiles) { Text("Actualizar") }
                        }
                    }
                }
            }
        }
        items(ModelCatalog.models, key = { it.id }) { model ->
            val file = vm.repository.fileFor(model)
            CatalogCard(
                model = model,
                downloaded = state.localFiles.any { it.name == model.fileName },
                partialBytes = vm.repository.partialBytes(model),
                progress = state.transfers[model.id],
                isActive = activeFile == file,
                isLoading = loadingName == model.fileName,
                hasToken = state.settings.hfToken.isNotBlank(),
                onDownload = { vm.download(model) },
                onCancel = { vm.cancelDownload(model) },
                onLoad = { vm.loadModel(file) },
                onDelete = { toDelete = file },
            )
        }
        if (others.isNotEmpty()) {
            item { Text("Otros modelos en el dispositivo", style = MaterialTheme.typography.titleMedium) }
            items(others, key = { it.absolutePath }) { file ->
                Card {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.name, fontWeight = FontWeight.SemiBold)
                            Text(formatBytes(file.length()), style = MaterialTheme.typography.bodySmall)
                            if (activeFile == file) ActiveLabel()
                        }
                        TextButton(onClick = { vm.loadModel(file) }, enabled = loadingName == null) { Text("Cargar") }
                        TextButton(onClick = { toDelete = file }) { Text("Borrar") }
                    }
                }
            }
        }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar modelo?") },
            text = { Text("${file.name} (${formatBytes(file.length())}) se eliminará del dispositivo.") },
            confirmButton = { TextButton(onClick = { vm.deleteFile(file); toDelete = null }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CatalogCard(
    model: ModelInfo,
    downloaded: Boolean,
    partialBytes: Long,
    progress: TransferProgress?,
    isActive: Boolean,
    isLoading: Boolean,
    hasToken: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatBytes(model.sizeBytes), style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "${model.params} parámetros · ${model.quantization}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Licencia: ${model.license}" + if (model.gated) " · requiere cuenta HF" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (model.notes.isNotEmpty()) {
                Text(model.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isActive) ActiveLabel()
            Spacer(Modifier.padding(4.dp))
            when {
                progress != null -> {
                    ProgressLine("Descargando…", progress)
                    TextButton(onClick = onCancel) { Text("Pausar") }
                }
                downloaded -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLoad, enabled = !isLoading && !isActive) {
                        Text(if (isLoading) "Cargando…" else if (isActive) "En uso" else "Cargar")
                    }
                    OutlinedButton(onClick = onDelete) { Text("Borrar") }
                }
                else -> {
                    val resume = partialBytes > 0
                    Button(onClick = onDownload, enabled = !model.gated || hasToken) {
                        Text(
                            if (resume) "Reanudar (${(100 * partialBytes / model.sizeBytes)} %)"
                            else "Descargar"
                        )
                    }
                    if (model.gated && !hasToken) {
                        Text(
                            "Configure un token de Hugging Face en Ajustes o use \"Importar\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveLabel() = Text(
    "● Cargado en memoria",
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.primary,
)

@Composable
private fun ProgressLine(label: String, p: TransferProgress) {
    Column {
        if (p.total > 0) {
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            Text(
                formatBytes(p.bytes) + if (p.total > 0) " / ${formatBytes(p.total)}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
