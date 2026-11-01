package com.sportshub.app.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.model.GameDetail
import com.sportshub.app.model.Sport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val detail: GameDetail? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class GameDetailViewModel(
    private val repository: ScoresRepository,
    private val sport: Sport,
    private val gameId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(GameDetailUiState())
    val state: StateFlow<GameDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch { load() }
    }

    suspend fun pollWhileVisible() {
        while (true) {
            val live = _state.value.detail?.game?.isLive == true
            delay(if (live) 20_000L else 120_000L)
            load()
        }
    }

    private suspend fun load() {
        val result = repository.gameDetail(sport, gameId)
        _state.update { s ->
            result.fold(
                onSuccess = { s.copy(detail = it, isLoading = false, error = null) },
                onFailure = { s.copy(isLoading = false, error = "Couldn't load this game. Check your connection.") },
            )
        }
    }
}
