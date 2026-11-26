package com.edu.unal.haspima.slmsondevice.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.unal.haspima.slmsondevice.R
import com.edu.unal.haspima.slmsondevice.data.ModelCatalog
import com.edu.unal.haspima.slmsondevice.data.ModelInfo
import com.edu.unal.haspima.slmsondevice.data.TransferProgress
import com.edu.unal.haspima.slmsondevice.data.formatBytes
import java.io.File

/** Catálogo de modelos: descargar, importar, cargar y borrar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    vm: ChatViewModel,
    state: UiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(),
) {
    var toDelete by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importModel)
    }
    val activeFile = (state.model as? ModelState.Ready)?.file
    val loadingName = (state.model as? ModelState.Loading)?.name
    val catalogNames = ModelCatalog.models.map { it.fileName }.toSet()
    val others = state.localFiles.filter { it.name !in catalogNames }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        TopAppBar(
            title = {
                Text(stringResource(R.string.models_title), fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_chat))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.models_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.models_import_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.models_import_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = vm.repository.modelsDir.absolutePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val importing = state.transfers["import"]
                        if (importing != null) {
                            ProgressLine(stringResource(R.string.models_importing), importing)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.models_btn_open_file)) }
                                TextButton(onClick = vm::refreshFiles) { Text(stringResource(R.string.models_btn_refresh)) }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.models_featured_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
                item {
                    Text(
                        text = stringResource(R.string.models_others_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(others, key = { it.absolutePath }) { file ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                Text(formatBytes(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (activeFile == file) ActiveLabel()
                            }
                            TextButton(onClick = { vm.loadModel(file) }, enabled = loadingName == null) { Text(stringResource(R.string.models_btn_load)) }
                            TextButton(onClick = { toDelete = file }) { Text(stringResource(R.string.models_btn_delete)) }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.models_delete_dialog_title)) },
            text = { Text(stringResource(R.string.models_delete_dialog_text, file.name, formatBytes(file.length()))) },
            confirmButton = { TextButton(onClick = { vm.deleteFile(file); toDelete = null }) { Text(stringResource(R.string.models_btn_delete)) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
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
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        formatBytes(model.sizeBytes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.models_card_params, model.params, model.quantization),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.models_card_license, model.license) + if (model.gated) stringResource(R.string.models_gated_tag) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val notesText = model.notesResId?.let { stringResource(it) } ?: model.notes
            if (notesText.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(notesText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (isActive) ActiveLabel()
            Spacer(Modifier.height(12.dp))
            when {
                progress != null -> {
                    ProgressLine(stringResource(R.string.models_downloading), progress)
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.models_btn_pause)) }
                }
                downloaded -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLoad, enabled = !isLoading && !isActive) {
                        Text(
                            if (isLoading) stringResource(R.string.models_btn_loading)
                            else if (isActive) stringResource(R.string.models_btn_in_use)
                            else stringResource(R.string.models_btn_load)
                        )
                    }
                    OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.models_btn_delete)) }
                }
                else -> {
                    val resume = partialBytes > 0
                    Button(onClick = onDownload, enabled = !model.gated || hasToken) {
                        Text(
                            if (resume) stringResource(R.string.models_btn_resume, (100 * partialBytes / model.sizeBytes).toInt())
                            else stringResource(R.string.models_btn_download)
                        )
                    }
                    if (model.gated && !hasToken) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.models_gated_warning),
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
private fun ActiveLabel() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            stringResource(R.string.models_active_label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ProgressLine(label: String, p: TransferProgress) {
    Column {
        if (p.total > 0) {
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            Text(
                formatBytes(p.bytes) + if (p.total > 0) " / ${formatBytes(p.total)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
