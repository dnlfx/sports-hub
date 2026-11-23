package com.sportshub.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.sportshub.app.MainActivity
import com.sportshub.app.SportsHubApp
import com.sportshub.app.model.Sport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.TimeZone

/**
 * Runs the real app on the JVM against ESPN's live data, opens a finished NFL game and saves
 * screenshots of its player stats: the top of the box score, further down it (where the team
 * switch pins under the top bar), and the other team's.
 *
 * -Pscreenshots.game picks the game by a team name on its card (default Buccaneers) and
 * -Pscreenshots.other names the team to switch to (default Lions),
 * -Pscreenshots.fontScale the text size (default 1.15, a common "large" setting),
 * -Pscreenshots.night=false shoots light mode.
 *
 * Only runs when asked: ./gradlew testDebugUnitTest -Pscreenshots --tests '*BoxScoreScreenshots*'
 * The PNGs land in app/build/screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class BoxScoreScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun save(name: String) {
        // Let pictures (logos, headshots) finish loading before the shot.
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

    /** Scrolls the list down by [dy] pixels (negative scrolls back up), slowly so it doesn't fling. */
    private fun scrollBy(dy: Float) {
        compose.onNode(hasScrollToKeyAction()).performTouchInput {
            val start = Offset(centerX, if (dy > 0) bottom - 200f else top + 400f)
            swipe(start, start - Offset(0f, dy), durationMillis = 1_500)
        }
    }

    @Test fun playerStats() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
        val night = System.getProperty("screenshots.night") != "false"
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        RuntimeEnvironment.setFontScale(System.getProperty("screenshots.fontScale")?.toFloat() ?: 1.15f)
        val prefix = System.getProperty("screenshots.prefix") ?: ""
        val app = ApplicationProvider.getApplicationContext<SportsHubApp>()
        app.container.selectSport(Sport.NFL)

        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.use {
            val team = System.getProperty("screenshots.game") ?: "Buccaneers"
            compose.waitUntil(60_000) {
                compose.onAllNodesWithContentDescription("Final", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            // The game's card may be further down the scores list, which only builds what's on screen.
            val card = hasContentDescription(team, substring = true)
            val lists = compose.onAllNodes(hasScrollToNodeAction())
            for (i in lists.fetchSemanticsNodes().indices) {
                if (runCatching { lists[i].performScrollToNode(card) }.isSuccess) break
            }
            compose.onAllNodesWithContentDescription(team, substring = true).onFirst().performClick()
            // The game page's list, once its box score has loaded (it builds only what's on screen).
            compose.waitUntil(60_000) {
                runCatching { compose.onNode(hasScrollToKeyAction()).performScrollToKey("table_passing") }.isSuccess
            }
            val list = compose.onNode(hasScrollToKeyAction())
            list.performScrollToKey("players")
            // Bring the box score's title out from under the top bar.
            scrollBy(-260f)
            save("${prefix}1-player-stats")

            list.performScrollToKey("table_receiving")
            scrollBy(-120f)
            save("${prefix}2-player-stats-receiving")

            // The other team, from its tab: pinned under the top bar if the layout has one,
            // else back up at the top of the box score.
            val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                hasText(System.getProperty("screenshots.other") ?: "Lions", substring = true)
            if (compose.onAllNodes(tab).fetchSemanticsNodes().isEmpty()) list.performScrollToKey("players")
            compose.onAllNodes(tab).onFirst().performClick()
            compose.waitForIdle()
            list.performScrollToKey("table_receiving")
            scrollBy(-120f)
            save("${prefix}3-player-stats-other-team")
        }
    }
}
