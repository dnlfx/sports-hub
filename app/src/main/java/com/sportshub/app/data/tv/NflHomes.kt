package com.sportshub.app.data.tv

/**
 * Where each NFL team plays, and the TV markets that count as its home.
 *
 * League rules guarantee a team's games to its home markets: every regional CBS or FOX
 * game airs there, and games on cable or streaming (ESPN, Prime Video, NFL Network,
 * Netflix) are also shown on a local station there. The league's "home territory" is 75
 * miles around the stadium, so a market is home when its city is within that distance and
 * this is its closest stadium (so Washington isn't counted as a Ravens market).
 */
object NflHomes {
    const val HOME_RADIUS_MILES = 75.0

    private data class Stadium(val lat: Double, val lon: Double)

    /** By ESPN team abbreviation, with the older spellings ESPN has used too. */
    private val stadiums: Map<String, Stadium> = buildMap {
        fun s(lat: Double, lon: Double, vararg teams: String) = teams.forEach { put(it, Stadium(lat, lon)) }
        s(33.528, -112.263, "ARI")
        s(33.755, -84.401, "ATL")
        s(39.278, -76.623, "BAL")
        s(42.774, -78.787, "BUF")
        s(35.226, -80.853, "CAR")
        s(41.862, -87.617, "CHI")
        s(39.095, -84.516, "CIN")
        s(41.506, -81.700, "CLE")
        s(32.748, -97.093, "DAL")
        s(39.744, -105.020, "DEN")
        s(42.340, -83.046, "DET")
        s(44.501, -88.062, "GB")
        s(29.685, -95.411, "HOU")
        s(39.760, -86.164, "IND")
        s(30.324, -81.637, "JAX", "JAC")
        s(39.049, -94.484, "KC")
        s(36.091, -115.184, "LV")
        s(33.953, -118.339, "LAR", "LA", "LAC")
        s(25.958, -80.239, "MIA")
        s(44.974, -93.258, "MIN")
        s(42.091, -71.264, "NE")
        s(29.951, -90.081, "NO")
        s(40.814, -74.074, "NYG", "NYJ")
        s(39.901, -75.168, "PHI")
        s(40.447, -80.016, "PIT")
        s(37.403, -121.970, "SF")
        s(47.595, -122.332, "SEA")
        s(27.976, -82.503, "TB")
        s(36.166, -86.771, "TEN")
        s(38.908, -76.864, "WSH", "WAS")
    }

    /** Big markets that are home to a team despite being farther than 75 miles. */
    private val extraHomes: Map<String, List<String>> = mapOf(
        // The Packers have played part of their schedule in Milwaukee for decades.
        "GB" to listOf("milwaukee"),
    )

    fun knows(team: String): Boolean = team.uppercase() in stadiums

    /** Latitude and longitude of [team]'s stadium, or null for a team the app doesn't know. */
    fun stadium(team: String): Pair<Double, Double>? = stadiums[team.uppercase()]?.let { it.lat to it.lon }

    /** Miles from a market's city to [team]'s stadium, or null for a team the app doesn't know. */
    fun milesTo(market: TvMarket, team: String): Double? =
        stadiums[team.uppercase()]?.let { Geo.miles(market.lat, market.lon, it.lat, it.lon) }

    private val homeTeamsByMarket: Map<String, Set<String>> by lazy {
        val result = HashMap<String, MutableSet<String>>()
        TvMarkets.all.forEach { market ->
            val distances = stadiums.mapValues { (_, s) -> Geo.miles(market.lat, market.lon, s.lat, s.lon) }
            val closest = distances.values.min()
            if (closest <= HOME_RADIUS_MILES) {
                // Every team at the closest stadium: both New York teams, both Los Angeles teams.
                val teams = distances.filterValues { it <= closest + 0.5 }.keys
                result.getOrPut(market.id) { HashSet() } += teams
            }
        }
        extraHomes.forEach { (team, markets) -> markets.forEach { result.getOrPut(it) { HashSet() } += team } }
        result
    }

    /** The teams [market] is home to, by abbreviation (including older spellings). */
    fun homeTeams(market: TvMarket): Set<String> = homeTeamsByMarket[market.id].orEmpty()

    fun isHome(market: TvMarket, team: String): Boolean = team.uppercase() in homeTeams(market)

    /** The markets that are home to [team]. */
    fun homeMarkets(team: String): List<TvMarket> = TvMarkets.all.filter { isHome(it, team) }
}
