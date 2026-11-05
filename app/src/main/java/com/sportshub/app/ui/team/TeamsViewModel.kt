package com.sportshub.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.Sport
import com.sportshub.app.model.TeamListing
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamsUiState(
    val sport: Sport = Sport.default,
    val groups: List<TeamGroup>? = null,
    /** Team id to record, e.g. "6-2", taken from the scores already loaded. */
    val records: Map<String, String> = emptyMap(),
    val error: Boolean = false,
)

class TeamsViewModel(
    private val teams: TeamRepository,
    private val scores: ScoresRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(stateFor(Sport.default))
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var loadedSport: Sport? = null

    private fun stateFor(sport: Sport) = TeamsUiState(
        sport = sport,
        groups = teams.peekTeams(sport)?.let { groupTeams(sport, it) },
        records = recordsFor(sport),
    )

    private fun recordsFor(sport: Sport): Map<String, String> = scores.peekScoreboard(sport)?.games.orEmpty()
        .flatMap { listOf(it.home.team, it.away.team) }
        .mapNotNull { t -> t.record?.let { t.id to it } }
        .toMap()

    fun setSport(sport: Sport) {
        if (sport == loadedSport) {
            // Pick up records from scores loaded since.
            _state.update { it.copy(records = recordsFor(sport).ifEmpty { it.records }) }
            return
        }
        loadedSport = sport
        if (sport != _state.value.sport) _state.value = stateFor(sport)
        load()
    }

    fun retry() {
        _state.update { it.copy(error = false) }
        load()
    }

    private fun load() {
        val sport = loadedSport ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_state.value.groups == null) {
                teams.cachedTeams(sport)?.let { cached -> _state.update { it.copy(groups = groupTeams(sport, cached)) } }
            }
            if (_state.value.records.isEmpty()) {
                scores.cachedScoreboard(sport)?.let { _ -> _state.update { it.copy(records = recordsFor(sport)) } }
            }
            teams.teams(sport).fold(
                onSuccess = { list -> _state.update { it.copy(groups = groupTeams(sport, list), error = false) } },
                onFailure = { if (_state.value.groups == null) _state.update { it.copy(error = true) } },
            )
        }
    }
}
