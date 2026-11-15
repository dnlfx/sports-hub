package com.sportshub.app.data.tv

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Distances on the globe, and the map projection used to draw the lower 48. */
object Geo {
    private const val EARTH_RADIUS_MILES = 3958.8

    /** Great-circle distance in miles. */
    fun miles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_MILES * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    // Albers equal-area conic, the usual projection for maps of the lower 48: standard
    // parallels 29.5°N and 45.5°N, centered on 37.5°N, 96°W.
    private val phi1 = Math.toRadians(29.5)
    private val phi2 = Math.toRadians(45.5)
    private val phi0 = Math.toRadians(37.5)
    private const val LAMBDA0 = -96.0
    private val n = (sin(phi1) + sin(phi2)) / 2
    private val c = cos(phi1) * cos(phi1) + 2 * n * sin(phi1)
    private val rho0 = sqrt(c - 2 * n * sin(phi0)) / n

    /**
     * Projects a point to flat map units with x growing east and y growing south (screen
     * order). The lower 48 span roughly x -0.33..0.33 and y -0.25..0.17.
     */
    fun albers(lat: Double, lon: Double, out: DoubleArray = DoubleArray(2)): DoubleArray {
        val rho = sqrt(c - 2 * n * sin(Math.toRadians(lat))) / n
        val theta = n * Math.toRadians(lon - LAMBDA0)
        out[0] = rho * sin(theta)
        out[1] = -(rho0 - rho * cos(theta))
        return out
    }
}
