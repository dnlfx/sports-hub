package com.sportshub.app.ui.athlete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.model.CareerTable
import com.sportshub.app.model.PlayerProfile
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Sport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerProfileUiState(
    /** What the screen that opened the player knew, for an instant header. */
    val ref: PlayerRef?,
    val profile: PlayerProfile? = null,
    val career: List<CareerTable>? = null,
    val profileFailed: Boolean = false,
    val careerFailed: Boolean = false,
)

class PlayerProfileViewModel(
    private val repository: TeamRepository,
    private val sport: Sport,
    private val athleteId: String,
    ref: PlayerRef?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlayerProfileUiState(
            ref = ref,
            profile = repository.peekPlayer(sport, athleteId),
            career = repository.peekCareer(sport, athleteId),
        ),
    )
    val state: StateFlow<PlayerProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _state.update { it.copy(profileFailed = false, careerFailed = false) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            if (_state.value.profile == null) {
                repository.cachedPlayer(sport, athleteId)?.let { p -> _state.update { it.copy(profile = it.profile ?: p) } }
            }
            repository.player(sport, athleteId).fold(
                onSuccess = { p -> _state.update { it.copy(profile = if (it.profile == p) it.profile else p, profileFailed = false) } },
                onFailure = { _state.update { it.copy(profileFailed = it.profile == null) } },
            )
        }
        viewModelScope.launch {
            if (_state.value.career == null) {
                repository.cachedCareer(sport, athleteId)?.let { c -> _state.update { it.copy(career = it.career ?: c) } }
            }
            repository.career(sport, athleteId).fold(
                onSuccess = { c -> _state.update { it.copy(career = if (it.career == c) it.career else c, careerFailed = false) } },
                onFailure = { _state.update { it.copy(careerFailed = it.career == null) } },
            )
        }
    }
}
