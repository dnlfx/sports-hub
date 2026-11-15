package com.sportshub.app.data.tv

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * The user's TV market, which decides which games they get over the air, and their ZIP
 * code, for the exact channels from the TV guide. Picked from a list and typed in, or
 * found once from the phone's approximate location (never tracked). Saved on the device only.
 */
class TvMarketRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _market = MutableStateFlow(TvMarkets.byId(prefs.getString(KEY_MARKET, null)))
    val market: StateFlow<TvMarket?> = _market.asStateFlow()

    fun set(market: TvMarket?) {
        _market.value = market
        prefs.edit().putString(KEY_MARKET, market?.id).apply()
    }

    private val _zip = MutableStateFlow(prefs.getString(KEY_ZIP, null)?.takeIf(LocalListings::isZip))

    /** The user's ZIP code, for their exact antenna channels from the TV guide. */
    val zip: StateFlow<String?> = _zip.asStateFlow()

    fun setZip(zip: String?) {
        val valid = zip?.takeIf(LocalListings::isZip)
        _zip.value = valid
        prefs.edit().putString(KEY_ZIP, valid).apply()
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * The market the phone is in, from its approximate location: the last known fix if
     * there is one, else a fresh one. Null without permission or when no fix comes in time.
     */
    @SuppressLint("MissingPermission")
    suspend fun locate(): TvMarket? {
        if (!hasLocationPermission()) return null
        val manager = ContextCompat.getSystemService(appContext, LocationManager::class.java) ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val last = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        val location = last ?: fresh(manager, providers) ?: return null
        zipAt(location)?.let(::setZip)
        return TvMarkets.nearest(location.latitude, location.longitude)
    }

    /** The ZIP code at [location], from the phone's geocoder when it has one. */
    private suspend fun zipAt(location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(appContext, Locale.US).getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()?.postalCode?.take(5)?.takeIf(LocalListings::isZip)
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(manager: LocationManager, providers: List<String>): Location? {
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        val provider = preferred.firstOrNull { it in providers } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        manager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(appContext),
                    ) { location -> if (cont.isActive) cont.resume(location) }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    companion object {
        const val LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
        private const val KEY_MARKET = "tv_market"
        private const val KEY_ZIP = "tv_zip"
    }
}
