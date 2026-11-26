package com.edu.unal.haspima.slmsondevice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.edu.unal.haspima.slmsondevice.llm.GenerationMetrics
import java.util.Locale

/** Pantalla de chat con streaming token a token y métricas por respuesta. */
@Composable
fun ChatScreen(vm: ChatViewModel, state: UiState, onGoToModels: () -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val ready = state.model as? ModelState.Ready

    // Mantener visible el final mientras llega el streaming.
    val lastLen = state.messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(state.messages.size, lastLen) {
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Header(state, onNewChat = vm::newConversation)

        when (val m = state.model) {
            is ModelState.Loading -> Column(Modifier.padding(16.dp)) {
                Text("Cargando ${m.name}…")
                Text(
                    "Inicializando LiteRT-LM (pesos + KV-cache). Puede tardar unos segundos.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            is ModelState.Failed -> Banner(m.message, "Ver modelos", onGoToModels, error = true)
            ModelState.None -> Banner("No hay un modelo cargado.", "Ir a Modelos", onGoToModels)
            is ModelState.Ready -> Unit
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.messages.isEmpty() && ready != null) {
                item {
                    Text(
                        "Escriba un mensaje. Pruebe activar el modo avión: la respuesta se genera igual, " +
                                "porque el modelo corre en este teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(state.messages, key = { it.id }) { MessageBubble(it) }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (ready != null) "Mensaje…" else "Cargue un modelo primero") },
                enabled = ready != null,
                maxLines = 5,
            )
            if (state.generating) {
                FilledIconButton(
                    onClick = vm::stop,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Filled.Close, contentDescription = "Detener") }
            } else {
                FilledIconButton(
                    onClick = { vm.send(input); input = "" },
                    enabled = ready != null && input.isNotBlank(),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar") }
            }
        }
    }
}

@Composable
private fun Header(state: UiState, onNewChat: () -> Unit) {
    val ready = state.model as? ModelState.Ready
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    ready?.file?.nameWithoutExtension ?: "SLM on Device",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ready != null) {
                    Text(
                        "Backend ${ready.result.backend} · carga ${ready.result.loadMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OfflineChip(state.online)
            IconButton(onClick = onNewChat, enabled = ready != null) {
                Icon(Icons.Filled.Refresh, contentDescription = "Nueva conversación")
            }
        }
    }
}

/** Indicador de red: deja claro que la inferencia no depende de internet. */
@Composable
private fun OfflineChip(online: Boolean) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                if (online) "En línea · inferencia local" else "Sin red · 100 % local",
                style = MaterialTheme.typography.labelSmall,
            )
        },
        leadingIcon = {
            Box(
                Modifier
                    .padding(2.dp)
                    .background(
                        if (online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(50),
                    )
                    .padding(4.dp)
            )
        },
    )
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.sender == Sender.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (msg.thought.isNotBlank()) ThoughtSection(msg.thought)
                when {
                    msg.text.isEmpty() && msg.streaming -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                        Text("Pensando…", style = MaterialTheme.typography.bodySmall)
                    }
                    else -> SelectionContainer {
                        Text(
                            msg.text + if (msg.streaming) " ▌" else "",
                            color = if (msg.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (msg.stopped) {
                    Text("[detenido]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                msg.metrics?.let { MetricsLine(it) }
            }
        }
    }
}

@Composable
private fun ThoughtSection(thought: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        if (expanded) "▾ Razonamiento" else "▸ Razonamiento (${thought.length} caracteres)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.clickable { expanded = !expanded }.padding(bottom = 4.dp),
    )
    if (expanded) {
        Text(
            thought.trim(),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** Línea de métricas. "≈" indica conteo estimado (4 caracteres ≈ 1 token). */
@Composable
private fun MetricsLine(m: GenerationMetrics) {
    val approx = if (m.exact) "" else "≈"
    val parts = buildList {
        add("TTFT ${m.ttftMs} ms")
        add("$approx${String.format(Locale.US, "%.1f", m.decodeTokensPerSecond)} tok/s")
        add("$approx${m.decodeTokens} tokens")
        m.prefillTokens?.let { add("prompt $it tok") }
        add("total ${String.format(Locale.US, "%.1f", m.totalMs / 1000.0)} s")
    }
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
    if (!m.exact) {
        Text(
            "≈ estimado: ~4 caracteres por token (el runtime no devolvió conteos).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
