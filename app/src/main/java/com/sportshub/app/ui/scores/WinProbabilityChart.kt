package com.sportshub.app.ui.scores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sportshub.app.model.Game
import com.sportshub.app.model.PlayKind
import com.sportshub.app.model.Team
import com.sportshub.app.model.WinProbPoint
import com.sportshub.app.model.WinProbability
import com.sportshub.app.ui.components.Tag
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.theme.DisplayFamily
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// The numbers behind the chart
// ---------------------------------------------------------------------------

/** How much play [i] moved the home team's chance, from -1 to 1. */
internal fun swingAt(points: List<WinProbPoint>, i: Int): Float =
    if (i <= 0 || i >= points.size) 0f else points[i].homeWinPct - points[i - 1].homeWinPct

/**
 * The plays worth marking on the chart: the biggest swings, plus scores and turnovers
 * that moved the needle at all. At most [max], in game order.
 */
internal fun keyPlays(points: List<WinProbPoint>, max: Int = 8): List<Int> =
    (1 until points.size)
        .filter { i ->
            val swing = abs(swingAt(points, i))
            swing >= 0.08f || (points[i].kind != PlayKind.Other && swing >= 0.03f)
        }
        .sortedByDescending { abs(swingAt(points, it)) }
        .take(max)
        .sorted()

/** The first point of each period, as (period, index), for the quarter lines. */
internal fun periodStarts(points: List<WinProbPoint>): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    var last: Int? = null
    points.forEachIndexed { i, p ->
        val period = p.period ?: return@forEachIndexed
        if (last == null || period > last!!) {
            out += period to i
            last = period
        }
    }
    return out
}

internal fun periodLabel(period: Int, short: String = "Q"): String = when {
    period <= 4 -> "$short$period"
    period == 5 -> "OT"
    else -> "${period - 4}OT"
}

/** "+18%" style percentage points for a swing. */
private fun swingText(swing: Float): String = "+${(abs(swing) * 100).roundToInt()}%"

private fun pct(value: Float): String = "${(value * 100).roundToInt()}%"

/** Team colors for the chart, kept apart when both teams wear much the same color. */
@Composable
private fun chartColors(game: Game): Pair<Color, Color> {
    val away = game.away.team.accentColor(MaterialTheme.colorScheme.tertiary)
    val home = game.home.team.accentColor(MaterialTheme.colorScheme.primary)
    val dr = away.red - home.red
    val dg = away.green - home.green
    val db = away.blue - home.blue
    val close = sqrt(dr * dr + dg * dg + db * db) < 0.25f
    return (if (close) MaterialTheme.colorScheme.tertiary else away) to home
}

// ---------------------------------------------------------------------------
// The section on the game page
// ---------------------------------------------------------------------------

/**
 * The win probability chart: the home team's chance on top, the away team's below, a
 * marker on each play that swung the game, and a card for the play under your finger.
 * Tap a marker, or drag across the chart to scrub through every play.
 */
