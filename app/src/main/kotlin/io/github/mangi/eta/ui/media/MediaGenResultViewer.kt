package io.github.mangi.eta.ui.media

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.model.AgentHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import top.yukonga.miuix.kmp.basic.Icon
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * 生成结果查看器：全屏显示图片/视频，支持保存到相册和分享。
 */
@Composable
internal fun MediaGenResultViewer(uiState: MediaGenUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val imageUrl = uiState.viewerImageUrl
    val imageBase64 = uiState.viewerImageBase64
    val videoUrl = uiState.viewerVideoUrl
    val isVideo = videoUrl != null

    // 加载图片
    LaunchedEffect(imageUrl, imageBase64) {
        if (isVideo) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        withContext(Dispatchers.IO) {
            runCatching {
                when {
                    imageBase64 != null -> {
                        val bytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    imageUrl != null -> {
                        val req = Request.Builder().url(imageUrl).get().build()
                        AgentHttpClient.client.newCall(req).execute().use { resp ->
                            val bytes = resp.body?.bytes() ?: error("empty")
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                    else -> null
                }
            }.onSuccess {
                bitmap = it
                if (it == null) loadError = "decode failed"
            }.onFailure {
                loadError = it.message
            }
        }
        loading = false
    }

    fun saveToGallery() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (isVideo) {
                    // 下载视频后存入 MediaStore
                    val url = videoUrl ?: error("no video")
                    val req = Request.Builder().url(url).get().build()
                    val bytes = AgentHttpClient.client.newCall(req).execute().use { resp ->
                        resp.body?.bytes() ?: error("empty")
                    }
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "eta_video_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Eta")
                        }
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("insert failed")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                } else {
                    val bmp = bitmap ?: error("no image")
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "eta_image_${System.currentTimeMillis()}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Eta")
                        }
                    }
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: error("insert failed")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.media_gen_saved, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.media_gen_save_failed, it.message ?: ""),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { uiState.closeViewer() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
        ) {
            // 内容区
            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null && !isVideo -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = loadError ?: "", color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                isVideo -> {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(Uri.parse(videoUrl))
                                setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = uiState.viewerPrompt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 顶部栏：关闭 + 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { uiState.closeViewer() }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.viewerPrompt.take(40),
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }

            // 底部栏：保存 + 分享
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Button(onClick = ::saveToGallery) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.media_gen_save))
                    }
                }
            }
        }
    }
}
