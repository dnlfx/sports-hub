package com.sportshub.app

import android.content.Context
import com.sportshub.app.data.PlaceholderScoresRepository
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.model.Sport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Simple manual dependency container, created once in [SportsHubApp]. */
class AppContainer(context: Context) {
    val scoresRepository: ScoresRepository = PlaceholderScoresRepository()
    val sourcesRepository = SourcesRepository(context.applicationContext)

    private val _selectedSport = MutableStateFlow(Sport.default)
    val selectedSport: StateFlow<Sport> = _selectedSport.asStateFlow()

    fun selectSport(sport: Sport) {
        if (sport.isAvailable) _selectedSport.value = sport
    }
}
