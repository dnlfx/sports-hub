package com.sportshub.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportshub.app.ui.theme.DisplayFamily
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme

/**
 * Space taken at the bottom of the screen by the floating navigation bar (and the system
 * navigation bar under it). Scrolling lists add it to their bottom padding so their last
 * item can scroll clear of the bar.
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Bottom padding for a scrolling list: [extra] plus room for the floating navigation bar. */
@Composable
fun bottomListPadding(extra: Dp = 24.dp): Dp = LocalBottomBarSpace.current + extra

/** A card: the card color with a hairline border, clipped to [shape]. */
@Composable
fun Modifier.cardSurface(
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = SportsHubTheme.colors.card,
): Modifier = this
    .clip(shape)
    .background(color)
    .border(1.dp, SportsHubTheme.colors.stroke, shape)

/**
 * Shape for one row of a list drawn as a single rounded card: the first row gets the
 * top corners, the last row the bottom ones.
 */
fun groupedShape(index: Int, count: Int, radius: Dp = 24.dp): Shape {
    val top = if (index == 0) radius else 4.dp
    val bottom = if (index == count - 1) radius else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A row of pill tabs that scrolls sideways when they don't fit. The selected pill is
 * filled with the text color, which reads clearly in light and dark mode.
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected > 0) listState.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(labels, key = { i, label -> "$i$label" }) { index, label ->
            Pill(label, selected = index == selected, onClick = { onSelect(index) })
        }
    }
}

@Composable
fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = SportsHubTheme.colors
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else colors.card,
        tween(180),
        label = "pill",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        tween(180),
        label = "pillText",
    )
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (selected) Color.Transparent else colors.stroke, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A big number with a small label under it, e.g. "27.4 / Points a game". */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Column(modifier) {
        Text(
            value,
            fontFamily = DisplayFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
            lineHeight = 28.sp,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label.uppercase(),
            style = OverlineTextStyle.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small rounded label, e.g. an injury status or "Next up". */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1)
    }
}
