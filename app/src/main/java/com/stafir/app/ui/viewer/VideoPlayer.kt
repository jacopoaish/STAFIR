package com.stafir.app.ui.viewer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Lettore video (Media3/ExoPlayer) con i controlli standard. */
@Composable
fun VideoPlayer(uri: Uri, playing: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    DisposableEffect(playing) {
        player.playWhenReady = playing
        if (!playing) player.pause()
        onDispose { }
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        update = { it.player = player },
    )
}
