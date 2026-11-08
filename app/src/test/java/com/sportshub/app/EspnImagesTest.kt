package com.sportshub.app

import com.sportshub.app.data.espn.EspnImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EspnImagesTest {
    @Test fun logosAreFetchedAsSmallSquares() {
        assertEquals(
            "https://a.espncdn.com/combiner/i?img=/i/teamlogos/nfl/500/det.png&w=256&h=256&scale=crop&location=origin&transparent=true",
            EspnImages.resized("https://a.espncdn.com/i/teamlogos/nfl/500/det.png"),
        )
        assertEquals(
            "https://a.espncdn.com/combiner/i?img=/i/teamlogos/ncaa/500/2.png&w=256&h=256&scale=crop&location=origin&transparent=true",
            EspnImages.resized("https://a.espncdn.com/i/teamlogos/ncaa/500/2.png"),
        )
    }

    @Test fun headshotsKeepTheirShape() {
        assertEquals(
            "https://a.espncdn.com/combiner/i?img=/i/headshots/nba/players/full/1966.png&w=256&h=185&scale=crop&location=origin&transparent=true",
            EspnImages.resized("https://a.espncdn.com/i/headshots/nba/players/full/1966.png"),
        )
    }

    @Test fun otherImagesAreLeftAlone() {
        assertNull(EspnImages.resized(null))
        assertNull(EspnImages.resized("https://example.com/i/logo.png"))
        assertNull(EspnImages.resized("https://a.espncdn.com/combiner/i?img=/i/teamlogos/nfl/500/det.png"))
    }
}
