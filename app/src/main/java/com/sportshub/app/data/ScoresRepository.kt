package com.sportshub.app.data

import com.sportshub.app.model.Game
import com.sportshub.app.model.Sport
import java.time.LocalDate

/**
 * Where scores come from. Kept behind an interface so the data provider can be
 * swapped (ESPN public data first, a licensed feed later) without touching the UI.
 */
interface ScoresRepository {
    suspend fun gamesOn(sport: Sport, date: LocalDate): Result<List<Game>>
}

/** Stand-in until step 2 wires up the live provider. Returns no games. */
class PlaceholderScoresRepository : ScoresRepository {
    override suspend fun gamesOn(sport: Sport, date: LocalDate): Result<List<Game>> =
        Result.success(emptyList())
}
