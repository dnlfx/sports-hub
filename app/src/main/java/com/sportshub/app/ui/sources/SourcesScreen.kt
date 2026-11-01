package com.sportshub.app.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.model.Sport
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.ScreenHeader
import com.sportshub.app.ui.components.SportSelector
import kotlinx.coroutines.launch

/**
 * Lets the user paste page URLs to watch from. Nothing is pre-filled.
 * Step 3 adds scanning each page for standard video and playing it.
 */
@Composable
fun SourcesScreen(sourcesRepository: SourcesRepository, defaultSport: Sport) {
    val sources by sourcesRepository.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var urlInput by rememberSaveable { mutableStateOf("") }
    var labelInput by rememberSaveable { mutableStateOf("") }
    var sport by remember { mutableStateOf(defaultSport) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val url = SourcesRepository.normalizeUrl(urlInput)
        if (url == null) {
            error = "Enter a valid web address, like https://example.com/page"
            return
        }
        val label = labelInput.trim().ifEmpty { java.net.URI(url).host.removePrefix("www.") }
        scope.launch { sourcesRepository.add(url, label, sport) }
        urlInput = ""
        labelInput = ""
        error = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenHeader("Sources", "Paste a page and the app will find its videos")
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Page URL") },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { urlInput = it; error = null } }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Spacer(Modifier.height(12.dp))
                Text("Show under", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            SportSelector(selected = sport, onSelect = { sport = it })
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { submit() },
                enabled = urlInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text("Add source") }
            Spacer(Modifier.height(24.dp))
        }

        if (sources.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Link,
                    title = "No sources added",
                    message = "Sources you add are saved on this device only. Add pages you have the rights to watch.",
                )
            }
        } else {
            item {
                Text(
                    "Saved",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            items(sources, key = { it.id }) { source ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(source.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${source.sport.shortName} · ${source.url}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { scope.launch { sourcesRepository.remove(source.id) } }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${source.label}")
                        }
                    }
                }
            }
        }
    }
}
