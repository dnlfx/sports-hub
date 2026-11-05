package com.sportshub.app.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.data.scan.HtmlMediaExtractor
import com.sportshub.app.model.Sport
import com.sportshub.app.model.StreamSource
import com.sportshub.app.ui.components.LargeHeaderBar
import com.sportshub.app.ui.components.SectionHeader
import com.sportshub.app.ui.components.SportChips
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.theme.SportsHubTheme
import kotlinx.coroutines.launch

/** Lets the user paste page URLs to watch from. Nothing is pre-filled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    sourcesRepository: SourcesRepository,
    defaultSport: Sport,
    onOpenWatch: () -> Unit,
) {
    val loadedSources by sourcesRepository.current.collectAsStateWithLifecycle()
    val sources = loadedSources.orEmpty()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }

    var urlInput by rememberSaveable { mutableStateOf("") }
    var labelInput by rememberSaveable { mutableStateOf("") }
    var sport by remember(defaultSport) { mutableStateOf(defaultSport) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val url = SourcesRepository.normalizeUrl(urlInput)
        if (url == null) {
            error = "Enter a valid web address, like https://example.com/page"
            return
        }
        sources.firstOrNull { it.url == url }?.let {
            error = "Already saved as \"${it.label}\""
            return
        }
        val label = labelInput.trim().ifEmpty { HtmlMediaExtractor.hostOf(url) }
        urlInput = ""
        labelInput = ""
        error = null
        focusManager.clearFocus()
        scope.launch {
            sourcesRepository.add(url, label, sport)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "Added $label",
                actionLabel = "Watch",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onOpenWatch()
        }
    }

    fun remove(source: StreamSource) {
        scope.launch {
            sourcesRepository.remove(source.id)
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = "Removed ${source.label}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) sourcesRepository.restore(source)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeHeaderBar(
                title = "Sources",
                subtitle = "Paste a page and the app finds its videos",
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(bottom = bottomListPadding()),
        ) {
            item(key = "form") {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = SportsHubTheme.colors.card),
                ) {
                    Column(Modifier.padding(vertical = 16.dp)) {
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
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                                    ?.getItemAt(0)?.text?.toString()
                                                    ?.let { urlInput = it.trim(); error = null }
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                                    }
                                },
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = labelInput,
                                onValueChange = { labelInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Name (optional)") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Show under",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        SportChips(selected = sport, onSelect = { sport = it })
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { submit() },
                            enabled = urlInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(48.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add source")
                        }
                    }
                }
            }

            item(key = "note") {
                Row(
                    Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Saved on this device only. Add pages you have the rights to watch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (sources.isNotEmpty()) {
                item(key = "saved_header") {
                    SectionHeader("Saved", count = sources.size, padding = PaddingValues(start = 20.dp, top = 24.dp, bottom = 8.dp))
                }
                items(sources, key = { it.id }) { source ->
                    SavedSourceRow(
                        source = source,
                        onRemove = { remove(source) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .animateItem(),
                    )
                }
            }
        }
    }
}

/** A saved source. Swipe it left, or tap the bin, to remove it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedSourceRow(source: StreamSource, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(MaterialTheme.shapes.large),
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onRemove() },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(SportsHubTheme.colors.card)
                .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    source.label.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(source.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${source.sport.shortName} · ${SourcesRepository.displayUrl(source.url)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove ${source.label}")
            }
        }
    }
}
