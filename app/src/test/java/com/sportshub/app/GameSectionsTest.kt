package com.sportshub.app

import com.sportshub.app.data.espn.EspnParser
import com.sportshub.app.model.Competitor
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import com.sportshub.app.ui.scores.sectionsOf
import com.sportshub.app.ui.scores.statShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class GameSectionsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 1)

    private fun game(id: String, status: GameStatus, day: LocalDate = today, hour: Int = 18) = Game(
        id = id,
        sport = Sport.NFL,
        startTime = ZonedDateTime.of(day.atTime(hour, 0), zone).toInstant(),
        status = status,
        home = Competitor(Team("h$id", "Home", "HOM"), score = null),
        away = Competitor(Team("a$id", "Away", "AWY"), score = null),
    )

    private fun board(vararg games: Game) = Scoreboard(Sport.NFL, 2026, 2, 9, games.toList(), 0L)

    @Test fun groupsLiveThenUpcomingByDayThenFinal() {
        val sections = sectionsOf(
            board(
                game("f", GameStatus.Final()),
                game("mon", GameStatus.Scheduled, today.plusDays(1)),
                game("thu", GameStatus.Scheduled, today.plusDays(4)),
                game("sun", GameStatus.Scheduled, today),
                game("live", GameStatus.Live(2, "4:12", "Q2 4:12")),
                game("ppd", GameStatus.Postponed("Postponed")),
            ),
            zone = zone,
            today = today,
        )
        assertEquals(
            listOf("Live now", "Today", "Tomorrow", "Thursday, Nov 5", "Final", "Postponed"),
            sections.map { it.title },
        )
        assertEquals(listOf("live"), sections[0].games.map { it.id })
        // Section keys must be unique: they become lazy list keys.
        assertEquals(sections.size, sections.map { it.key }.toSet().size)
    }

    @Test fun emptyBoardHasNoSections() {
        assertEquals(emptyList<Any>(), sectionsOf(board(), zone = zone, today = today))
    }

    @Test fun fixtureBoardKeepsEveryGame() {
        val json = requireNotNull(javaClass.classLoader?.getResource("espn_scoreboard.json")).readText()
        val parsed = EspnParser.parseScoreboard(Sport.NFL, json)
        assertEquals(parsed.games.size, sectionsOf(parsed).sumOf { it.games.size })
    }

    @Test fun statShareComparesNumbersPercentagesAndClockTimes() {
        assertEquals(0.25f, statShare("100", "300")!!, 0.001f)
        assertEquals(0.5f, statShare("45%", "45%")!!, 0.001f)
        assertEquals(0.5f, statShare("1,200", "1,200")!!, 0.001f)
        // Possession 40:00 vs 20:00
        assertEquals(2f / 3f, statShare("40:00", "20:00")!!, 0.001f)
    }

    @Test fun statShareSkipsValuesThatDontCompare() {
        assertNull(statShare("5-12", "7-13"))
        assertNull(statShare("0", "0"))
        assertNull(statShare("-", "12"))
    }
}
