package com.sportshub.app.ui.coverage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SlateRequest
import com.sportshub.app.data.tv.Coverage
import com.sportshub.app.data.tv.CoverageWeek
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoverageUiState(
    val season: Int?,
    val seasonType: Int?,
    val week: Int?,
    val coverage: CoverageWeek? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** The TV windows of one NFL week, worked out from that week's scoreboard. */
class CoverageViewModel(
    private val repository: ScoresRepository,
    season: Int?,
    seasonType: Int?,
    week: Int?,
) : ViewModel() {

    private val _state = MutableStateFlow(start(season, seasonType, week))
    val state: StateFlow<CoverageUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    /** The week already in memory from the Scores tab, drawn on the first frame. */
    private fun start(season: Int?, seasonType: Int?, week: Int?): CoverageUiState {
        val board = repository.peekScoreboard(Sport.NFL)?.takeIf { it.week == week && it.season == season && it.seasonType == seasonType }
        return CoverageUiState(
            season = season,
            seasonType = seasonType,
            week = week,
            coverage = board?.let { Coverage.nflWeek(it.games) },
            isLoading = board == null,
        )
    }

    /** Weeks in the slate the page is showing: the regular season's 18, or the playoffs' rounds. */
    val lastWeek: Int get() = if (_state.value.seasonType == 3) 5 else Sport.NFL.regularSeasonWeeks

    fun showWeek(week: Int) {
        if (week < 1 || week > lastWeek || week == _state.value.week) return
        _state.update { it.copy(week = week, isLoading = true, error = null) }
        load()
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        load()
    }

    private fun load() {
        val s = _state.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val request = if (s.week == null) SlateRequest() else SlateRequest(season = s.season, seasonType = s.seasonType, week = s.week)
            val result = repository.scoreboard(Sport.NFL, request)
            val coverage = result.getOrNull()?.let { board -> withContext(Dispatchers.Default) { Coverage.nflWeek(board.games) } }
            _state.update { current ->
                if (current.week != s.week) return@update current
                val board: Scoreboard? = result.getOrNull()
                if (board != null && coverage != null) {
                    current.copy(
                        season = board.season,
                        seasonType = board.seasonType,
                        week = board.week ?: current.week,
                        coverage = coverage,
                        isLoading = false,
                        error = null,
                    )
                } else {
                    current.copy(isLoading = false, error = "Couldn't reach the scores service.")
                }
            }
        }
    }
}
