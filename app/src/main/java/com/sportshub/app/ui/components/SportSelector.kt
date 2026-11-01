package com.sportshub.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sportshub.app.model.Sport

/**
 * Compact sport switcher for a top bar: a chip naming the current sport that
 * opens a menu of every sport. Sports that aren't live yet are listed but greyed out.
 */
@Composable
fun SportMenuButton(selected: Sport, onSelect: (Sport) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.padding(end = 8.dp)) {
        AssistChip(
            onClick = { open = true },
            label = { Text(selected.shortName) },
            trailingIcon = {
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            border = null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Sport.entries.forEach { sport ->
                DropdownMenuItem(
                    text = { Text(sport.displayName) },
                    onClick = {
                        onSelect(sport)
                        open = false
                    },
                    enabled = sport.isAvailable,
                    trailingIcon = if (sport == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/** Sport picker for forms, as a row of chips. Sports that aren't live yet show as disabled chips. */
@Composable
fun SportChips(
    selected: Sport,
    onSelect: (Sport) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Sport.entries.forEach { sport ->
            FilterChip(
                selected = sport == selected,
                enabled = sport.isAvailable,
                onClick = { onSelect(sport) },
                label = { Text(sport.shortName) },
                leadingIcon = if (sport == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}
