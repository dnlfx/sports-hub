package com.sportshub.app.data.tv

/**
 * Where each NFL team's fans are beyond its home markets, and its division rivals.
 *
 * The networks give a team's regional fan base its game when they can, on top of the
 * guaranteed home markets ([NflHomes]). These "secondary" markets are the ones that
 * reliably got their team's game in past weeks' real listings; a market can belong to
 * more than one team. Edit freely: a market missing here just falls back to the window's
 * featured game.
 */
object NflRegions {
    /** By team abbreviation (ESPN's, plus older spellings), the TV market ids of its fan region. */
    private val secondary: Map<String, Set<String>> = buildMap {
        fun r(markets: String, vararg teams: String) {
            val ids = markets.split(' ').filter { it.isNotBlank() }.toSet()
            teams.forEach { put(it, ids) }
        }
        r("rochester-ny syracuse elmira watertown utica binghamton erie", "BUF")
        r("west-palm-beach fort-myers", "MIA")
        r("providence portland-me bangor burlington springfield-ma presque-isle hartford", "NE")
        r("hartford albany-ny", "NYJ", "NYG")
        r("salisbury", "BAL")
        r("dayton lima zanesville columbus-oh", "CIN")
        r("youngstown toledo columbus-oh", "CLE")
        r("johnstown wheeling erie clarksburg", "PIT")
        r("san-antonio austin beaumont corpus-christi victoria harlingen laredo waco", "HOU")
        r("evansville terre-haute lafayette-in fort-wayne", "IND")
        r("gainesville tallahassee savannah", "JAX", "JAC")
        r("memphis jackson-tn bowling-green chattanooga huntsville", "TEN")
        r("colorado-springs grand-junction cheyenne casper billings rapid-city", "DEN")
        r("topeka st-joseph wichita joplin springfield-mo columbia-mo", "KC")
        r("reno", "LV")
        r("san-diego santa-barbara bakersfield palm-springs", "LAC", "LAR", "LA")
        r("tyler sherman wichita-falls abilene lubbock amarillo odessa san-angelo shreveport", "DAL")
        r("wilkes-barre", "PHI")
        r("richmond norfolk roanoke charlottesville harrisonburg", "WSH", "WAS")
        r("rockford peoria champaign quad-cities south-bend", "CHI")
        r("flint lansing grand-rapids traverse-city alpena", "DET")
        r("green-bay madison wausau la-crosse marquette", "GB")
        r("rochester-mn mankato fargo sioux-falls duluth minot", "MIN")
        r("macon columbus-ga augusta albany-ga montgomery dothan", "ATL")
        r("raleigh greenville-sc greensboro columbia-sc charleston-sc myrtle-beach wilmington greenville-nc", "CAR")
        r("baton-rouge lafayette-la biloxi mobile jackson-ms alexandria-la lake-charles monroe hattiesburg", "NO")
        r("tucson yuma", "ARI")
        r("sacramento fresno monterey chico eureka", "SF")
        r("spokane yakima portland-or eugene medford bend boise anchorage", "SEA")
    }

    private val divisions: List<Set<String>> = listOf(
        setOf("BUF", "MIA", "NE", "NYJ"),
        setOf("BAL", "CIN", "CLE", "PIT"),
        setOf("HOU", "IND", "JAX", "JAC", "TEN"),
        setOf("DEN", "KC", "LV", "LAC"),
        setOf("DAL", "NYG", "PHI", "WSH", "WAS"),
        setOf("CHI", "DET", "GB", "MIN"),
        setOf("ATL", "CAR", "NO", "TB"),
        setOf("ARI", "LAR", "LA", "SF", "SEA"),
    )

    /** Whether [market] is in [team]'s fan region beyond its home markets. */
    fun isRegional(market: TvMarket, team: String): Boolean = market.id in secondary[team.uppercase()].orEmpty()

    /** Whether [a] and [b] play in the same division. */
    fun rivals(a: String, b: String): Boolean {
        val x = a.uppercase()
        val y = b.uppercase()
        return x != y && divisions.any { x in it && y in it }
    }
}
