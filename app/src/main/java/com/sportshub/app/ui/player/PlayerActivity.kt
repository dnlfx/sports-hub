package com.sportshub.app.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sportshub.app.data.scan.PageScanner
import com.sportshub.app.data.scan.VideoKind
import com.sportshub.app.ui.theme.SportsHubTheme

/**
 * Full-screen video player. Handles dropped connections on its own: it retries
 * failed loads, rejoins live streams at the live edge, and resumes as soon as
 * the network comes back.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var reconnecting by mutableStateOf(false)
    private var fatalError by mutableStateOf<String?>(null)
    private var inPip by mutableStateOf(false)
    private var resizeMode by mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val referer = intent.getStringExtra(EXTRA_REFERER)
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { VideoKind.valueOf(it) }.getOrNull() }

        enterImmersive()
        player = buildPlayer(referer)
        player.setMediaItem(buildMediaItem(url, kind))
        player.prepare()
        player.playWhenReady = true
        watchNetwork()

        setContent {
            SportsHubTheme(darkTheme = true) {
                PlayerScreen(
                    title = title,
                    onBack = ::finish,
                    onPip = ::enterPip,
                    onToggleAspect = {
                        resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onRetry = {
                        fatalError = null
                        retryCount = 0
                        restart()
                    },
                )
            }
        }
    }

    private fun buildPlayer(referer: String?): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(PageScanner.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .apply { if (referer != null) setDefaultRequestProperties(mapOf("Referer" to referer)) }

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(http)
            // Retry each failed segment several times before reporting an error.
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(8))

        // A larger buffer rides out short network drops, VPN reconnects included.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) = handleError(error)
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            retryCount = 0
                            reconnecting = false
                        }
                    }
                })
            }
    }

    private fun buildMediaItem(url: String, kind: VideoKind?): MediaItem {
        val mime = when (kind) {
            VideoKind.HLS -> MimeTypes.APPLICATION_M3U8
            VideoKind.DASH -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        return MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build()
    }

    private fun handleError(error: PlaybackException) {
        if (retryCount >= MAX_RETRIES) {
            reconnecting = false
            fatalError = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The stream refused the connection."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This video format isn't supported."
                else -> "The stream stopped and couldn't reconnect."
            }
            return
        }
        retryCount++
        reconnecting = true
        val delayMs = minOf(1_000L shl (retryCount - 1), 15_000L)
        handler.postDelayed({ restart(behindLiveWindow = error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) }, delayMs)
    }

    private fun restart(behindLiveWindow: Boolean = true) {
        if (behindLiveWindow || player.isCurrentMediaItemLive) player.seekToDefaultPosition()
        player.prepare()
        player.play()
    }

    /** Resume right away when the connection returns instead of waiting for the next retry. */
    private fun watchNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.post {
                    if (player.playerError != null && fatalError == null) {
                        handler.removeCallbacksAndMessages(null)
                        restart()
                    }
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }.onSuccess { networkCallback = callback }
    }

    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            Rational(size.width, size.height).coerceIn(Rational(100, 239), Rational(239, 100))
        } else {
            Rational(16, 9)
        }
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
    }

    private fun Rational.coerceIn(min: Rational, max: Rational): Rational =
        if (this < min) min else if (this > max) max else this

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!inPip) player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized && fatalError == null) player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        networkCallback?.let {
            runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        }
        if (::player.isInitialized) player.release()
    }

    @Composable
    private fun PlayerScreen(
        title: String,
        onBack: () -> Unit,
        onPip: () -> Unit,
        onToggleAspect: () -> Unit,
        onRetry: () -> Unit,
    ) {
        var controlsVisible by androidx.compose.runtime.remember { mutableStateOf(true) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        this.player = this@PlayerActivity.player
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        keepScreenOn = true
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == android.view.View.VISIBLE
                        })
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                    view.useController = !inPip
                },
            )

            AnimatedVisibility(
                visible = controlsVisible && !inPip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onToggleAspect) {
                        Icon(Icons.Outlined.AspectRatio, contentDescription = "Fit or fill", tint = Color.White)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(onClick = onPip) {
                            Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "Picture in picture", tint = Color.White)
                        }
                    }
                }
            }

            if (reconnecting && fatalError == null && !inPip) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Reconnecting…", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }

            fatalError?.let { message ->
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }

    companion object {
        private const val MAX_RETRIES = 6
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_REFERER = "referer"
        private const val EXTRA_KIND = "kind"

        fun intent(context: Context, url: String, title: String, referer: String?, kind: VideoKind): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_REFERER, referer)
                .putExtra(EXTRA_KIND, kind.name)
    }
}
