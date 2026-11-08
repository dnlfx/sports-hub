package com.sportshub.app

import com.sportshub.app.data.FavoritesRepository
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.favorites.idleTeams
import com.sportshub.app.ui.scores.YOUR_TEAMS_SECTION
import com.sportshub.app.ui.scores.sectionsOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FavoritesTest {
    private val bills = Team("2", "Bills", "BUF", logoUrl = "https://a/buf.png", primaryColor = "#00338D")
    private val jets = Team("20", "Jets", "NYJ")

    @Test fun savedTeamsReadBack() {
        val saved = mapOf("nfl" to listOf(bills, jets))
        assertEquals(saved, FavoritesRepository.decode(FavoritesRepository.encode(saved)))
    }

    @Test fun badSavedDataReadsAsNoTeams() {
        assertEquals(emptyMap<String, List<Team>>(), FavoritesRepository.decode("not json"))
        assertEquals(emptyMap<String, List<Team>>(), FavoritesRepository.decode(null))
    }

    private fun game(id: String, home: Team, away: Team, status: GameStatus, homeScore: Int? = null, awayScore: Int? = null) = Game(
        id = id,
        sport = Sport.NFL,
        startTime = Instant.parse("2026-11-01T18:00:00Z"),
        status = status,
        home = Competitor(home.copy(record = "6-2"), homeScore, isWinner = status is GameStatus.Final && (homeScore ?: 0) > (awayScore ?: 0)),
        away = Competitor(away.copy(record = "2-6"), awayScore, isWinner = status is GameStatus.Final && (awayScore ?: 0) > (homeScore ?: 0)),
    )

    private val board = Scoreboard(
        Sport.NFL, 2026, 2, 9,
        listOf(
            game("1", bills, Team("15", "Dolphins", "MIA"), GameStatus.Final(), homeScore = 27, awayScore = 20),
            game("2", Team("1", "Falcons", "ATL"), Team("29", "Panthers", "CAR"), GameStatus.Scheduled),
        ),
        0L,
    )

    @Test fun yourTeamsGamesComeFirstAndOnlyOnce() {
        val sections = sectionsOf(board, favoriteIds = setOf("2"))
        assertEquals(YOUR_TEAMS_SECTION, sections.first().key)
        assertEquals(listOf("1"), sections.first().games.map { it.id })
        assertEquals(board.games.size, sections.sumOf { it.games.size })
    }

    @Test fun yourTeamsGamesFollowTheOrderTeamsWereFollowed() {
        val sections = sectionsOf(board, favoriteIds = linkedSetOf("1", "2"))
        assertEquals(listOf("2", "1"), sections.first().games.map { it.id })
    }

    @Test fun onlyTeamsWithoutAGameGetAnIdleRow() {
        val idle = idleTeams(listOf(bills, jets), board)
        assertEquals(listOf("20"), idle.map { it.team.id })
        assertEquals("Bye week", idle.single().status)
    }

    @Test fun everyTeamPlayingMeansNoIdleRows() {
        assertEquals(emptyList<Any>(), idleTeams(listOf(bills), board))
    }
}
