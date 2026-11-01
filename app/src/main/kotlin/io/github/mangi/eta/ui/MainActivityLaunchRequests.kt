package io.github.mangi.eta.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import io.github.mangi.eta.agent.voice.EtaAssistantOverlayService
import io.github.mangi.eta.ui.app.ArchivedConversationRequest
import io.github.mangi.eta.ui.voice.ACTION_SPEECH_SETTINGS

internal data class MainActivityLaunchRequests(
    val assistantConversationKey: String? = null,
    val conversationId: String? = null,
    val archivedConversation: ArchivedConversationRequest? = null,
    val speechSettings: Boolean = false,
    val terminal: Boolean = false,
    val sharedUris: List<String> = emptyList(),
    val sharedText: String? = null,
) {
    fun accept(intent: Intent?): MainActivityLaunchRequests = when (intent?.action) {
        MainActivity.ACTION_OPEN_TERMINAL -> copy(terminal = true)
        ACTION_SPEECH_SETTINGS -> copy(speechSettings = true)
        MainActivity.ACTION_OPEN_CONVERSATION_ID -> copy(
            conversationId = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_ID),
        )
        MainActivity.ACTION_OPEN_ARCHIVED_CONVERSATION -> {
            val source = intent.nonBlankExtra(MainActivity.EXTRA_HANDOFF_SOURCE)
            val key = intent.nonBlankExtra(MainActivity.EXTRA_CONVERSATION_KEY)
            copy(
                archivedConversation = if (source != null && key != null) {
                    ArchivedConversationRequest(source, key)
                } else null,
            )
        }
        EtaAssistantOverlayService.ACTION_OPEN_CONVERSATION -> copy(
            assistantConversationKey = intent.nonBlankExtra(EtaAssistantOverlayService.EXTRA_CONVERSATION_KEY),
        )
        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW -> copy(
            sharedUris = intent.extractShareUris(),
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() },
        )
        else -> this
    }

    fun saveTo(outState: Bundle) {
        outState.putBundle(STATE_KEY, Bundle().apply {
            putString("assistant_conversation_key", assistantConversationKey)
            putString("conversation_id", conversationId)
            putString("archive_source", archivedConversation?.source)
            putString("archive_conversation_key", archivedConversation?.conversationKey)
            putBoolean("speech_settings", speechSettings)
            putBoolean("terminal", terminal)
            putStringArrayList("shared_uris", ArrayList(sharedUris))
            putString("shared_text", sharedText)
        })
    }

    companion object {
        private const val STATE_KEY = "eta_pending_launch_requests"

        fun restore(savedInstanceState: Bundle?, intent: Intent?): MainActivityLaunchRequests {
            // 系统可能保留最初的启动 Intent；空的待办状态也必须保存，防止重建后重放已消费的跳转。
            val saved = savedInstanceState?.getBundle(STATE_KEY)
                ?: return MainActivityLaunchRequests().accept(intent)
            val source = saved.getString("archive_source")
            val key = saved.getString("archive_conversation_key")
            return MainActivityLaunchRequests(
                assistantConversationKey = saved.getString("assistant_conversation_key"),
                conversationId = saved.getString("conversation_id"),
                archivedConversation = if (source != null && key != null) {
                    ArchivedConversationRequest(source, key)
                } else null,
                speechSettings = saved.getBoolean("speech_settings"),
                terminal = saved.getBoolean("terminal"),
                sharedUris = saved.getStringArrayList("shared_uris").orEmpty(),
                sharedText = saved.getString("shared_text"),
            )
        }
    }
}

private fun Intent.nonBlankExtra(key: String): String? = getStringExtra(key)?.takeIf(String::isNotBlank)

/** 从分享/打开 Intent 中提取 URI（EXTRA_STREAM 或 data）。 */
private fun Intent.extractShareUris(): List<String> {
    val fromStream = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(shareUriExtra())
        Intent.ACTION_SEND_MULTIPLE -> shareUriListExtra()
        else -> emptyList()
    }
    val fromData = data?.takeIf {
        action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND
    }
    return (fromStream + listOfNotNull(fromData)).distinct().map { it.toString() }
}

private fun Intent.shareUriExtra(): Uri? {
    return if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
}

private fun Intent.shareUriListExtra(): List<Uri> {
    val values: ArrayList<out Parcelable>? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
    }
    return values.orEmpty().mapNotNull { it as? Uri }
}
