package com.sportshub.app

import com.sportshub.app.data.espn.EspnTeamParser
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnTeamParserTest {
    private fun resource(name: String) =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    @Test fun parsesTeamList() {
        val teams = EspnTeamParser.parseTeams(resource("espn_teams.json"))
        assertEquals(listOf("2", "15"), teams.map { it.team.id })
        assertEquals("Buffalo Bills", teams.first().displayName)
        assertEquals("Bills", teams.first().team.name)
        assertEquals("#00338d", teams.first().team.primaryColor)
        assertEquals("https://a.espncdn.com/i/teamlogos/nfl/500/buf.png", teams.first().team.logoUrl)
    }

    @Test fun parsesProfile() {
        val profile = EspnTeamParser.parseTeamProfile(resource("espn_team.json"))!!
        assertEquals("4-1", profile.team.record)
        assertEquals("1st in AFC East", profile.standing)
        assertEquals(listOf("Home" to "2-1", "Away" to "2-0"), profile.splits.map { it.label to it.summary })
        assertEquals(27.4, profile.pointsForPerGame!!, 0.01)
        assertEquals(41, profile.pointDifferential)
        assertEquals(3, profile.streak)
    }

    @Test fun parsesSchedule() {
        val schedule = EspnTeamParser.parseSchedule(Sport.NFL, resource("espn_team_schedule.json"))
        assertEquals(2026, schedule.season)
        assertEquals(listOf("401", "402"), schedule.entries.map { it.game.id })
        val first = schedule.entries.first()
        assertEquals(1, first.week)
        assertEquals("Week 1", first.label)
        assertTrue(first.game.status is GameStatus.Final)
        assertEquals(31, first.game.home.score)
        assertEquals("1-0", first.game.home.team.record)
        assertEquals(GameStatus.Scheduled, schedule.entries[1].game.status)
    }

    @Test fun parsesSeasonStatsIntoGroups() {
        val stats = EspnTeamParser.parseTeamStatistics(resource("espn_team_stats.json"))
        assertEquals(listOf("offense", "defense"), stats.groups.map { it.key })
        val passing = stats.groups.first().stats.first { it.key == "netPassingYards" }
        assertEquals("3,683", passing.value)
        assertEquals("216.6", passing.perGame)
        // Percentages have no per-game figure.
        assertNull(stats.groups.first().stats.first { it.key == "completionPct" }.perGame)
        assertTrue(stats.groups[1].stats.any { it.label == "Interceptions" })
    }

    @Test fun parsesRosterAndDepth() {
        val roster = EspnTeamParser.parseRoster(resource("espn_roster.json"))
        assertEquals(listOf("Offense", "Defense"), roster.map { it.title })
        val qb = roster.first().players.single()
        assertEquals("Josh Allen", qb.name)
        assertEquals("QB", qb.position)
        assertEquals(9, qb.experience)
        assertEquals("Wyoming", qb.college)
        assertNull(qb.injury)
        assertEquals("Questionable", roster[1].players.single().injury)

        val depth = EspnTeamParser.parseDepthCharts(resource("espn_depth.json"))
        // Offense sorts first, though ESPN listed defense first.
        assertEquals(listOf("3WR 1TE", "Base 3-4 D"), depth.map { it.name })
        assertEquals("QB", depth.first().positions.first().abbreviation)
        assertEquals(listOf("Josh Allen", "Mitchell Trubisky"), depth.first().positions.first().players.map { it.name })
        assertEquals("Weakside Linebacker", depth[1].positions.first().name)
    }
}
