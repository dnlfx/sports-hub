package com.sportshub.app

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sportshub.app.data.EspnScoresRepository
import com.sportshub.app.data.FavoritesRepository
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.data.TeamRepository
import com.sportshub.app.data.espn.EspnClient
import com.sportshub.app.data.scan.PageScanner
import com.sportshub.app.model.Game
import com.sportshub.app.model.PlayerRef
import com.sportshub.app.model.Sport
import com.sportshub.app.model.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Simple manual dependency container, created once in [SportsHubApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /**
     * One HTTP client for the whole app, so scores, page scans and images share
     * a connection pool and thread pool instead of each opening their own.
     */
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val espn = EspnClient(http)
    val scoresRepository: ScoresRepository = EspnScoresRepository(appContext, espn)
    val teamRepository = TeamRepository(appContext, espn)
    val sourcesRepository = SourcesRepository(appContext)
    val favorites = FavoritesRepository(appContext)
    val pageScanner = PageScanner(http)

    /**
     * Images (team logos, player headshots). Logos almost never change, so the disk
     * cache ignores the server's short cache headers and keeps them across launches.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient(http)
            .crossfade(180)
            .respectCacheHeaders(false)
            .memoryCache { MemoryCache.Builder(appContext).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("images"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    /** The game the user just tapped, so its detail screen can draw the header before loading. */
    var lastOpenedGame: Game? = null

    /** The team the user just tapped, so its page can draw the header before loading. */
    var lastOpenedTeam: Team? = null

    /** The player the user just tapped, so their page can draw the header before loading. */
    var lastOpenedPlayer: PlayerRef? = null

    /** What the compare page opens with in its first slot. */
    var lastCompareTeam: Team? = null
    var lastComparePlayer: PlayerRef? = null

    private val _selectedSport = MutableStateFlow(Sport.default)
    val selectedSport: StateFlow<Sport> = _selectedSport.asStateFlow()

    fun selectSport(sport: Sport) {
        if (sport.isAvailable) _selectedSport.value = sport
    }
}
