package com.sportshub.app.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SlateRequest
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScoresUiState(
    val sport: Sport = Sport.default,
    val board: Scoreboard? = null,
    /** Week the user picked; null means the current week. */
    val selectedWeek: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class ScoresViewModel(private val repository: ScoresRepository) : ViewModel() {

    private val _state = MutableStateFlow(ScoresUiState())
    val state: StateFlow<ScoresUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun setSport(sport: Sport) {
        if (sport == _state.value.sport && _state.value.board != null) return
        _state.value = ScoresUiState(sport = sport)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Show the last saved scores straight away, then refresh in place.
            repository.cachedScoreboard(sport)?.let { cached ->
                _state.update { it.copy(board = cached, isLoading = false) }
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

    /** Called while the Scores screen is visible. Refreshes fast during live games, slowly otherwise. */
    suspend fun pollWhileVisible() {
        while (true) {
            val anyLive = _state.value.board?.games?.any { it.isLive } == true
            delay(if (anyLive) LIVE_REFRESH_MS else IDLE_REFRESH_MS)
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
        _state.update { s ->
            if (s.sport != current.sport) return@update s
            result.fold(
                onSuccess = { board -> s.copy(board = board, isLoading = false, isRefreshing = false, error = null) },
                onFailure = {
                    s.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Couldn't reach the scores service. Check your connection.",
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
