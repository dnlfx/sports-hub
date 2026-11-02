package com.sportshub.app.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.components.EmptyState
import com.sportshub.app.ui.components.LightStatusBarIcons
import com.sportshub.app.ui.components.PillTabs
import com.sportshub.app.ui.components.PlayerHeadshot
import com.sportshub.app.ui.components.TeamLogo
import com.sportshub.app.ui.components.accentColor
import com.sportshub.app.ui.components.bottomListPadding
import com.sportshub.app.ui.components.heroColor
import com.sportshub.app.ui.scores.Section
import com.sportshub.app.ui.scores.SectionSkeleton
import com.sportshub.app.ui.theme.DisplayFamily
import com.sportshub.app.ui.theme.OverlineTextStyle
import com.sportshub.app.ui.theme.SportsHubTheme
import kotlin.math.sqrt

private val NeutralHero = Color(0xFF1E3F8C)

/** Which slot a picker is filling: the first (left) or second (right). */
private enum class Slot { First, Second }

/**
 * Head to head: two teams or two players side by side, stat by stat, with the better
 * number in each row picked out in that side's color.
 */
@Composable
fun CompareScreen(
    viewModel: CompareViewModel,
    sport: Sport,
    teamRepository: TeamRepository,
    favoriteTeams: List<Team>,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<Slot?>(null) }
    LightStatusBarIcons(enabled = true)

    val teams = state.kind == CompareKind.Teams
    // Each side's team, for colors: the team itself, or the player's team.
    val sideTeamA = if (teams) state.teamA else state.playerA?.let { state.players[it.id]?.team ?: it.team }
    val sideTeamB = if (teams) state.teamB else state.playerB?.let { state.players[it.id]?.team ?: it.team }
    val heroA = sideTeamA?.heroColor() ?: NeutralHero
    val heroB = sideTeamB?.heroColor() ?: lerp(NeutralHero, Color.Black, 0.3f)
    val wash = remember(heroA, heroB) { Brush.horizontalGradient(listOf(heroA, lerp(heroA, heroB, 0.5f), heroB)) }
    val (colorA, colorB) = sideColors(sideTeamA, sideTeamB)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(Modifier.fillMaxWidth().background(wash)) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Compare", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::swap) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = "Swap sides", tint = Color.White)
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
                        .background(wash)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (teams) {
                            TeamSlot(state.teamA, state.teamProfiles[state.teamA?.id]?.team?.record, Modifier.weight(1f)) { picking = Slot.First }
                            VersusBadge()
                            TeamSlot(state.teamB, state.teamProfiles[state.teamB?.id]?.team?.record, Modifier.weight(1f)) { picking = Slot.Second }
                        } else {
                            PlayerSlot(state.playerA, state, Modifier.weight(1f)) { picking = Slot.First }
                            VersusBadge()
                            PlayerSlot(state.playerB, state, Modifier.weight(1f)) { picking = Slot.Second }
                        }
                    }
                }
            }
            item(key = "kind") {
                Column {
                    PillTabs(
                        labels = CompareKind.entries.map { it.label },
                        selected = state.kind.ordinal,
                        onSelect = { viewModel.setKind(CompareKind.entries[it]) },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    )
                    if (!teams) {
                        Spacer(Modifier.height(8.dp))
                        PillTabs(
                            labels = listOf("Latest season", "Career"),
                            selected = if (state.career) 1 else 0,
                            onSelect = { viewModel.setCareer(it == 1) },
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        )
                    }
                }
            }

            val a = if (teams) state.teamA?.id else state.playerA?.id
            val b = if (teams) state.teamB?.id else state.playerB?.id
            when {
                a == null || b == null -> item(key = "pick") {
                    EmptyState(
                        icon = Icons.Outlined.CompareArrows,
                        title = if (teams) "Pick two teams" else "Pick two players",
                        message = if (teams) {
                            "Put any two teams side by side: record, scoring, offense, defense and more."
                        } else {
                            "Find each player on their team's roster, then compare their numbers stat by stat."
                        },
                        action = {
                            FilledTonalButton(onClick = { picking = if (a == null) Slot.First else Slot.Second }) {
                                Text(if (teams) "Pick a team" else "Pick a player")
                            }
                        },
                    )
                }
                else -> {
                    val loaded = if (teams) {
                        listOf(a, b).all { it in state.teamProfiles && it in state.teamStats }
                    } else {
                        listOf(a, b).all { it in state.players && it in state.careers }
                    }
                    val failed = listOf(a, b).any { it in state.failed }
                    val groups = if (!loaded) {
                        emptyList()
                    } else if (teams) {
                        compareTeams(
                            TeamSide(state.teamA!!, state.teamProfiles[a], state.teamStats[a]),
                            TeamSide(state.teamB!!, state.teamProfiles[b], state.teamStats[b]),
                        )
                    } else {
                        comparePlayers(
                            PlayerSide(state.players[a], state.careers[a]),
                            PlayerSide(state.players[b], state.careers[b]),
                            career = state.career,
                        )
                    }
                    when {
                        loaded -> {
                            val (edgeA, edgeB) = edges(groups)
                            if (edgeA + edgeB > 0) {
                                item(key = "edges") { EdgeTally(sideTeamA, sideTeamB, labelA(state), labelB(state), edgeA, edgeB, colorA, colorB) }
                            }
                            if (!teams && groups.none { it.key != "bio" }) {
                                item(key = "no_overlap") {
                                    EmptyState(
                                        icon = Icons.Outlined.CompareArrows,
                                        title = "No stats in common",
                                        message = "These two don't share a stat category. Players at the same position compare best.",
                                    )
                                }
                            }
                            groups.forEach { group ->
                                item(key = "g_${group.key}", contentType = "group") { CompareSection(group, colorA, colorB) }
                            }
                        }
                        failed -> item(key = "failed") {
                            EmptyState(
                                icon = Icons.Outlined.CloudOff,
                                title = "Couldn't load the numbers",
                                message = "Check your connection and try again.",
                                action = { FilledTonalButton(onClick = viewModel::retry) { Text("Try again") } },
                            )
                        }
                        else -> {
                            item(key = "skeleton1") { SectionSkeleton(if (teams) "Season" else "Bio", rows = 4) }
                            item(key = "skeleton2") { SectionSkeleton(if (teams) "Offense" else "Stats", rows = 6) }
                        }
                    }
                }
            }
        }
    }

    when (picking) {
        null -> Unit
        else -> {
            val first = picking == Slot.First
            if (teams) {
                TeamPickSheet(
                    sport = sport,
                    teamRepository = teamRepository,
                    favorites = favoriteTeams,
                    selectedId = (if (first) state.teamA else state.teamB)?.id,
                    otherId = (if (first) state.teamB else state.teamA)?.id,
                    onPick = { team ->
                        viewModel.pickTeam(first, team)
                        picking = null
                    },
                    onDismiss = { picking = null },
                )
            } else {
                val other = if (first) state.playerB else state.playerA
                PlayerPickSheet(
                    sport = sport,
                    teamRepository = teamRepository,
                    favorites = favoriteTeams,
                    otherId = other?.id,
                    otherPosition = other?.let { state.players[it.id]?.position ?: it.position },
                    onPick = { player ->
                        viewModel.pickPlayer(first, player)
                        picking = null
                    },
                    onDismiss = { picking = null },
                )
            }
        }
    }
}

