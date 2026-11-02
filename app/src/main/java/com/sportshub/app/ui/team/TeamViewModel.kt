package com.sportshub.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.TeamPart
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.data.peekDepth
import com.sportshub.app.data.peekProfile
import com.sportshub.app.data.peekRoster
import com.sportshub.app.data.peekSchedule
import com.sportshub.app.data.peekTeamStats
import com.sportshub.app.model.DepthChart
import com.sportshub.app.model.PlayerSeasonStats
import com.sportshub.app.model.RosterGroup
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.model.TeamProfile
import com.sportshub.app.model.TeamSchedule
import com.sportshub.app.model.TeamSeasonStats
import com.sportshub.app.ui.scores.keepIfEqual
import com.sportshub.app.ui.scores.nextPollDelayMs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything on a team page. A null part hasn't loaded yet; its name in [failed] means it couldn't. */
data class TeamUiState(
    val team: Team?,
    val profile: TeamProfile? = null,
    val schedule: TeamSchedule? = null,
    val teamStats: TeamSeasonStats? = null,
    val playerStats: PlayerSeasonStats? = null,
    val roster: List<RosterGroup>? = null,
    val depth: List<DepthChart>? = null,
    val failed: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
) {
    /** The team as best known: from the profile once loaded, else from the screen that opened it. */
    val displayTeam: Team? get() = profile?.team?.let { p -> p.copy(record = p.record ?: team?.record) } ?: team
}

const val PLAYERS_PART = "players"

class TeamViewModel(
    private val repository: TeamRepository,
    private val sport: Sport,
    private val teamId: String,
    initialTeam: Team?,
) : ViewModel() {

    // Start from whatever is already in memory, so a team opened before is complete on its first frame.
    private val _state = MutableStateFlow(
        repository.peekSchedule(sport, teamId).let { schedule ->
            TeamUiState(
                team = initialTeam ?: repository.teamInfo(sport, teamId),
                profile = repository.peekProfile(sport, teamId),
                schedule = schedule,
                teamStats = repository.peekTeamStats(sport, teamId),
                playerStats = repository.peekPlayerStats(sport, teamId, schedule),
                roster = repository.peekRoster(sport, teamId),
                depth = repository.peekDepth(sport, teamId),
            )
        },
    )
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var lastLoadAtMs = 0L

    init {
        loadJob = viewModelScope.launch { loadAll() }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadAll() }
    }

    fun retry() {
        _state.update { it.copy(failed = emptySet()) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadAll() }
    }

    /** While the page is visible: refresh every minute during a live game, every 10 minutes otherwise. */
    suspend fun pollWhileVisible() {
        while (true) {
            loadJob?.join()
            val live = _state.value.schedule?.entries?.any { it.game.isLive } == true
            delay(nextPollDelayMs(if (live) 60_000L else 600_000L, System.currentTimeMillis() - lastLoadAtMs))
            if (loadJob?.isActive != true) {
                loadJob = viewModelScope.launch { loadAll() }
            }
        }
    }

    private suspend fun loadAll() {
        coroutineScope {
            launch { load<TeamProfile>(TeamPart.Profile, { it.profile }) { s, v -> s.copy(profile = v) } }
            launch { load<TeamSeasonStats>(TeamPart.TeamStats, { it.teamStats }) { s, v -> s.copy(teamStats = v) } }
            launch { load<List<RosterGroup>>(TeamPart.Roster, { it.roster }) { s, v -> s.copy(roster = v) } }
            launch { load<List<DepthChart>>(TeamPart.Depth, { it.depth }) { s, v -> s.copy(depth = v) } }
            launch {
                val schedule = load<TeamSchedule>(TeamPart.Schedule, { it.schedule }) { s, v -> s.copy(schedule = v) }
                if (schedule == null) {
                    if (_state.value.playerStats == null) _state.update { it.copy(failed = it.failed + PLAYERS_PART) }
                } else {
                    repository.playerStats(sport, teamId, schedule).fold(
                        onSuccess = { stats ->
                            _state.update { it.copy(playerStats = keepIfEqual(it.playerStats, stats), failed = it.failed - PLAYERS_PART) }
                        },
                        onFailure = {
                            if (_state.value.playerStats == null) _state.update { it.copy(failed = it.failed + PLAYERS_PART) }
                        },
                    )
                }
            }
        }
        lastLoadAtMs = System.currentTimeMillis()
        _state.update { it.copy(isRefreshing = false) }
    }

    /**
     * Loads one part: from disk first when nothing is showing yet, then fresh from the
     * network. Returns the newest value, or null if there's nothing to show.
     */
    private suspend fun <T : Any> load(
        part: TeamPart,
        get: (TeamUiState) -> T?,
        set: (TeamUiState, T) -> TeamUiState,
    ): T? {
        if (get(_state.value) == null) {
            repository.cached<T>(sport, part, teamId)?.let { cached ->
                _state.update { s -> if (get(s) == null) set(s, cached) else s }
            }
        }
        repository.load<T>(sport, part, teamId).fold(
            onSuccess = { fresh -> _state.update { s -> set(s, keepIfEqual(get(s), fresh)).let { it.copy(failed = it.failed - part.id) } } },
            onFailure = { if (get(_state.value) == null) _state.update { it.copy(failed = it.failed + part.id) } },
        )
        return get(_state.value)
    }
}
