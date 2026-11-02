package io.github.mangi.eta.ui.media

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 图片/视频生成的 UI 状态（弹窗可见性等），经 CompositionLocal 提供，
 * 避免在输入栏的多层参数中透传。
 */
internal class MediaGenUiState {
    var showImageDialog by mutableStateOf(false)
    var showVideoDialog by mutableStateOf(false)
    var showViewer by mutableStateOf(false)
    var viewerImageUrl by mutableStateOf<String?>(null)
    var viewerImageBase64 by mutableStateOf<String?>(null)
    var viewerVideoUrl by mutableStateOf<String?>(null)
    var viewerPrompt by mutableStateOf("")

    fun openImageDialog() { showImageDialog = true }
    fun openVideoDialog() { showVideoDialog = true }
    fun closeDialogs() { showImageDialog = false; showVideoDialog = false }

    fun showImageResult(prompt: String, url: String?, base64: String?) {
        viewerPrompt = prompt
        viewerImageUrl = url
        viewerImageBase64 = base64
        viewerVideoUrl = null
        showViewer = true
    }

    fun showVideoResult(prompt: String, url: String) {
        viewerPrompt = prompt
        viewerVideoUrl = url
        viewerImageUrl = null
        viewerImageBase64 = null
        showViewer = true
    }

    fun closeViewer() {
        showViewer = false
        viewerImageUrl = null
        viewerImageBase64 = null
        viewerVideoUrl = null
    }
}

internal val LocalMediaGenUi = compositionLocalOf<MediaGenUiState?> { null }
