package com.sportshub.app.data.tv

import androidx.compose.runtime.Immutable
import com.sportshub.app.data.espn.EspnJson
import com.sportshub.app.data.espn.arr
import com.sportshub.app.data.espn.int
import com.sportshub.app.data.espn.obj
import com.sportshub.app.data.espn.str
import com.sportshub.app.model.Game
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** A station in the user's antenna lineup, with what it shows in the hours asked for. */
data class ListingChannel(
    val callSign: String,
    /** The number on the TV, e.g. "2.1". */
    val number: String,
    /** The network the station carries, e.g. "CBS", when the guide says. */
    val affiliate: String?,
    val events: List<ListingEvent>,
)

data class ListingEvent(
    val title: String,
    val episodeTitle: String?,
    val description: String?,
    val start: Instant,
    val end: Instant,
)

/** The local station a game is on over the air, from the user's TV guide. */
@Immutable
data class LocalAiring(
    val callSign: String,
    val channel: String,
    val network: String?,
) {
    /** "CBS 2.1", or the call sign when the network isn't known. */
    val label: String get() = "${network ?: callSign} $channel".trim()
}

/** What the user's antenna lineup shows for a set of games. */
@Immutable
data class LocalLineup(
    val zip: String,
    /** Games found in the guide, with the station they're on. */
    val airings: Map<String, LocalAiring>,
    /**
     * Games whose kickoff the guide covered. A game in here but not in [airings] isn't on
     * any station the user gets over the air. Games not in here weren't checked (the guide
     * didn't answer, or doesn't go that far ahead) and keep the estimate.
     */
    val checked: Set<String>,
) {
    companion object {
        val Empty = LocalLineup("", emptyMap(), emptySet())
    }
}

/**
 * Reads the free TV guide at tvlistings.gracenote.com (the guide behind most TV listings
 * sites) for the user's ZIP code and antenna lineup, and finds the NFL games in it.
 */
object LocalListings {
    fun isZip(zip: String): Boolean = zip.length == 5 && zip.all { it.isDigit() }

    /**
     * The guide's grid for the stations an antenna gets in [zip] ("Local Over the Air
     * Broadcast" in the guide's lineup picker), [hours] hours from [start].
     */
    fun gridUrl(zip: String, start: Instant, hours: Int = 6): String =
        "https://tvlistings.gracenote.com/api/grid?lineupId=USA-lineupId-DEFAULT&timespan=$hours" +
            "&headendId=lineupId&country=USA&device=-&postalCode=$zip&time=${start.epochSecond}" +
            "&isOverride=true&pref=-&userId=-&aid=orbebb&languagecode=en-us"

    /** The guide's grid, parsed. Empty when the response isn't one. */
    fun parse(json: String): List<ListingChannel> {
        val root = runCatching { EspnJson.parseToJsonElement(json) }.getOrNull().obj ?: return emptyList()
        return root.arr("channels").orEmpty().mapNotNull { element ->
            val c = element.obj ?: return@mapNotNull null
            val callSign = c.str("callSign")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ListingChannel(
                callSign = callSign,
                number = c.str("channelNo").orEmpty(),
                affiliate = c.str("affiliateName")?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let(::networkName),
                events = c.arr("events").orEmpty().mapNotNull { e -> e.obj?.let(::event) },
            )
        }
    }

    private fun event(e: kotlinx.serialization.json.JsonObject): ListingEvent? {
        val program = e.obj("program")
        val start = instant(e.str("startTime")) ?: return null
        val end = instant(e.str("endTime"))
            ?: e.int("duration")?.let { start.plus(Duration.ofMinutes(it.toLong())) }
            ?: return null
        return ListingEvent(
            title = program?.str("title") ?: e.str("title") ?: return null,
            episodeTitle = program?.str("episodeTitle")?.takeIf { it.isNotBlank() },
            description = program?.str("shortDesc")?.takeIf { it.isNotBlank() },
            start = start,
            end = end,
        )
    }

