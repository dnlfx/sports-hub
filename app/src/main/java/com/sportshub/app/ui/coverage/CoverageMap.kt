package com.sportshub.app.ui.coverage

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sportshub.app.data.tv.CoverageWindow
import com.sportshub.app.data.tv.Geo
import com.sportshub.app.data.tv.MarketRaster
import com.sportshub.app.data.tv.NflHomes
import com.sportshub.app.data.tv.TvMarket
import com.sportshub.app.data.tv.UsMap
import com.sportshub.app.ui.theme.SportsHubTheme
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The outline and the market grid, loaded once off the main thread. */
class MapData(val map: UsMap, val raster: MarketRaster)

@Composable
fun rememberMapData(): MapData? {
    val context = LocalContext.current.applicationContext
    val data by produceState<MapData?>(cachedMapData) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.Default) {
            val map = UsMap.load(context)
            MapData(map, MarketRaster.build(map)).also { cachedMapData = it }
        }
    }
    return data
}

@Volatile private var cachedMapData: MapData? = null

/** Where the map sits in a box: map units to pixels. */
private class MapFit(map: UsMap, width: Float, height: Float, pad: Float) {
    val scale = min((width - 2 * pad) / map.width, (height - 2 * pad) / map.height)
    val left = (width - map.width * scale) / 2 - map.minX * scale
    val top = (height - map.height * scale) / 2 - map.minY * scale
    fun x(mapX: Float) = left + mapX * scale
    fun y(mapY: Float) = top + mapY * scale
    fun point(lat: Double, lon: Double): Offset {
        val p = Geo.albers(lat, lon)
        return Offset(x(p[0].toFloat()), y(p[1].toFloat()))
    }
}

/**
 * One window's coverage: the lower 48 colored by the game each market gets, with a dot
 * at each team's stadium and a ring around the user's market. Tapping picks the market
 * under the finger.
 */
@Composable
fun CoverageMap(
    data: MapData,
    window: CoverageWindow,
    gameColors: Map<String, Color>,
    yourMarket: TvMarket?,
    selected: TvMarket?,
    onTapMarket: (TvMarket) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = SportsHubTheme.colors
    val none = colors.cardHigh
    val image: ImageBitmap = remember(data, window, gameColors, none) {
        val pixels = data.raster.pixels(
            colorOf = { market -> window.airing[market.id]?.let { gameColors[it] }?.toArgb() },
            none = none.toArgb(),
        )
        Bitmap.createBitmap(pixels, data.raster.width, data.raster.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val map = data.map
    val lineColor = SportsHubTheme.colors.card
    val coastColor = SportsHubTheme.colors.stroke
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    // Each team's stadium, in its game's color.
    val stadiums = remember(window, gameColors) {
        window.games.flatMap { game ->
            listOf(game.away.team, game.home.team).mapNotNull { team ->
                NflHomes.stadium(team.abbreviation)?.let { Triple(it.first, it.second, gameColors[game.id] ?: Color.Gray) }
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(map.width / map.height)
            .semantics { contentDescription = description }
            .pointerInput(data) {
                detectTapGestures { tap ->
                    val fit = MapFit(map, size.width.toFloat(), size.height.toFloat(), 6.dp.toPx())
                    val col = ((tap.x - fit.x(map.minX)) / (map.width * fit.scale) * data.raster.width).toInt()
                    val row = ((tap.y - fit.y(map.minY)) / (map.height * fit.scale) * data.raster.height).toInt()
                    if (col in 0 until data.raster.width && row in 0 until data.raster.height) {
                        onTapMarket(data.raster.markets[data.raster.cells[row * data.raster.width + col]])
                    }
                }
            }
            .drawWithCache {
                val fit = MapFit(map, size.width, size.height, 6.dp.toPx())
                fun path(lines: List<FloatArray>, close: Boolean) = Path().apply {
                    lines.forEach { line ->
                        moveTo(fit.x(line[0]), fit.y(line[1]))
                        var i = 2
                        while (i < line.size) {
                            lineTo(fit.x(line[i]), fit.y(line[i + 1]))
                            i += 2
                        }
                        if (close) close()
                    }
                }
                val states = path(map.states, close = true)
                val borders = path(map.borders, close = false)
                val coast = path(map.coast, close = false)
                val imageOffset = IntOffset(fit.x(map.minX).roundToInt(), fit.y(map.minY).roundToInt())
                val imageSize = IntSize((map.width * fit.scale).roundToInt(), (map.height * fit.scale).roundToInt())
                val thin = Stroke(width = 0.8.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                val outline = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                val dot = 3.5.dp.toPx()
                val ring = 1.5.dp.toPx()
                onDrawBehind {
                    drawPath(states, none)
                    clipPath(states) {
                        drawImage(image, dstOffset = imageOffset, dstSize = imageSize, filterQuality = FilterQuality.Low)
                    }
                    drawPath(borders, lineColor.copy(alpha = 0.7f), style = thin)
                    drawPath(coast, coastColor, style = outline)
                    stadiums.forEach { (lat, lon, color) ->
                        val at = fit.point(lat, lon)
                        drawCircle(surface, radius = dot + ring, center = at)
                        drawCircle(color, radius = dot, center = at)
                    }
                    selected?.let {
                        val at = fit.point(it.lat, it.lon)
                        drawCircle(ink, radius = 7.dp.toPx(), center = at, style = Stroke(1.5.dp.toPx()))
                    }
                    yourMarket?.takeIf { it.state != "AK" && it.state != "HI" }?.let {
                        val at = fit.point(it.lat, it.lon)
                        drawCircle(surface, radius = 6.dp.toPx(), center = at)
                        drawCircle(ink, radius = 6.dp.toPx(), center = at, style = Stroke(2.dp.toPx()))
                        drawCircle(ink, radius = 2.dp.toPx(), center = at)
                    }
                }
            },
    )
}
