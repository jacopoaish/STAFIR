package com.stafir.app.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

/** Apre una singola foto o video ricevuti da un'altra app ("Apri con…"). */
@Composable
fun ExternalViewerScreen(uri: Uri, mimeType: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onClose)
    val type = mimeType ?: ctx.contentResolver.getType(uri) ?: ""
    var chrome by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (type.startsWith("video")) {
            VideoPlayer(uri, playing = true, modifier = Modifier.fillMaxSize())
        } else {
            Zoomable(onTap = { chrome = !chrome }) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(ctx).data(uri).size(3072).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Impossibile aprire questo file.", color = Color.White)
                        }
                    },
                )
            }
        }
        if (chrome) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding().padding(4.dp),
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Chiudi", tint = Color.White) }
                Box(Modifier.weight(1f))
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        setType(type.ifEmpty { "*/*" })
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, "Condividi"))
                }) { Icon(Icons.Filled.Share, "Condividi", tint = Color.White) }
            }
        }
    }
}