    private fun instant(text: String?): Instant? =
        text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.recoverCatching { _ -> Instant.parse(it) }.getOrNull() }

    /** "CBS", "FOX", "NBC"... from the guide's affiliate names ("CBS TELEVISION NETWORK", "FOX ENTERTAINMENT"). */
    internal fun networkName(affiliate: String): String {
        val upper = affiliate.uppercase()
        return when {
            upper.startsWith("NATIONAL BROADCASTING") -> "NBC"
            upper.startsWith("AMERICAN BROADCASTING") -> "ABC"
            else -> listOf("CBS", "FOX", "NBC", "ABC", "CW").firstOrNull { upper == it || upper.startsWith("$it ") || upper.startsWith("$it-") }
                ?: affiliate
        }
    }

    /** Whether the grid has listings at [time] at all, so a game missing from it can be trusted to be missing. */
    fun covers(channels: List<ListingChannel>, time: Instant): Boolean =
        channels.any { c -> c.events.any { !it.start.isAfter(time) && it.end.isAfter(time) } }

    /**
     * The station each of [games] is on, among [channels]. A game is matched to a football
     * broadcast on the air at kickoff whose title or description names one of its teams,
     * or, when the guide gives no teams, to the one game on that station's network then.
     */
    fun match(games: List<Game>, channels: List<ListingChannel>): Map<String, LocalAiring> {
        val found = HashMap<String, LocalAiring>()
        for (game in games) {
            var best: Pair<Int, LocalAiring>? = null
            for (channel in channels) {
                for (event in channel.events) {
                    if (!isFootball(event) || !onAtKickoff(event, game.startTime)) continue
                    val text = listOfNotNull(event.title, event.episodeTitle, event.description).joinToString(" ")
                    val named = listOf(game.away.team.name, game.home.team.name).count { mentions(text, it) }
                    val othersNamed = games.any { other ->
                        other.id != game.id && onAtKickoff(event, other.startTime) &&
                            (mentions(text, other.away.team.name) || mentions(text, other.home.team.name))
                    }
                    val score = when {
                        named == 2 -> 3
                        named == 1 && !othersNamed -> 2
                        named == 0 && !othersNamed && soleGameOnNetwork(game, games, channel.affiliate) -> 1
                        else -> 0
                    }
                    if (score > 0 && score > (best?.first ?: 0)) {
                        best = score to LocalAiring(channel.callSign, channel.number, channel.affiliate)
                    }
                }
            }
            best?.let { found[game.id] = it.second }
        }
        return found
    }

    /**
     * Games among [games] the guide hasn't decided yet: one of the user's stations on the
     * game's network has football on at kickoff but doesn't say which game, as happens
     * before the networks set the week's coverage. These keep the estimate.
     */
    fun undecided(games: List<Game>, channels: List<ListingChannel>, matched: Set<String>): Set<String> =
        games.filter { game ->
            if (game.id in matched) return@filter false
            val network = Networks.overTheAir(game.broadcasts) ?: return@filter false
            channels.any { channel ->
                channel.affiliate.equals(network, ignoreCase = true) && channel.events.any { event ->
                    isFootball(event) && onAtKickoff(event, game.startTime) && games.none { other ->
                        val text = listOfNotNull(event.title, event.episodeTitle, event.description).joinToString(" ")
                        mentions(text, other.away.team.name) || mentions(text, other.home.team.name)
                    }
                }
            }
        }.mapTo(HashSet()) { it.id }

    /**
     * The lineup for [games] from [channels] that cover the time ranges [covered]: games
     * kicking off in a covered range count as checked, except the [undecided] ones.
     */
    fun lineup(zip: String, games: List<Game>, channels: List<ListingChannel>, covered: (Instant) -> Boolean): LocalLineup {
        val inRange = games.filter { covered(it.startTime) }
        val airings = match(inRange, channels)
        val open = undecided(inRange, channels, airings.keys)
        return LocalLineup(zip, airings, inRange.mapTo(HashSet()) { it.id } - open)
    }

    /**
     * [channels] trimmed to their football broadcasts, written back in the guide's own
     * format so [parse] reads it: what the app keeps on the phone in case the guide
     * refuses later.
     */
    fun footballOnly(channels: List<ListingChannel>): String = buildJsonObject {
        putJsonArray("channels") {
            for (channel in channels) {
                val football = channel.events.filter(::isFootball)
                addJsonObject {
                    put("callSign", channel.callSign)
                    put("channelNo", channel.number)
                    channel.affiliate?.let { put("affiliateName", it) }
                    putJsonArray("events") {
                        for (event in football) addJsonObject {
                            put("startTime", event.start.toString())
                            put("endTime", event.end.toString())
                            putJsonObject("program") {
                                put("title", event.title)
                                event.episodeTitle?.let { put("episodeTitle", it) }
                                event.description?.let { put("shortDesc", it) }
                            }
                        }
                    }
                }
            }
        }
    }.toString()

    /**
     * The games the user gets for free: the guide's answer for the games it checked, and
     * the [estimate] for the rest.
     */
    fun overTheAir(estimate: Set<String>, lineup: LocalLineup?): Set<String> =
        if (lineup == null) estimate else estimate.filterTo(HashSet()) { it !in lineup.checked } + lineup.airings.keys

    /** "NFL" as a word (not inside "DragonFly"), or "Football". */
    internal fun isFootball(event: ListingEvent): Boolean =
        nflWord.containsMatchIn(event.title) || "FOOTBALL" in event.title.uppercase()

    private val nflWord = Regex("\\bNFL\\b", RegexOption.IGNORE_CASE)

    /** On the air at kickoff, and starting no more than an hour before it (not a pregame show running long). */
    private fun onAtKickoff(event: ListingEvent, kickoff: Instant): Boolean =
        !event.start.isAfter(kickoff.plus(Duration.ofMinutes(20))) &&
            event.end.isAfter(kickoff.plus(Duration.ofMinutes(30))) &&
            !event.start.isBefore(kickoff.minus(Duration.ofMinutes(60)))

    private fun mentions(text: String, teamName: String): Boolean {
        if (teamName.isBlank()) return false
        return Regex("\\b${Regex.escape(teamName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun soleGameOnNetwork(game: Game, games: List<Game>, affiliate: String?): Boolean {
        val network = Networks.overTheAir(game.broadcasts) ?: return false
        if (affiliate == null || !affiliate.equals(network, ignoreCase = true)) return false
        return games.none { other ->
            other.id != game.id && Networks.overTheAir(other.broadcasts) == network &&
                kotlin.math.abs(other.startTime.epochSecond - game.startTime.epochSecond) < 60 * 60
        }
    }
}
