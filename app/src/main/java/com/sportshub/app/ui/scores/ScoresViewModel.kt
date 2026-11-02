package com.sportshub.app.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.FavoritesRepository
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SlateRequest
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScoresUiState(
    val sport: Sport = Sport.default,
    val board: Scoreboard? = null,
    /** The board's games, grouped for display. Built once per update, not on every frame. */
    val sections: List<GameSection> = emptyList(),
    /** Week the user picked; null means the current week. */
    val selectedWeek: Int? = null,
    /** This week's number, so the week strip can mark it while browsing other weeks. */
    val currentWeek: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class ScoresViewModel(
    private val repository: ScoresRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(startState(Sport.default))
    val state: StateFlow<ScoresUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var lastFetchAtMs = 0L

    init {
        // Following or unfollowing a team moves its games in or out of "Your teams" straight away.
        viewModelScope.launch {
            favorites.teams.drop(1).collect {
                _state.update { s -> s.board?.let { board -> s.copy(sections = sectionsFor(board, s.sport)) } ?: s }
            }
        }
    }

    private fun sectionsFor(board: Scoreboard, sport: Sport): List<GameSection> =
        sectionsOf(board, favoriteIds = favorites.teams(sport).mapTo(HashSet()) { it.id })

    /** Scores already in memory, shown on the first frame instead of a loading placeholder. */
    private fun startState(sport: Sport): ScoresUiState =
        repository.peekScoreboard(sport)?.let { board ->
            ScoresUiState(sport = sport, board = board, sections = sectionsFor(board, sport), currentWeek = board.week, isLoading = false)
        } ?: ScoresUiState(sport = sport)

    fun setSport(sport: Sport) {
        if (sport == _state.value.sport && _state.value.board != null) {
            // Showing scores from memory: still bring them up to date once.
            if (lastFetchAtMs == 0L && loadJob == null) loadJob = viewModelScope.launch { fetch(showSpinner = false) }
            return
        }
        _state.value = startState(sport)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Show the last saved scores straight away, then refresh in place.
            repository.cachedScoreboard(sport)?.let { cached ->
                _state.update {
                    it.copy(board = cached, sections = sectionsFor(cached, sport), currentWeek = cached.week, isLoading = false)
                }
            }
            fetch(showSpinner = false)
        }
    }

    fun selectWeek(week: Int) {
        val board = _state.value.board
        val target = if (board != null && board.week == week && _state.value.selectedWeek == null) null else week
        _state.update { it.copy(selectedWeek = target, isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(showSpinner = false) }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { fetch(showSpinner = true) }
    }

    /**
     * Called while the Scores screen is visible. Refreshes fast during live games and
     * slowly otherwise. Coming back to the screen after a while refreshes right away.
     */
    suspend fun pollWhileVisible() {
        while (true) {
            // Let a load that's already running (like the first one at launch) finish first.
            loadJob?.join()
            val anyLive = _state.value.board?.games?.any { it.isLive } == true
            val interval = if (anyLive) LIVE_REFRESH_MS else IDLE_REFRESH_MS
            delay(nextPollDelayMs(interval, System.currentTimeMillis() - lastFetchAtMs))
            if (loadJob?.isActive != true) fetch(showSpinner = false)
        }
    }

    private suspend fun fetch(showSpinner: Boolean) {
        val current = _state.value
        if (showSpinner) _state.update { it.copy(isRefreshing = true) }
        val request = current.selectedWeek?.let { week ->
            SlateRequest(season = current.board?.season, seasonType = current.board?.seasonType, week = week)
        } ?: SlateRequest()
        val result = repository.scoreboard(current.sport, request)
        lastFetchAtMs = System.currentTimeMillis()
        _state.update { s ->
            if (s.sport != current.sport || s.selectedWeek != current.selectedWeek) return@update s
            result.fold(
                onSuccess = { board ->
                    s.copy(
                        board = board,
                        sections = sectionsFor(board, s.sport),
                        currentWeek = if (request.isCurrent) board.week else s.currentWeek,
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                    )
                },
                onFailure = {
                    s.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Couldn't reach the scores service.",
                    )
                },
            )
        }
    }

    companion object {
        const val LIVE_REFRESH_MS = 15_000L
        const val IDLE_REFRESH_MS = 120_000L
    }
}
