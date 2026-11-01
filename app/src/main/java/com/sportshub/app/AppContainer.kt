package com.sportshub.app

import android.content.Context
import com.sportshub.app.data.EspnScoresRepository
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.model.Sport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Simple manual dependency container, created once in [SportsHubApp]. */
class AppContainer(context: Context) {
    val scoresRepository: ScoresRepository = EspnScoresRepository(context.applicationContext)
    val sourcesRepository = SourcesRepository(context.applicationContext)

    /** The game the user just tapped, so its detail screen can draw the header before loading. */
    var lastOpenedGame: com.sportshub.app.model.Game? = null

    private val _selectedSport = MutableStateFlow(Sport.default)
    val selectedSport: StateFlow<Sport> = _selectedSport.asStateFlow()

    fun selectSport(sport: Sport) {
        if (sport.isAvailable) _selectedSport.value = sport
    }
}
