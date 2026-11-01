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

    private val _state = MutableStateFlow(GameDetailUiState())
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
                onSuccess = { s.copy(detail = it, isLoading = false, isRefreshing = false, error = null) },
                onFailure = {
                    s.copy(isLoading = false, isRefreshing = false, error = "Couldn't load this game. Check your connection.")
                },
            )
        }
    }

    private companion object {
        const val LIVE_REFRESH_MS = 20_000L
        const val IDLE_REFRESH_MS = 120_000L
    }
}
