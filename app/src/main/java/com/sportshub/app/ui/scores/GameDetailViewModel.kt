package com.sportshub.app.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.model.GameDetail
import com.sportshub.app.model.Sport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val detail: GameDetail? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class GameDetailViewModel(
    private val repository: ScoresRepository,
    private val sport: Sport,
    private val gameId: String,
) : ViewModel() {

    // A game opened a moment ago is still in memory: start from it so the screen's
    // first frame is complete, then refresh in place.
    private val _state = MutableStateFlow(
        repository.peekGameDetail(gameId)?.let { GameDetailUiState(detail = it, isLoading = false) }
            ?: GameDetailUiState(),
    )
    val state: StateFlow<GameDetailUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var lastLoadAtMs = 0L

    init {
        loadJob = viewModelScope.launch { load() }
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    /** Refreshes while the screen is visible: every 20 seconds during a live game, every 2 minutes otherwise. */
    suspend fun pollWhileVisible() {
        while (true) {
            // Let a load that's already running (like the first one) finish first.
            loadJob?.join()
            val interval = if (_state.value.detail?.game?.isLive == true) LIVE_REFRESH_MS else IDLE_REFRESH_MS
            delay(nextPollDelayMs(interval, System.currentTimeMillis() - lastLoadAtMs))
            if (loadJob?.isActive != true) load()
        }
    }

    private suspend fun load() {
        val result = repository.gameDetail(sport, gameId)
        lastLoadAtMs = System.currentTimeMillis()
        _state.update { s ->
            result.fold(
                onSuccess = { s.copy(detail = reuseUnchanged(s.detail, it), isLoading = false, isRefreshing = false, error = null) },
                onFailure = {
                    s.copy(isLoading = false, isRefreshing = false, error = "Couldn't load this game. Check your connection.")
                },
            )
        }
    }

    /**
     * Each refresh parses a brand-new copy of the game. Parts that didn't change keep
     * their old objects, so Compose skips redrawing them (the box score is big, and
     * during a live game most of it is the same from one refresh to the next).
     */
    private fun reuseUnchanged(old: GameDetail?, new: GameDetail): GameDetail {
        if (old == null) return new
        if (old == new) return old
        return GameDetail(
            game = keepIfEqual(old.game, new.game),
            teamStats = keepIfEqual(old.teamStats, new.teamStats),
            leaders = keepIfEqual(old.leaders, new.leaders),
            playerStats = new.playerStats.map { team ->
                val before = old.playerStats.firstOrNull { it.teamId == team.teamId }
                if (before == null || before == team) before ?: team
                else team.copy(tables = team.tables.map { t -> keepIfEqual(before.tables.firstOrNull { it.key == t.key }, t) })
            },
            winProbability = keepIfEqual(old.winProbability, new.winProbability),
        )
    }

    private companion object {
        const val LIVE_REFRESH_MS = 20_000L
        const val IDLE_REFRESH_MS = 120_000L
    }
}
