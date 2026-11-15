package com.sportshub.app.ui.coverage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.tv.LocalListings
import com.sportshub.app.data.tv.TvMarket
import com.sportshub.app.data.tv.TvMarketRepository
import com.sportshub.app.data.tv.TvMarkets
import kotlinx.coroutines.launch

/**
 * Picks the user's TV market: from the phone's approximate location, or by searching
 * the list. Picking saves straight away and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketPickerSheet(
    repository: TvMarketRepository,
    current: TvMarket?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var locateFailed by remember { mutableStateOf<String?>(null) }
    val results = remember(query) { TvMarkets.search(query) }
    val savedZip by repository.zip.collectAsStateWithLifecycle()
    var zip by rememberSaveable { mutableStateOf(savedZip.orEmpty()) }

    fun pick(market: TvMarket) {
        repository.set(market)
        onDismiss()
    }

    fun locate() {
        locating = true
        locateFailed = null
        scope.launch {
            val found = repository.locate()
            locating = false
            if (found != null) pick(found) else locateFailed = "Couldn't get your location. Pick your market from the list."
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else locateFailed = "Without location access, pick your market from the list."
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Your TV market", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Decides which games you get for free with an antenna. Saved on this phone only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = { if (repository.hasLocationPermission()) locate() else permission.launch(TvMarketRepository.LOCATION_PERMISSION) },
                enabled = !locating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (locating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (locating) "Finding your market…" else "Use my location")
            }
            locateFailed?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            // The ZIP code gets the user's real channels from the TV guide. It saves as soon as
            // it's five digits, and clearing the field clears it.
            OutlinedTextField(
                value = zip,
                onValueChange = { typed ->
                    zip = typed.filter { it.isDigit() }.take(5)
                    if (LocalListings.isZip(zip)) repository.setZip(zip) else if (zip.isEmpty()) repository.setZip(null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.SettingsInputAntenna, contentDescription = null) },
                label = { Text("ZIP code") },
                supportingText = {
                    Text(
                        if (savedZip != null) "Showing your exact antenna channels from the TV guide." else "For your exact antenna channels from the TV guide.",
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Search by city or state") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            if (current != null) {
                TextButton(onClick = { repository.set(null); onDismiss() }) { Text("Clear my market") }
            }
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            items(results, key = { it.id }) { market ->
                val selected = market.id == current?.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Choose") { pick(market) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(market.name, style = MaterialTheme.typography.bodyLarge)
                        Text(market.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
