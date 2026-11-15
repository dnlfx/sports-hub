package com.sportshub.app.data.tv

import androidx.compose.runtime.Immutable
import com.sportshub.app.model.Game
import com.sportshub.app.model.Scoreboard
import com.sportshub.app.model.Sport
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * One TV window: the games a network carries at the same time, and which of them each
 * market gets. On a Sunday afternoon CBS and FOX each split the country between several
 * games, one map per network and window, the way the weekly NFL coverage maps are drawn.
 */
@Immutable
data class CoverageWindow(
    val key: String,
    /** "CBS", "FOX", "NBC", or the cable or streaming service for [Kind.LocalOnly]. */
    val network: String,
    /** E.g. "CBS early", "FOX single game", "Sunday night". */
    val title: String,
    val start: Instant,
    val kind: Kind,
    val games: List<Game>,
    /** Market id to the id of the game that market sees over the air in this window. */
    val airing: Map<String, String>,
) {
    enum class Kind {
        /** Several games on one broadcast network, each shown in part of the country. */
        Regional,

        /** One game on a broadcast network, everywhere. */
        National,

        /** On cable or streaming; over the air only on local stations in the teams' home markets. */
        LocalOnly,
    }

    /** Share of the country's TV homes that get [gameId] over the air in this window, 0 to 1. */
    fun share(gameId: String): Double {
        val homes = airing.entries.sumOf { (market, game) ->
            if (game == gameId) TvMarkets.byId(market)?.tvHomes ?: 0 else 0
        }
        return homes.toDouble() / TvMarkets.totalHomes
    }

    /** Markets that get [gameId], biggest first. */
    fun marketsFor(gameId: String): List<TvMarket> =
        airing.filterValues { it == gameId }.keys.mapNotNull(TvMarkets::byId).sortedByDescending { it.tvHomes }
}

/** A week of TV windows. */
@Immutable
data class CoverageWeek(val windows: List<CoverageWindow>) {
    /** Ids of the games [market] gets over the air, on a broadcast network or a local station. */
    fun overTheAir(market: TvMarket): Set<String> =
        windows.mapNotNullTo(HashSet()) { it.airing[market.id] }

    /** Ids of the games on a broadcast network everywhere, for when the user's market isn't known. */
    fun nationalOverTheAir(): Set<String> =
        windows.filter { it.kind == CoverageWindow.Kind.National }.flatMapTo(HashSet()) { w -> w.games.map { it.id } }
}

/**
 * Works out the TV coverage of a week of games from their networks and kickoff times.
 *
 * ESPN's data says which network has each game but not which markets get it, so regional
 * games are assigned the way the networks do it first, then by proximity:
 *  1. A team's home markets always get its game.
 *  2. Every other market gets the game whose team plays closest to it.
 * A game alone on its network in its window goes everywhere. The networks' real maps
 * also weigh which game is the bigger draw, so they can differ outside home markets.
 *
 * The network with Sunday's doubleheader starts its late games at 4:25 PM ET; the other
 * network's late games start at 4:05 and each market gets only one of its games that day,
 * early or late, so its two windows are mapped as one "single game" window.
 */
object Coverage {
    private val eastern: ZoneId = ZoneId.of("America/New_York")

    /**
     * Ids of the games on [board] the user gets for free over the air: in the NFL, the ones
     * their [market] gets (or only the national ones when it isn't set); in other sports, the
     * ones alone on a broadcast network.
     */
    fun overTheAir(board: Scoreboard, market: TvMarket?): Set<String> =
        if (board.sport == Sport.NFL) {
            nflWeek(board.games).let { week -> market?.let(week::overTheAir) ?: week.nationalOverTheAir() }
        } else {
            nationalOverTheAir(board.games)
        }

