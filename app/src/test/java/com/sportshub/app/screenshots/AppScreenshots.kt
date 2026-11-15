package com.sportshub.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sportshub.app.MainActivity
import com.sportshub.app.SportsHubApp
import com.sportshub.app.data.tv.TvMarkets
import com.sportshub.app.model.Sport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * Runs the real app on the JVM against live data (ESPN's scores, the TV guide's listings)
 * and saves screenshots of the NFL scores and the TV coverage page, with the TV market set
 * to New York and the ZIP to 10001 (-Dscreenshots.market / -Dscreenshots.zip to change).
 *
 * Only runs when asked: ./gradlew testDebugUnitTest -Pscreenshots --tests '*AppScreenshots*'
 * The PNGs land in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class AppScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun save(name: String) {
        // Let pictures (team logos) finish loading before the shot.
        repeat(20) {
            compose.waitForIdle()
            Thread.sleep(150)
        }
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 60_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun scoresAndCoverage() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val app = ApplicationProvider.getApplicationContext<SportsHubApp>()
        app.container.selectSport(Sport.NFL)
        app.container.tvMarkets.set(TvMarkets.byId(System.getProperty("screenshots.market") ?: "new-york"))
        app.container.tvMarkets.setZip(System.getProperty("screenshots.zip") ?: "10001")

        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.use {
            waitForText("TV coverage map")
            // The guide's answer: a card that names its local channel.
            compose.waitUntil(60_000) {
                compose.onAllNodesWithContentDescription("Free over the air on", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            save("1-nfl-scores")

            compose.onAllNodesWithText("TV coverage map").onFirst().performClick()
            waitForText("early")
            save("2-coverage")

            // FOX's early window, the one with the most games this week.
            compose.onAllNodesWithText("FOX early").onFirst().performClick()
            save("3-coverage-fox-early")
        }
    }
}