private fun labelA(state: CompareUiState): String =
    if (state.kind == CompareKind.Teams) state.teamA?.abbreviation.orEmpty() else state.playerA?.name.orEmpty().shortPlayerName()

private fun labelB(state: CompareUiState): String =
    if (state.kind == CompareKind.Teams) state.teamB?.abbreviation.orEmpty() else state.playerB?.name.orEmpty().shortPlayerName()

/** "Josh Allen" as "J. Allen", to fit the tally. Names already short are kept. */
private fun String.shortPlayerName(): String {
    val parts = trim().split(" ")
    return if (parts.size >= 2 && parts[0].length > 2) "${parts[0].first()}. ${parts.drop(1).joinToString(" ")}" else this
}

/** Each side's color for values and bars, kept apart when both teams wear much the same color. */
@Composable
private fun sideColors(a: Team?, b: Team?): Pair<Color, Color> {
    val ca = a?.accentColor(MaterialTheme.colorScheme.primary) ?: MaterialTheme.colorScheme.primary
    val cb = b?.accentColor(MaterialTheme.colorScheme.tertiary) ?: MaterialTheme.colorScheme.tertiary
    val dr = ca.red - cb.red
    val dg = ca.green - cb.green
    val db = ca.blue - cb.blue
    val close = sqrt(dr * dr + dg * dg + db * db) < 0.25f
    return ca to (if (close) MaterialTheme.colorScheme.tertiary else cb)
}

