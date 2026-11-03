package app.chumo.otoho

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private var beepTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                OtohoApp(onPlayLeft = { playBeep(leftChannel = true) }, onPlayRight = { playBeep(leftChannel = false) })
            }
        }
    }

    override fun onDestroy() {
        releaseBeep()
        super.onDestroy()
    }

    /** 播放 3 秒 500Hz 蜂鸣声；leftChannel=true 只走左声道，否则只走右声道 */
    private fun playBeep(leftChannel: Boolean) {
        releaseBeep()
        val track = buildBeepTrack(leftChannel) ?: return
        beepTrack = track
        try {
            track.play()
        } catch (_: Exception) {
            releaseBeep()
        }
        // 3 秒后释放
        lifecycleScope.launch(Dispatchers.Default) {
            delay(3300)
            if (beepTrack === track) releaseBeep()
        }
    }

    private fun buildBeepTrack(leftChannel: Boolean): AudioTrack? {
        val frames = SAMPLE_RATE * 3  // 3 秒
        val pcm = ShortArray(frames * 2) // 立体声交错
        val twoPiF = 2.0 * PI * 500.0
        val fadeFrames = SAMPLE_RATE / 100 // 10ms 淡入淡出，避免爆音
        for (i in 0 until frames) {
            val v = sin(twoPiF * i / SAMPLE_RATE) * 0.6
            val gain = when {
                i < fadeFrames -> i.toDouble() / fadeFrames
                i > frames - fadeFrames -> (frames - i).toDouble() / fadeFrames
                else -> 1.0
            }
            val s = (v * gain * Short.MAX_VALUE).toInt().toShort()
            pcm[i * 2] = if (leftChannel) s else 0
            pcm[i * 2 + 1] = if (leftChannel) 0 else s
        }
        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
                maxOf(minBuf, pcm.size * 2),
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            track.write(pcm, 0, pcm.size)
            track
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseBeep() {
        beepTrack?.let { t ->
            try {
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                t.flush()
                t.release()
            } catch (_: Exception) {
            }
        }
        beepTrack = null
    }

    companion object {
        private const val SAMPLE_RATE = 44100
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OtohoApp(onPlayLeft: () -> Unit, onPlayRight: () -> Unit) {
    var showAbout by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Otoho") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "关于")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayLeft,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("左") }
                Button(
                    onClick = onPlayRight,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) { Text("右") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAbout) {
        val linkColor = if (isSystemInDarkTheme()) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primary
        }
        val aboutText = buildAnnotatedString {
            append("本应用由")
            withLink(
                LinkAnnotation.Url(
                    "https://github.com/zzChumo",
                    TextLinkStyles(style = SpanStyle(color = linkColor))
                )
            ) {
                append("zzChumo")
            }
            append("制作，DeepSeek辅助完成。")
        }
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于", style = MaterialTheme.typography.headlineSmall) },
            text = { Text(aboutText) },
            confirmButton = {
                FilledTonalButton(onClick = { showAbout = false }) { Text("关闭") }
            }
        )
    }
}
