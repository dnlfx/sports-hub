package com.sportshub.app.data.tv

import androidx.compose.runtime.Immutable

/**
 * The map split into a grid of cells, each cell holding the market whose city is closest
 * (an index into [markets]). Coloring a cell by what that market sees draws the coverage
 * map; it's built once and recolored for each window.
 */
@Immutable
class MarketRaster(
    val width: Int,
    val height: Int,
    val cells: IntArray,
    val markets: List<TvMarket>,
) {
    /** Pixels for one window: each cell in the color of the game its market gets, else [none]. */
    fun pixels(colorOf: (TvMarket) -> Int?, none: Int): IntArray {
        val byMarket = IntArray(markets.size) { colorOf(markets[it]) ?: none }
        return IntArray(cells.size) { byMarket[cells[it]] }
    }

    companion object {
        /** Builds the grid over [map]'s bounds, [width] cells across. Takes a few tens of milliseconds. */
        fun build(map: UsMap, markets: List<TvMarket> = TvMarkets.mainland, width: Int = 480): MarketRaster {
            val height = (width * map.height / map.width).toInt().coerceAtLeast(1)
            val point = DoubleArray(2)
            val xs = FloatArray(markets.size)
            val ys = FloatArray(markets.size)
            markets.forEachIndexed { i, m ->
                Geo.albers(m.lat, m.lon, point)
                xs[i] = point[0].toFloat()
                ys[i] = point[1].toFloat()
            }
            val cells = IntArray(width * height)
            for (row in 0 until height) {
                val y = map.minY + (row + 0.5f) * map.height / height
                for (col in 0 until width) {
                    val x = map.minX + (col + 0.5f) * map.width / width
                    var best = 0
                    var bestDist = Float.MAX_VALUE
                    for (i in xs.indices) {
                        val dx = xs[i] - x
                        val dy = ys[i] - y
                        val d = dx * dx + dy * dy
                        if (d < bestDist) {
                            bestDist = d
                            best = i
                        }
                    }
                    cells[row * width + col] = best
                }
            }
            return MarketRaster(width, height, cells, markets)
        }
    }
}
