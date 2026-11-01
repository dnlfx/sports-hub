package com.sportshub.app.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sportshub.app.data.ScoresRepository
import com.sportshub.app.data.SourcesRepository
import com.sportshub.app.data.scan.PageScanner
import com.sportshub.app.data.scan.ScanResult
import com.sportshub.app.model.Game
import com.sportshub.app.model.GameStatus
import com.sportshub.app.model.Sport
import com.sportshub.app.model.StreamSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

sealed interface ScanState {
    data object Scanning : ScanState
    data class Done(val result: ScanResult) : ScanState
}

class WatchViewModel(
    sourcesRepository: SourcesRepository,
    private val scoresRepository: ScoresRepository,
    private val scanner: PageScanner,
) : ViewModel() {

    /** Saved sources; null until they've been read from storage. */
    val sources: StateFlow<List<StreamSource>?> = sourcesRepository.current

    private val _scans = MutableStateFlow<Map<String, ScanState>>(emptyMap())
    val scans: StateFlow<Map<String, ScanState>> = _scans.asStateFlow()

    // Start from the scores already in memory, so the "on now" row is there on the first frame.
    private val _games = MutableStateFlow(
        scoresRepository.peekScoreboard(Sport.default)?.let { relevant(it.games) }.orEmpty(),
    )
    val games: StateFlow<List<Game>> = _games.asStateFlow()

    private var loadedSport: Sport? = null
    private var gamesLoadedAtMs = 0L

    /** Scans any source that hasn't been scanned yet. */
    fun ensureScanned(list: List<StreamSource>) {
        list.filter { it.id !in _scans.value }.forEach { scan(it) }
    }

    fun scan(source: StreamSource) {
        _scans.update { it + (source.id to ScanState.Scanning) }
        viewModelScope.launch {
            val result = scanner.scan(source.url)
            _scans.update { it + (source.id to ScanState.Done(result)) }
        }
    }

    /**
     * Games that are live now or start within the next 12 hours, with where they air.
     * Reloads when the sport changes or the list is more than a minute old.
     */
    fun loadGames(sport: Sport) {
        val fresh = System.currentTimeMillis() - gamesLoadedAtMs < 60_000L
        if (loadedSport == sport && fresh) return
        if (loadedSport != sport) _games.value = scoresRepository.peekScoreboard(sport)?.let { relevant(it.games) }.orEmpty()
        loadedSport = sport
        gamesLoadedAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            if (_games.value.isEmpty()) {
                scoresRepository.cachedScoreboard(sport)?.let { _games.value = relevant(it.games) }
            }
            scoresRepository.scoreboard(sport).onSuccess { _games.value = relevant(it.games) }
        }
    }

    private fun relevant(games: List<Game>): List<Game> {
        val now = Instant.now()
        return games.filter { game ->
            game.status is GameStatus.Live ||
                (game.status == GameStatus.Scheduled && Duration.between(now, game.startTime).toHours() in 0..12)
        }
    }
}
