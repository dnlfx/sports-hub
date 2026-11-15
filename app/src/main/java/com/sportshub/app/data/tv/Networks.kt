package com.sportshub.app.data.tv

/** Telling free, over-the-air networks apart from cable channels and streaming services. */
object Networks {
    /** The broadcast networks anyone with an antenna gets, under the names ESPN uses. */
    private val overTheAir = setOf("CBS", "FOX", "NBC", "ABC", "CW", "THE CW")

    /**
     * The over-the-air network among a game's broadcasts, or null when it's only on cable
     * or streaming. "ESPN/ABC" counts as ABC; "CBS Sports Network", "FOX Deportes" and
     * "NBC Sports" don't count, being cable channels.
     */
    fun overTheAir(broadcasts: List<String>): String? {
        for (name in broadcasts) {
            for (part in name.split('/', ',', '&', '+')) {
                val key = part.trim().uppercase()
                if (key in overTheAir) return if (key == "THE CW") "CW" else key
            }
        }
        return null
    }
}
