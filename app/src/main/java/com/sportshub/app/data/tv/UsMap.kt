package com.sportshub.app.data.tv

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The lower 48 in map units (see [Geo.albers]): state outlines to fill and clip to, the
 * lines between states, and the coast and borders. Each line is x0, y0, x1, y1, ...
 */
@Immutable
class UsMap(
    val states: List<FloatArray>,
    val borders: List<FloatArray>,
    val coast: List<FloatArray>,
) {
    val minX: Float = states.minOf { ring -> ring.xs().min() }
    val maxX: Float = states.maxOf { ring -> ring.xs().max() }
    val minY: Float = states.minOf { ring -> ring.ys().min() }
    val maxY: Float = states.maxOf { ring -> ring.ys().max() }
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY

    companion object {
        private const val ASSET = "us_map.txt"

        @Volatile private var loaded: UsMap? = null

        /** Read once from the app's assets, then kept. */
        suspend fun load(context: Context): UsMap = loaded ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            parse(text).also { loaded = it }
        }

        /** Parses the asset written by tools/make_us_map.py. */
        fun parse(text: String): UsMap {
            val states = ArrayList<FloatArray>()
            val borders = ArrayList<FloatArray>()
            val coast = ArrayList<FloatArray>()
            val point = DoubleArray(2)
            text.lineSequence().forEach { line ->
                if (line.length < 3 || line.startsWith("#")) return@forEach
                val pairs = line.substring(2).split(' ')
                val out = FloatArray(pairs.size * 2)
                pairs.forEachIndexed { i, pair ->
                    val comma = pair.indexOf(',')
                    Geo.albers(pair.substring(comma + 1).toDouble(), pair.substring(0, comma).toDouble(), point)
                    out[i * 2] = point[0].toFloat()
                    out[i * 2 + 1] = point[1].toFloat()
                }
                when (line[0]) {
                    'P' -> states += out
                    'B' -> borders += out
                    'C' -> coast += out
                }
            }
            return UsMap(states, borders, coast)
        }

        private fun FloatArray.xs() = filterIndexed { i, _ -> i % 2 == 0 }
        private fun FloatArray.ys() = filterIndexed { i, _ -> i % 2 == 1 }
    }
}
