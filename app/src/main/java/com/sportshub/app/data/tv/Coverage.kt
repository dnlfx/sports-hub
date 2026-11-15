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
 * games are assigned the way the networks do it, in this order:
 *  1. A team's home markets always get its game.
 *  2. A team's fan region ([NflRegions]) gets its game.
 *  3. A market whose own team isn't playing that afternoon gets a division rival's game from within
 *     [RIVAL_MILES] (Pittsburgh and Cincinnati get the Browns).
 *  4. Everywhere else gets the best mix of a near game and a big draw (the teams' national
 *     following plus their records), so the network's featured game reaches far and the
 *     others stay closer to home.
 * A game alone on its network in its window goes everywhere. Checked against two weeks of
 * real local listings (CoverageAccuracyTest): about 9 in 10 market maps right on the week
 * the rules were tuned on, 3 in 4 on a week they weren't. The networks decide which game
 * to feature on more than ratings and records, which is most of the misses.
 *
 * The network with Sunday's doubleheader starts its late games at 4:25 PM ET; the other
 * network's late games start at 4:05 and each market gets only one of its games that day,
 * early or late, so its two windows are mapped as one "single game" window. Markets get
 * an early game, unless a late game is theirs by the rules above, or their own team plays
 * early on another network, which the single-game network doesn't go against.
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

        // Teams playing on Sunday afternoon on any broadcast network: their markets skip the rival rule then.
        val sundayTeams = broadcast.filter { it.isSundayAfternoon() }.flatMap(::teams).toSet()

        // Broadcast networks: Sunday afternoons by early and late window, everything else by start time.
        broadcast.groupBy { Networks.overTheAir(it.broadcasts)!! }.forEach { (network, list) ->
            val (sundayAfternoon, rest) = list.partition { it.isSundayAfternoon() }
            if (sundayAfternoon.isNotEmpty()) {
                val (early, late) = sundayAfternoon.partition { it.easternMinutes() < 15 * 60 }
                val doubleheader = late.any { it.easternMinutes() >= 16 * 60 + 15 }
                if (doubleheader) {
                    if (early.isNotEmpty()) windows += broadcastWindow(network, "$network early", early, markets, playingThen = sundayTeams)
                    windows += broadcastWindow(network, "$network late", late, markets, playingThen = sundayTeams)
                } else {
                    // Teams playing early on another network: their markets get this network's late game.
                    val busyEarly = broadcast.filter {
                        it.isSundayAfternoon() && it.easternMinutes() < 15 * 60 && Networks.overTheAir(it.broadcasts) != network
                    }.flatMap { listOf(it.home.team.abbreviation, it.away.team.abbreviation) }.toSet()
                    windows += broadcastWindow(network, "$network single game", sundayAfternoon, markets, busyEarly, sundayTeams)
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

    private fun broadcastWindow(
        network: String,
        title: String,
        games: List<Game>,
        markets: List<TvMarket>,
        busyEarly: Set<String>? = null,
        playingThen: Set<String> = emptySet(),
    ): CoverageWindow {
        val sorted = games.sortedBy { it.startTime }
        val airing = when {
            sorted.size == 1 -> markets.associate { it.id to sorted[0].id }
            busyEarly != null -> markets.associate { it.id to pickSingleGame(it, sorted, busyEarly, playingThen).id }
            else -> markets.associate { it.id to pick(it, sorted, playingThen).id }
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

    /** How close a division rival's game has to be for a market whose team isn't playing to get it. */
    const val RIVAL_MILES = 250.0

    /**
     * How strongly each team draws viewers outside its own region, 1 to 10. Teams not
     * listed count as 4. Based on the teams' usual national TV ratings; tune as needed.
     */
    private val nationalDraw: Map<String, Double> = mapOf(
        "DAL" to 10.0, "KC" to 9.0, "PHI" to 8.0, "GB" to 8.0, "PIT" to 8.0, "SF" to 8.0,
        "BUF" to 7.0, "DET" to 7.0, "BAL" to 6.0, "NE" to 6.0, "CHI" to 6.0, "NYG" to 6.0,
        "LAR" to 5.0, "LA" to 5.0, "MIN" to 5.0, "CIN" to 5.0, "DEN" to 5.0, "SEA" to 5.0,
        "MIA" to 5.0, "LV" to 5.0, "NYJ" to 5.0, "WSH" to 5.0, "WAS" to 5.0,
    )

    /** Winning share of a record like "7-2" or "6-3-1" (ties count half), or 0.5 if unknown. */
    internal fun winShare(record: String?): Double {
        val parts = record?.split('-')?.mapNotNull { it.trim().toIntOrNull() } ?: return 0.5
        if (parts.size < 2) return 0.5
        val wins = parts[0] + (parts.getOrElse(2) { 0 } / 2.0)
        val played = parts.sum()
        return if (played == 0) 0.5 else wins / played
    }

    /** How big a draw [game] is nationally: both teams' following, plus up to 4 for their records. */
    internal fun appeal(game: Game): Double = listOf(game.home.team, game.away.team).sumOf { team ->
        (nationalDraw[team.abbreviation.uppercase()] ?: 4.0) + 4 * winShare(team.record)
    }

    /** The window's featured game: the biggest draw, earliest first on a tie. */
    internal fun featured(games: List<Game>): Game =
        games.sortedBy { it.startTime }.maxBy(::appeal)

    private fun teams(game: Game) = listOf(game.home.team.abbreviation, game.away.team.abbreviation)

    private fun closest(market: TvMarket, game: Game): Double =
        teams(game).mapNotNull { NflHomes.milesTo(market, it) }.minOrNull() ?: Double.MAX_VALUE

    /**
     * The game that's [market]'s own among [games]: its team's, its fan region's, or a nearby
     * division rival's when its own team isn't playing. Null when none is.
     */
    internal fun localGame(market: TvMarket, games: List<Game>, playingThen: Set<String> = emptySet()): Game? {
        val home = games.filter { g -> teams(g).any { NflHomes.isHome(market, it) } }
        if (home.isNotEmpty()) return home.minBy { closest(market, it) }
        val regional = games.filter { g -> teams(g).any { NflRegions.isRegional(market, it) } }
        if (regional.isNotEmpty()) return regional.minBy { closest(market, it) }
        val own = NflHomes.homeTeams(market)
        if (own.any { it in playingThen }) return null
        return games
            .filter { g ->
                teams(g).any { t ->
                    own.any { NflRegions.rivals(it, t) } && (NflHomes.milesTo(market, t) ?: Double.MAX_VALUE) <= RIVAL_MILES
                }
            }
            .minByOrNull { closest(market, it) }
    }

    /**
     * How much a bigger draw outweighs distance when a market has no game of its own: each
     * game reaches out in proportion to its appeal raised to this power. 0 would give every
     * market the closest game, a very large number the featured game everywhere.
     */
    private const val PULL = 3.0

    /** The game a market with no game of its own gets: the best mix of near and big. */
    internal fun reach(market: TvMarket, games: List<Game>): Game =
        games.sortedBy { it.startTime }.minBy { closest(market, it) / Math.pow(appeal(it), PULL) }

    /** The game [market] gets among several on one network at once: its own game, else the best reach. */
    internal fun pick(
        market: TvMarket,
        games: List<Game>,
        playingThen: Set<String> = emptySet(),
    ): Game = localGame(market, games, playingThen) ?: reach(market, games)

    /**
     * The game [market] gets from the network with one game per market on Sunday afternoon:
     * its own game early or late; a late game when its own team is busy early on the other
     * network ([busyEarly]); else an early game.
     */
    internal fun pickSingleGame(market: TvMarket, games: List<Game>, busyEarly: Set<String>, playingThen: Set<String>): Game {
        localGame(market, games, playingThen)?.let { return it }
        val (early, late) = games.partition { it.easternMinutes() < 15 * 60 }
        return when {
            NflHomes.homeTeams(market).any { it in busyEarly } && late.isNotEmpty() -> reach(market, late)
            early.isNotEmpty() -> reach(market, early)
            else -> reach(market, late)
        }
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