@Composable
private fun VersusBadge() {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("VS", fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
    }
}

/** One side of the header. Tap it to pick, or change, the team. */
@Composable
private fun TeamSlot(team: Team?, record: String?, modifier: Modifier, onClick: () -> Unit) {
    SlotFrame(
        modifier = modifier,
        onClick = onClick,
        title = team?.name ?: "Pick a team",
        subtitle = if (team == null) " " else (record ?: team.record ?: team.abbreviation),
        empty = team == null,
    ) {
        if (team != null) TeamLogo(team, size = 62.dp)
    }
}

@Composable
private fun PlayerSlot(player: PlayerRef?, state: CompareUiState, modifier: Modifier, onClick: () -> Unit) {
    val profile = player?.let { state.players[it.id] }
    val team = profile?.team ?: player?.team
    SlotFrame(
        modifier = modifier,
        onClick = onClick,
        title = profile?.name ?: player?.name ?: "Pick a player",
        subtitle = if (player == null) " " else listOfNotNull(profile?.position ?: player.position, team?.abbreviation).joinToString(" · ").ifEmpty { " " },
        empty = player == null,
    ) {
        if (player != null) PlayerHeadshot(profile?.headshotUrl ?: player.headshotUrl, player.name, size = 84.dp)
    }
}

@Composable
private fun SlotFrame(
    modifier: Modifier,
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    empty: Boolean,
    image: @Composable () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(if (empty) 1.5.dp else 0.dp, Color.White.copy(alpha = if (empty) 0.5f else 0f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (empty) Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) else image()
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(
            if (empty) "Pick" else "Change",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** "Ahead in 9 of 14": how many rows each side wins, as a split bar. */
@Composable
private fun EdgeTally(
    teamA: Team?,
    teamB: Team?,
    nameA: String,
    nameB: String,
    edgeA: Int,
    edgeB: Int,
    colorA: Color,
    colorB: Color,
) {
    Section("Edges") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (teamA != null) {
                TeamLogo(teamA, size = 26.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("$edgeA", fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = colorA)
                Text(nameA, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("$edgeB", fontFamily = DisplayFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = colorB)
                Text(nameB, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (teamB != null) {
                Spacer(Modifier.width(8.dp))
                TeamLogo(teamB, size = 26.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        SplitBar(edgeA.toFloat() / (edgeA + edgeB), colorA, colorB, height = 8)
        Spacer(Modifier.height(8.dp))
        Text(
            "Stats where each side has the better number.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompareSection(group: CompareGroup, colorA: Color, colorB: Color) {
    val note = group.note
    val trailing: (@Composable () -> Unit)? = if (note == null) {
        null
    } else {
        @Composable { Text(note.uppercase(), style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Section(group.title, trailing = trailing) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            group.rows.forEach { row -> CompareRowView(row, colorA, colorB) }
        }
    }
}

@Composable
private fun CompareRowView(row: CompareRow, colorA: Color, colorB: Color) {
    val plain = MaterialTheme.colorScheme.onSurface
    val valueStyle = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.a,
                Modifier.weight(1f),
                style = valueStyle,
                fontWeight = if (row.edge < 0) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (row.edge < 0) colorA else plain,
                maxLines = 1,
            )
            Text(
                row.label,
                Modifier.weight(1.6f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                row.b,
                Modifier.weight(1f),
                textAlign = TextAlign.End,
                style = valueStyle,
                fontWeight = if (row.edge > 0) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (row.edge > 0) colorB else plain,
                maxLines = 1,
            )
        }
        row.share?.let { share ->
            Spacer(Modifier.height(6.dp))
            SplitBar(share, colorA.copy(alpha = if (row.edge > 0) 0.45f else 1f), colorB.copy(alpha = if (row.edge < 0) 0.45f else 1f))
        }
    }
}

/** A bar split between the two sides: the first side's share on the left. */
@Composable
private fun SplitBar(share: Float, colorA: Color, colorB: Color, height: Int = 6) {
    val a = share.coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (a > 0f) Box(Modifier.weight(a).fillMaxSize().clip(CircleShape).background(colorA))
        if (a < 1f) Box(Modifier.weight(1f - a).fillMaxSize().clip(CircleShape).background(colorB))
    }
}