    fun nflWeek(games: List<Game>, markets: List<TvMarket> = TvMarkets.all): CoverageWeek {
        val windows = mutableListOf<CoverageWindow>()
        val withNetwork = games.filter { it.broadcasts.isNotEmpty() }
        val (broadcast, other) = withNetwork.partition { Networks.overTheAir(it.broadcasts) != null }

        // Broadcast networks: Sunday afternoons by early and late window, everything else by start time.
        broadcast.groupBy { Networks.overTheAir(it.broadcasts)!! }.forEach { (network, list) ->
            val (sundayAfternoon, rest) = list.partition { it.isSundayAfternoon() }
            if (sundayAfternoon.isNotEmpty()) {
                val (early, late) = sundayAfternoon.partition { it.easternMinutes() < 15 * 60 }
                val doubleheader = late.any { it.easternMinutes() >= 16 * 60 + 15 }
                if (doubleheader) {
                    if (early.isNotEmpty()) windows += broadcastWindow(network, "$network early", early, markets)
                    windows += broadcastWindow(network, "$network late", late, markets)
                } else {
                    windows += broadcastWindow(network, "$network single game", sundayAfternoon, markets)
                }
            }
            rest.groupBy { it.slotKey() }.values.forEach { slot ->
                windows += broadcastWindow(network, slot.first().slotTitle(network), slot, markets)
            }
        }

        // Cable and streaming: on a local station in each team's home markets only.
        other.groupBy { it.broadcasts.first() to it.slotKey() }.forEach { (key, slot) ->
            val network = key.first
            val airing = buildMap {
                slot.forEach { game ->
                    markets.forEach { m ->
                        if (NflHomes.isHome(m, game.home.team.abbreviation) || NflHomes.isHome(m, game.away.team.abbreviation)) {
                            putIfAbsent(m.id, game.id)
                        }
                    }
                }
            }
            windows += CoverageWindow(
                key = "$network-${slot.first().slotKey()}",
                network = network,
                title = slot.first().slotTitle(network),
                start = slot.minOf { it.startTime },
                kind = CoverageWindow.Kind.LocalOnly,
                games = slot.sortedBy { it.startTime },
                airing = airing,
            )
        }
        return CoverageWeek(windows.sortedWith(compareBy<CoverageWindow> { it.start }.thenBy { it.kind }.thenBy { it.network }))
    }

    private fun broadcastWindow(network: String, title: String, games: List<Game>, markets: List<TvMarket>): CoverageWindow {
        val sorted = games.sortedBy { it.startTime }
        val airing = if (sorted.size == 1) {
            markets.associate { it.id to sorted[0].id }
        } else {
            markets.associate { it.id to pick(it, sorted).id }
        }
        return CoverageWindow(
            key = "$network-${sorted.first().startTime.epochSecond}-${sorted.size}-$title",
            network = network,
            title = title,
            start = sorted.first().startTime,
            kind = if (sorted.size == 1) CoverageWindow.Kind.National else CoverageWindow.Kind.Regional,
            games = sorted,
            airing = airing,
        )
    }

    /** The game [market] gets among several on one network: its own team's, else the closest team's. */
    internal fun pick(market: TvMarket, games: List<Game>): Game {
        fun closest(game: Game): Double = listOfNotNull(
            NflHomes.milesTo(market, game.home.team.abbreviation),
            NflHomes.milesTo(market, game.away.team.abbreviation),
        ).minOrNull() ?: Double.MAX_VALUE
        val home = games.filter {
            NflHomes.isHome(market, it.home.team.abbreviation) || NflHomes.isHome(market, it.away.team.abbreviation)
        }
        return (home.ifEmpty { games }).minBy(::closest)
    }

    /**
     * Games on a broadcast network everywhere, for sports without market-by-market coverage:
     * a game counts when it's the only one on its network starting within the same hour.
     * Regional splits (several games on one network at once) are left out, since which
     * one a market gets isn't known.
     */
    fun nationalOverTheAir(games: List<Game>): Set<String> {
        val onNetwork = games.mapNotNull { g -> Networks.overTheAir(g.broadcasts)?.let { it to g } }
        return onNetwork.filter { (network, game) ->
            onNetwork.none { (n, other) ->
                other.id != game.id && n == network &&
                    kotlin.math.abs(other.startTime.epochSecond - game.startTime.epochSecond) < 60 * 60
            }
        }.mapTo(HashSet()) { it.second.id }
    }

    private fun Game.eastern() = startTime.atZone(eastern)
    private fun Game.easternMinutes(): Int = eastern().let { it.hour * 60 + it.minute }
    private fun Game.isSundayAfternoon(): Boolean =
        eastern().dayOfWeek == DayOfWeek.SUNDAY && easternMinutes() in (12 * 60) until (19 * 60)

    /** Games in the same slot start on the same day within the same hour. */
    private fun Game.slotKey(): String = eastern().let { "${it.toLocalDate()}T${it.hour}" }

    /** "Sunday night", "Thursday night", "Saturday 4:30 PM", "Sunday morning". */
    private fun Game.slotTitle(network: String): String {
        val time = eastern()
        val day = time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        return when {
            time.hour >= 19 -> "$day night · $network"
            time.hour < 12 -> "$day morning · $network"
            else -> {
                val hour = if (time.hour > 12) time.hour - 12 else time.hour
                val clock = if (time.minute == 0) "$hour PM" else "$hour:${time.minute.toString().padStart(2, '0')} PM"
                "$day $clock ET · $network"
            }
        }
    }
}
