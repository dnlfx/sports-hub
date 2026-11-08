package com.sportshub.app.ui.team

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.Sport
import com.sportshub.app.model.TeamStanding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamsUiState(
    val sport: Sport = Sport.default,
    val groups: List<TeamGroup>? = null,
    /** The groups in standings order, with ranks once the standings are in. */
    val divisions: List<DivisionStandings>? = null,
    val standings: Map<String, TeamStanding> = emptyMap(),
    /** Team id to record, e.g. "6-2", taken from the scores already loaded. Fills in for missing standings. */
    val records: Map<String, String> = emptyMap(),
    val error: Boolean = false,
    /** True when the teams loaded but the standings couldn't, so ranks are missing. */
    val standingsFailed: Boolean = false,
) {
    /** A team's record: the standings' own, or the one on its latest score. */
    fun recordOf(teamId: String): String? = standings[teamId]?.overallRecord() ?: records[teamId]
}

class TeamsViewModel(
    private val teams: TeamRepository,
    private val scores: ScoresRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(stateFor(Sport.default))
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var standingsJob: Job? = null
    private var loadedSport: Sport? = null
    private var standingsLoadedAt = 0L

    private fun stateFor(sport: Sport) = TeamsUiState(
        sport = sport,
        groups = teams.peekTeams(sport)?.let { groupTeams(sport, it) },
        standings = teams.peekStandings(sport).orEmpty(),
        records = recordsFor(sport),
    ).ranked()

    /** Re-sorts the divisions after the teams or the standings change. */
    private fun TeamsUiState.ranked(): TeamsUiState = copy(divisions = groups?.let { rankGroups(sport, it, standings) })

    private fun recordsFor(sport: Sport): Map<String, String> = scores.peekScoreboard(sport)?.games.orEmpty()
        .flatMap { listOf(it.home.team, it.away.team) }
        .mapNotNull { t -> t.record?.let { t.id to it } }
        .toMap()

    fun setSport(sport: Sport) {
        if (sport == loadedSport) {
            // Pick up records from scores loaded since, and standings that a final score has moved.
            _state.update { it.copy(records = recordsFor(sport).ifEmpty { it.records }) }
            if (SystemClock.elapsedRealtime() - standingsLoadedAt > STANDINGS_REFRESH_MS) loadStandings(sport)
            return
        }
        loadedSport = sport
        if (sport != _state.value.sport) _state.value = stateFor(sport)
        load()
    }

    fun retry() {
        _state.update { it.copy(error = false, standingsFailed = false) }
        load()
    }

    private fun load() {
        val sport = loadedSport ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_state.value.groups == null) {
                teams.cachedTeams(sport)?.let { cached -> update(sport) { it.copy(groups = groupTeams(sport, cached)) } }
            }
            if (_state.value.standings.isEmpty()) {
                teams.cachedStandings(sport)?.let { cached -> update(sport) { it.copy(standings = cached) } }
            }
            if (_state.value.records.isEmpty()) {
                scores.cachedScoreboard(sport)?.let { _ -> _state.update { it.copy(records = recordsFor(sport)) } }
            }
            loadStandings(sport)
            teams.teams(sport).fold(
                onSuccess = { list -> update(sport) { it.copy(groups = groupTeams(sport, list), error = false) } },
                onFailure = { if (_state.value.groups == null) _state.update { it.copy(error = true) } },
            )
        }
    }

    private fun loadStandings(sport: Sport) {
        standingsJob?.cancel()
        standingsJob = viewModelScope.launch {
            teams.standings(sport).fold(
                onSuccess = { table ->
                    standingsLoadedAt = SystemClock.elapsedRealtime()
                    update(sport) { it.copy(standings = table, standingsFailed = false) }
                },
                onFailure = { _state.update { s -> s.copy(standingsFailed = s.standings.isEmpty()) } },
            )
        }
    }

    /** Applies [change] and re-ranks, unless the user has moved on to another sport. Ranking a league is quick. */
    private fun update(sport: Sport, change: (TeamsUiState) -> TeamsUiState) {
        _state.update { if (it.sport == sport) change(it).ranked() else it }
    }

    private companion object {
        const val STANDINGS_REFRESH_MS = 2 * 60 * 1000L
    }
}
