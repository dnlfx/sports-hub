package com.sportshub.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.DataSource
import coil.imageLoader
import coil.memory.MemoryCache
import coil.network.HttpException
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget
import com.sportshub.app.data.espn.EspnImages
import kotlinx.coroutines.delay

private const val MAX_RETRIES = 5

/**
 * Every logo and headshot is decoded at this one size (in pixels) and cached in memory
 * under its URL alone. That way an image already shown anywhere in the app is found in
 * memory before the first frame is drawn, instead of flashing its placeholder first.
 */
private const val IMAGE_SIZE_PX = EspnImages.LOGO_PX

/**
 * Whether ESPN's image resizer is answering. If it turns down a picture whose original
 * then loads fine, the app goes back to the originals for the rest of the session
 * instead of paying for a failed request on every image.
 */
@Volatile
private var resizerWorks = true

/** The URL to download for [url]: a small copy when ESPN can make one, else the original. */
private fun downloadUrl(url: String, original: Boolean): String =
    if (original || !resizerWorks) url else EspnImages.resized(url) ?: url

/**
 * The request for one image. Screens and [prefetchImages] build it the same way, so a
 * prefetched image is the one a screen finds in memory.
 */
private fun imageRequest(context: Context, url: String, original: Boolean = false, attempt: Int = 0): ImageRequest =
    ImageRequest.Builder(context)
        .data(downloadUrl(url, original))
        .size(IMAGE_SIZE_PX)
        // Fixed rather than taken from each screen's crop or fit, so a picture decoded for one
        // screen (or a prefetch) is always good enough for every other screen.
        .scale(Scale.FIT)
        .precision(Precision.INEXACT)
        // Keyed by the original URL whichever copy was downloaded, so every screen shares it.
        .memoryCacheKey(url)
        // Changing this makes Coil load again, without touching the cache keys.
        .setParameter("attempt", attempt, memoryCacheKey = null)
        .build()

/**
 * Starts downloading [urls] in the background, so they're already in memory when a
 * screen or a scroll needs them. Images already in memory are skipped.
 */
fun Context.prefetchImages(urls: Iterable<String?>) {
    val loader = imageLoader
    val cache = loader.memoryCache
    urls.asSequence()
        .filterNotNull()
        .distinct()
        .filter { cache?.get(MemoryCache.Key(it)) == null }
        .forEach { loader.enqueue(imageRequest(this, it)) }
}

/** Prefetches [urls] once each time the list changes. */
@Composable
fun PrefetchImages(urls: List<String?>) {
    val context = LocalContext.current
    LaunchedEffect(urls) { context.prefetchImages(urls) }
}

/**
 * An image from the network, with [placeholder] drawn until it's showing.
 *
 * - Images already in memory appear on the very first frame, with no placeholder flash.
 * - ESPN pictures are fetched as small resized copies; if a copy can't be had, the
 *   original is fetched straight away instead.
 * - Coil gives up after one failed download, so a brief drop used to leave the
 *   placeholder up for good. This retries with a growing pause instead, without
 *   changing the cache keys, so a later success is cached like any other.
 */
@Composable
fun RetryingImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val loader = context.imageLoader
    var loaded by remember(url) {
        mutableStateOf(url != null && loader.memoryCache?.get(MemoryCache.Key(url)) != null)
    }
    var original by remember(url) { mutableStateOf(false) }
    var resizeRefused by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    var failedAttempt by remember(url) { mutableIntStateOf(-1) }
    LaunchedEffect(url, failedAttempt) {
        if (failedAttempt >= 0 && failedAttempt == attempt && attempt < MAX_RETRIES) {
            delay(retryDelayMs(attempt))
            attempt++
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) placeholder()
        if (url != null) {
            val request = remember(url, original, attempt) { imageRequest(context, url, original, attempt) }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onState = { state ->
                    // Only success and failure change what's shown; "loading" is ignored so an
                    // image that's already up never blinks back to its placeholder.
                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            loaded = true
                            // The original came through where the small copy was turned down,
                            // so the resizer is the problem: skip it from now on.
                            if (resizeRefused && request.data == url) resizerWorks = false
                        }
                        is AsyncImagePainter.State.Error -> when {
                            // No small copy: fetch the original straight away, without a pause.
                            request.data != url -> {
                                resizeRefused = state.result.throwable is HttpException
                                original = true
                            }
                            else -> failedAttempt = attempt
                        }
                        else -> Unit
                    }
                },
            )
        }
    }
}

/** 1s, 2s, 4s, 8s, 15s: quick for a blip, gentle on a dead connection. */
internal fun retryDelayMs(attempt: Int): Long = minOf(1_000L shl attempt.coerceIn(0, 4), 15_000L)

/**
 * A player's headshot in a circle, with their initials until it loads or when
 * there is none (ESPN has no photo for some backups and new signings).
 */
@Composable
fun PlayerHeadshot(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    RetryingImage(
        url = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        placeholder = {
            Text(
                initials(name),
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** "J. Allen" -> "JA", "Josh Allen" -> "JA". */
internal fun initials(name: String): String =
    name.split(' ', '.').filter { it.isNotBlank() }.let { parts ->
        when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts[0].take(2)
            else -> "${parts.first().first()}${parts.last().first()}"
        }
    }.uppercase()

/**
 * Fades in only images that just came over the network. One read from the disk cache
 * shows at once, so a logo seen on an earlier launch doesn't look like it's loading.
 */
class NetworkOnlyCrossfade(durationMs: Int) : Transition.Factory {
    private val crossfade = CrossfadeTransition.Factory(durationMs)

    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is SuccessResult && result.dataSource != DataSource.NETWORK) {
            Transition.Factory.NONE.create(target, result)
        } else {
            crossfade.create(target, result)
        }
}