@Composable
internal fun WinProbabilitySection(wp: WinProbability, game: Game) {
    val points = wp.points
    val keys = remember(points) { keyPlays(points) }
    // The play being looked at; -1 means the default, the biggest swing of the game.
    var picked by rememberSaveable(game.id) { mutableIntStateOf(-1) }
    val selected = picked.takeIf { it in points.indices }
        ?: keys.maxByOrNull { abs(swingAt(points, it)) }
        ?: points.lastIndex
    val (awayColor, homeColor) = chartColors(game)
    val point = points[selected]

    Section("Win probability") {
        // The chance at the selected play, for each team.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChanceLabel(game.away.team, 1f - point.homeWinPct, awayColor, alignEnd = false, modifier = Modifier.weight(1f))
            ChanceLabel(game.home.team, point.homeWinPct, homeColor, alignEnd = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        WinProbabilityChart(
            points = points,
            keys = keys,
            selected = selected,
            awayColor = awayColor,
            homeColor = homeColor,
            awayLabel = game.away.team.abbreviation,
            homeLabel = game.home.team.abbreviation,
            live = game.isLive,
            periodShort = game.sport.periodName,
            onSelect = { picked = it },
        )
        Spacer(Modifier.height(12.dp))
        PlayCard(game, points, selected, isKey = selected in keys, awayColor = awayColor, homeColor = homeColor)
        Spacer(Modifier.height(10.dp))
        Text(
            buildString {
                append("Tap a marked play or drag across the chart.")
                wp.pregameHomePct?.let { pre ->
                    val favorite = if (pre >= 0.5f) game.home.team.abbreviation to pre else game.away.team.abbreviation to 1f - pre
                    append(" Before kickoff: ${favorite.first} ${pct(favorite.second)}.")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChanceLabel(team: Team, chance: Float, color: Color, alignEnd: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!alignEnd) {
            TeamLogo(team, size = 28.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alignEnd) Dot(color)
                Text(
                    pct(chance),
                    fontFamily = DisplayFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                if (!alignEnd) Dot(color)
            }
            Text(
                team.abbreviation,
                style = OverlineTextStyle.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        if (alignEnd) {
            Spacer(Modifier.width(8.dp))
            TeamLogo(team, size = 28.dp)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun WinProbabilityChart(
    points: List<WinProbPoint>,
    keys: List<Int>,
    selected: Int,
    awayColor: Color,
    homeColor: Color,
    awayLabel: String,
    homeLabel: String,
    live: Boolean,
    periodShort: String,
    onSelect: (Int) -> Unit,
) {
    val select by rememberUpdatedState(onSelect)
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = SportsHubTheme.colors.stroke
    val card = SportsHubTheme.colors.card
    val liveColor = if (live) SportsHubTheme.colors.live else null
    val axisStyle = OverlineTextStyle.copy(fontSize = 10.sp, color = muted)
    val periods = remember(points) { periodStarts(points) }
    val n = points.size

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .semantics { contentDescription = "Win probability chart" }
            .pointerInput(n, keys) {
                fun xOf(i: Int) = if (n <= 1) 0f else i * size.width.toFloat() / (n - 1)
                fun indexAt(x: Float, snap: Boolean): Int {
                    val raw = if (n <= 1) 0 else ((x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                    if (!snap) return raw
                    val near = keys.minByOrNull { abs(xOf(it) - x) } ?: return raw
                    return if (abs(xOf(near) - x) <= 28.dp.toPx()) near else raw
                }
                detectTapGestures { select(indexAt(it.x, snap = true)) }
            }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                    select(if (n <= 1) 0 else ((x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1))
                }
            },
    ) {
        val axis = 20.dp.toPx()
        val pad = 8.dp.toPx()
        val top = pad
        val bottom = size.height - axis - pad
        val h = bottom - top
        fun x(i: Int) = if (n <= 1) size.width / 2f else i * size.width / (n - 1)
        fun y(p: Float) = top + (1f - p) * h
        val mid = y(0.5f)

        // Frame: 100% lines and a dashed 50% line.
        drawLine(grid, Offset(0f, top), Offset(size.width, top), 1.dp.toPx())
        drawLine(grid, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        drawLine(
            muted.copy(alpha = 0.5f),
            Offset(0f, mid),
            Offset(size.width, mid),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )

        // A line at the start of each quarter, labelled underneath.
        periods.forEach { (period, i) ->
            val px = x(i)
            if (i > 0) drawLine(grid, Offset(px, top), Offset(px, bottom), 1.dp.toPx())
            val label = measurer.measure(periodLabel(period, periodShort), axisStyle)
            val lx = (px + 4.dp.toPx()).coerceAtMost(size.width - label.size.width)
            drawText(label, topLeft = Offset(lx, bottom + pad + 2.dp.toPx()))
        }

        // Team names in the corners of their half.
        val homeText = measurer.measure(homeLabel, axisStyle.copy(color = homeColor))
        val awayText = measurer.measure(awayLabel, axisStyle.copy(color = awayColor))
        drawText(homeText, topLeft = Offset(size.width - homeText.size.width - 2.dp.toPx(), top + 3.dp.toPx()))
        drawText(awayText, topLeft = Offset(size.width - awayText.size.width - 2.dp.toPx(), bottom - awayText.size.height - 3.dp.toPx()))

        if (n >= 2) {
            val line = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(i), y(p.homeWinPct)) else lineTo(x(i), y(p.homeWinPct)) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(n - 1), mid)
                lineTo(x(0), mid)
                close()
            }
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Above the middle the home team is ahead: shade and draw that part in its color.
            clipRect(bottom = mid) {
                drawPath(area, Brush.verticalGradient(listOf(homeColor.copy(alpha = 0.42f), homeColor.copy(alpha = 0.06f)), startY = top, endY = mid))
                drawPath(line, homeColor, style = stroke)
            }
            clipRect(top = mid) {
                drawPath(area, Brush.verticalGradient(listOf(awayColor.copy(alpha = 0.06f), awayColor.copy(alpha = 0.42f)), startY = mid, endY = bottom))
                drawPath(line, awayColor, style = stroke)
            }
        }

        // Guide through the selected play.
        val sx = x(selected)
        drawLine(ink.copy(alpha = 0.35f), Offset(sx, top), Offset(sx, bottom), 1.dp.toPx())

        // Markers on the plays that swung the game, in the color of the team they helped.
        keys.forEach { i ->
            val c = if (swingAt(points, i) >= 0f) homeColor else awayColor
            val center = Offset(x(i), y(points[i].homeWinPct))
            drawCircle(card, radius = 6.5.dp.toPx(), center = center)
            drawCircle(c, radius = 4.5.dp.toPx(), center = center)
        }

        // The latest play of a live game.
        if (liveColor != null && n >= 1) {
            val center = Offset(x(n - 1), y(points[n - 1].homeWinPct))
            drawCircle(liveColor.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = center)
            drawCircle(liveColor, radius = 4.dp.toPx(), center = center)
        }

        // The selected play.
        val sc = Offset(sx, y(points[selected].homeWinPct))
        drawCircle(ink, radius = 7.dp.toPx(), center = sc)
        drawCircle(card, radius = 3.5.dp.toPx(), center = sc)
    }
}

/** The play under the guide: what happened, when, the score after it, and how much it moved the game. */
@Composable
private fun PlayCard(
    game: Game,
    points: List<WinProbPoint>,
    index: Int,
    isKey: Boolean,
    awayColor: Color,
    homeColor: Color,
) {
    val p = points[index]
    val swing = swingAt(points, index)
    val helped = if (swing >= 0f) game.home.team else game.away.team
    val helpedColor = if (swing >= 0f) homeColor else awayColor
    val tag = when (p.kind) {
        PlayKind.Touchdown -> "TOUCHDOWN"
        PlayKind.FieldGoal -> "FIELD GOAL"
        PlayKind.Safety -> "SAFETY"
        PlayKind.Turnover -> "TURNOVER"
        PlayKind.Other -> if (p.isScoring) "SCORE" else if (isKey) "BIG PLAY" else "PLAY"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SportsHubTheme.colors.cardHigh)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(tag, if (isKey || p.kind != PlayKind.Other) helpedColor else MaterialTheme.colorScheme.onSurfaceVariant)
            val time = listOfNotNull(p.period?.let { periodLabel(it, game.sport.periodName) }, p.clock).joinToString(" · ")
            if (time.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (p.awayScore != null && p.homeScore != null) {
                Text(
                    "${game.away.team.abbreviation} ${p.awayScore} – ${p.homeScore} ${game.home.team.abbreviation}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            p.text ?: "Play ${index + 1} of ${points.size}",
            style = MaterialTheme.typography.bodyMedium,
            minLines = 2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (abs(swing) >= 0.005f) "${helped.abbreviation} ${swingText(swing)} win chance" else "No change in win chance",
            style = MaterialTheme.typography.labelLarge,
            color = if (abs(swing) >= 0.005f) helpedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Placeholder with the chart's shape, while the game's details load. */
@Composable
internal fun WinProbabilitySkeleton() {
    val block = SportsHubTheme.colors.cardHigh
    Section("Win probability") {
        Column(Modifier.shimmer(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(40.dp).clip(MaterialTheme.shapes.small).background(block))
            Box(Modifier.fillMaxWidth().height(190.dp).clip(MaterialTheme.shapes.medium).background(block))
            Box(Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.medium).background(block))
        }
    }
}

/** Before kickoff: ESPN's pick for each team, as a split bar. */
@Composable
internal fun MatchupPredictor(homePct: Float, game: Game) {
    val (awayColor, homeColor) = chartColors(game)
    Section("Matchup predictor") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChanceLabel(game.away.team, 1f - homePct, awayColor, alignEnd = false, modifier = Modifier.weight(1f))
            ChanceLabel(game.home.team, homePct, homeColor, alignEnd = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val away = (1f - homePct).coerceIn(0.01f, 0.99f)
            Box(Modifier.weight(away).height(10.dp).clip(CircleShape).background(awayColor))
            Box(Modifier.weight(1f - away).height(10.dp).clip(CircleShape).background(homeColor))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "ESPN Analytics' chance to win, before kickoff.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
