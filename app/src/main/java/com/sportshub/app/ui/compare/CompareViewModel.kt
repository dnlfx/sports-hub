package com.sportshub.app.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.TeamPart
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.data.peekProfile
import com.sportshub.app.data.peekTeamStats
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.PlayerProfile
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.model.TeamSeasonStats
import com.sportshub.app.ui.scores.keepIfEqual
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CompareKind(val id: String, val label: String) {
    Teams("team", "Teams"),
    Players("player", "Players");

    companion object {
        fun fromId(id: String?): CompareKind = entries.firstOrNull { it.id == id } ?: Teams
    }
}

/**
 * Everything on the compare page. Both kinds keep their picks, so switching between
 * teams and players doesn't lose either. Loaded data is keyed by team or player id.
 */
data class CompareUiState(
    val kind: CompareKind,
    val teamA: Team? = null,
    val teamB: Team? = null,
    val playerA: PlayerRef? = null,
    val playerB: PlayerRef? = null,
    val teamProfiles: Map<String, TeamProfile> = emptyMap(),
    val teamStats: Map<String, TeamSeasonStats> = emptyMap(),
    val players: Map<String, PlayerProfile> = emptyMap(),
    val careers: Map<String, List<CareerTable>> = emptyMap(),
    /** Ids whose data couldn't be loaded and isn't on screen. */
    val failed: Set<String> = emptySet(),
    /** Players: whole careers instead of the latest season. */
    val career: Boolean = false,
)

class CompareViewModel(
    private val repository: TeamRepository,
    private val sport: Sport,
    kind: CompareKind,
    firstTeam: Team?,
    firstPlayer: PlayerRef?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CompareUiState(kind = kind, teamA = firstTeam, playerA = firstPlayer).let { s -> withMemory(s, firstTeam?.id, firstPlayer?.id) },
    )
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    init {
        firstTeam?.let { loadTeam(it.id) }
        firstPlayer?.let { loadPlayer(it.id) }
    }

    fun setKind(kind: CompareKind) = _state.update { it.copy(kind = kind) }

    fun setCareer(career: Boolean) = _state.update { it.copy(career = career) }

    /** Puts [team] in the first ([first] true) or second slot. */
    fun pickTeam(first: Boolean, team: Team) {
        _state.update { s -> withMemory(if (first) s.copy(teamA = team) else s.copy(teamB = team), team.id, null) }
        loadTeam(team.id)
    }

    fun pickPlayer(first: Boolean, player: PlayerRef) {
        _state.update { s -> withMemory(if (first) s.copy(playerA = player) else s.copy(playerB = player), null, player.id) }
        loadPlayer(player.id)
    }

    fun swap() = _state.update { s ->
        if (s.kind == CompareKind.Teams) s.copy(teamA = s.teamB, teamB = s.teamA) else s.copy(playerA = s.playerB, playerB = s.playerA)
    }

    fun retry() {
        val s = _state.value
        _state.update { it.copy(failed = emptySet()) }
        listOfNotNull(s.teamA, s.teamB).forEach { loadTeam(it.id) }
        listOfNotNull(s.playerA, s.playerB).forEach { loadPlayer(it.id) }
    }

    /** Fills in whatever is already in memory, so a team or player seen before draws in full straight away. */
    private fun withMemory(s: CompareUiState, teamId: String?, playerId: String?): CompareUiState {
        var out = s
        if (teamId != null) {
            repository.peekProfile(sport, teamId)?.let { out = out.copy(teamProfiles = out.teamProfiles + (teamId to it)) }
            repository.peekTeamStats(sport, teamId)?.let { out = out.copy(teamStats = out.teamStats + (teamId to it)) }
        }
        if (playerId != null) {
            repository.peekPlayer(sport, playerId)?.let { out = out.copy(players = out.players + (playerId to it)) }
            repository.peekCareer(sport, playerId)?.let { out = out.copy(careers = out.careers + (playerId to it)) }
        }
        return out
    }

    private fun loadTeam(id: String) {
        viewModelScope.launch {
            if (id !in _state.value.teamProfiles) {
                repository.cached<TeamProfile>(sport, TeamPart.Profile, id)?.let { p -> _state.update { it.copy(teamProfiles = it.teamProfiles + (id to p)) } }
            }
            repository.load<TeamProfile>(sport, TeamPart.Profile, id).fold(
                onSuccess = { p -> _state.update { it.copy(teamProfiles = it.teamProfiles + (id to keepIfEqual(it.teamProfiles[id], p))) } },
                onFailure = { if (id !in _state.value.teamProfiles) _state.update { it.copy(failed = it.failed + id) } },
            )
        }
        viewModelScope.launch {
            if (id !in _state.value.teamStats) {
                repository.cached<TeamSeasonStats>(sport, TeamPart.TeamStats, id)?.let { v -> _state.update { it.copy(teamStats = it.teamStats + (id to v)) } }
            }
            repository.load<TeamSeasonStats>(sport, TeamPart.TeamStats, id).fold(
                onSuccess = { v -> _state.update { it.copy(teamStats = it.teamStats + (id to keepIfEqual(it.teamStats[id], v))) } },
                onFailure = { if (id !in _state.value.teamStats) _state.update { it.copy(failed = it.failed + id) } },
            )
        }
    }

    private fun loadPlayer(id: String) {
        viewModelScope.launch {
            if (id !in _state.value.players) {
                repository.cachedPlayer(sport, id)?.let { p -> _state.update { it.copy(players = it.players + (id to p)) } }
            }
            repository.player(sport, id).fold(
                onSuccess = { p -> _state.update { it.copy(players = it.players + (id to keepIfEqual(it.players[id], p))) } },
                onFailure = { if (id !in _state.value.players) _state.update { it.copy(failed = it.failed + id) } },
            )
        }
        viewModelScope.launch {
            if (id !in _state.value.careers) {
                repository.cachedCareer(sport, id)?.let { c -> _state.update { it.copy(careers = it.careers + (id to c)) } }
            }
            repository.career(sport, id).fold(
                onSuccess = { c -> _state.update { it.copy(careers = it.careers + (id to keepIfEqual(it.careers[id], c))) } },
                onFailure = { if (id !in _state.value.careers) _state.update { it.copy(failed = it.failed + id) } },
            )
        }
    }
}
