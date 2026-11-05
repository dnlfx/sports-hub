package com.sportshub.app.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.PositionStats
import com.sportshub.app.model.BioItem
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LightStatusBarIcons
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.heroColor
import com.sportshub.app.ui.components.shimmer
import com.sportshub.app.ui.scores.GridRow
import com.sportshub.app.ui.scores.Section
import com.sportshub.app.ui.scores.SectionSkeleton
import com.sportshub.app.ui.scores.StatGrid
import com.sportshub.app.ui.theme.DisplayFamily
import com.sportshub.app.ui.theme.OnHeroMuted
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme

/** A player's page: who they are, how this season is going, and their career year by year. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerProfileScreen(
    viewModel: PlayerProfileViewModel,
    onBack: () -> Unit,
    onOpenTeam: (Team) -> Unit,
    onCompare: (PlayerRef) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile
    val ref = state.ref
    val team = profile?.team ?: ref?.team
    val name = profile?.name ?: ref?.name.orEmpty()
    val headshot = profile?.headshotUrl ?: ref?.headshotUrl
    val hero = team?.heroColor() ?: Color(0xFF1E3F8C)
    // Only the stat categories that fit the player's position (plus any they really play).
    val position = profile?.position ?: ref?.position
    val relevantCareer = remember(state.career, position) { state.career?.let { PositionStats.relevant(it, position) } }
    LightStatusBarIcons(enabled = true)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxWidth().background(hero)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val id = profile?.id ?: ref?.id
                        if (id != null) onCompare(
                            PlayerRef(
                                id = id,
                                name = name,
                                headshotUrl = headshot,
                                position = profile?.position ?: ref?.position,
                                jersey = profile?.jersey ?: ref?.jersey,
                                team = team,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = "Compare with another player", tint = Color.White)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomListPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(Brush.verticalGradient(listOf(hero, lerp(hero, Color.Black, 0.35f)))),
                ) {
                    if (team != null) {
                        TeamLogo(
                            team,
                            size = 220.dp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 60.dp)
                                .alpha(0.12f),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PlayerHeadshot(
                            headshot,
                            name,
                            size = 120.dp,
                            modifier = Modifier.border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            name,
                            fontFamily = DisplayFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp,
                            lineHeight = 36.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        val line = listOfNotNull(
                            (profile?.jersey ?: ref?.jersey)?.let { "#$it" },
                            profile?.positionName ?: profile?.position ?: ref?.position,
                        ).joinToString(" · ")
                        Text(
                            line.ifEmpty { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnHeroMuted,
                        )
                        if (team != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.14f))
                                    .clickable { onOpenTeam(team) }
                                    .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TeamLogo(team, size = 22.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(team.name, style = MaterialTheme.typography.labelLarge, color = Color.White)
                            }
                        }
                        when {
                            profile != null -> if (profile.bio.isNotEmpty()) {
                                Spacer(Modifier.height(18.dp))
                                HeroBio(profile.bio)
                            }
                            !state.profileFailed -> {
                                Spacer(Modifier.height(18.dp))
                                HeroBioSkeleton()
                            }
                        }
                    }
                }
            }

            when {
                profile != null -> {
                    if (profile.summary.isNotEmpty()) {
                        item(key = "summary") {
                            Section(profile.summaryTitle?.replaceFirstChar { it.uppercase() } ?: "This season") {
                                Row(Modifier.fillMaxWidth()) {
                                    profile.summary.forEach { stat ->
                                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                stat.value,
                                                fontFamily = DisplayFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 26.sp,
                                                maxLines = 1,
                                            )
                                            Text(stat.label, style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            stat.rank?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                state.profileFailed -> item(key = "failed") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Couldn't load this player",
                        message = "Check your connection and try again.",
                        action = { FilledTonalButton(onClick = viewModel::retry) { Text("Try again") } },
                    )
                }
                else -> Unit
            }

            val career = relevantCareer
            when {
                career == null && !state.careerFailed -> item(key = "career_skeleton") { SectionSkeleton("Career", rows = 5) }
                career.isNullOrEmpty() -> Unit
                else -> {
                    val career = career.orEmpty()
                    val season = career.firstNotNullOfOrNull { it.seasons.firstOrNull()?.label }
                    val latest = career.filter { it.seasons.firstOrNull()?.label == season }
                    if (season != null && latest.isNotEmpty()) {
                        item(key = "season") { SeasonCard(season, latest) }
                    }
                    career.forEach { table ->
                        item(key = "career_${table.key}", contentType = "career") { CareerSection(table) }
                    }
                }
            }
        }
    }
}

/** Height, age, college and the rest, two to a row on the header under the player's name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroBio(bio: List<BioItem>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        bio.forEach { item ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black.copy(alpha = 0.22f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(item.label.uppercase(), style = OverlineTextStyle, color = OnHeroMuted, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(item.value, style = MaterialTheme.typography.titleSmall, color = Color.White)
            }
        }
    }
}

/** Placeholder tiles the size of [HeroBio] while the profile loads. */
@Composable
private fun HeroBioSkeleton() {
    Column(Modifier.fillMaxWidth().shimmer(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.Black.copy(alpha = 0.22f)),
                    )
                }
            }
        }
    }
}

/** The latest season in every category the player has stats in, as label and value pairs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonCard(season: String, tables: List<CareerTable>) {
    Section("$season season") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tables.forEach { table ->
                val row = table.seasons.first()
                Column {
                    Text(table.title.uppercase(), style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        table.columns.forEachIndexed { i, label ->
                            Column(
                                Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(SportsHubTheme.colors.cardHigh)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    row.values.getOrElse(i) { "-" },
                                    fontFamily = DisplayFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Year-by-year table for one category, newest first, with the career total at the bottom. */
@Composable
private fun CareerSection(table: CareerTable) {
    val rows = remember(table) {
        table.seasons.mapIndexed { i, s -> GridRow(key = "${s.label}_$i", label = s.label, sub = s.team, values = s.values) } +
            listOfNotNull(table.totals?.let { GridRow(key = "total", label = it.label, values = it.values, emphasized = true) })
    }
    Section("Career ${table.title.lowercase()}") {
        StatGrid(columns = table.columns, rows = rows, showHeadshots = false)
    }
}
