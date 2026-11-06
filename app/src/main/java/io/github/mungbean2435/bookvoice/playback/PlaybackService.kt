package io.github.mungbean2435.bookvoice.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.content.ContextCompat
import io.github.mungbean2435.bookvoice.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务只做三件事：保活、通知栏、锁屏/蓝牙媒体键。
 * 播放器本身在进程内的 SpeechPlayer，服务通过 MediaSession 回调操作它。
 */
class PlaybackService : Service() {

    private lateinit var session: MediaSession
    private var scope: CoroutineScope? = null
    private var renderJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // 先用不依赖 MediaSession 的占位通知满足前台服务 5 秒窗口，
        // 等 session 建好后再换成带媒体控制的通知。
        startForeground(NOTIFICATION_ID, buildPlaceholder())

        session = MediaSession(this, "bookvoice").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    val state = Speech.get().state.value
                    if (state is PlayerState.Paused) Speech.get().togglePause()
                }

                override fun onPause() {
                    val state = Speech.get().state.value
                    if (state is PlayerState.Playing) Speech.get().togglePause()
                }

                override fun onSkipToNext() = Speech.get().nextBlock()

                override fun onSkipToPrevious() = Speech.get().previousBlock()

                override fun onStop() = Speech.get().stop()
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        // 换成正式通知；此时 sessionToken 已可用。
        startForeground(NOTIFICATION_ID, buildNotification(Speech.get().state.value))

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { sc ->
            renderJob = sc.launch {
                Speech.get().state.collect { render(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val state = Speech.get().state.value
                if (state is PlayerState.Playing || state is PlayerState.Paused) Speech.get().togglePause()
            }

            ACTION_NEXT -> Speech.get().nextBlock()
            ACTION_PREV -> Speech.get().previousBlock()
            ACTION_STOP -> Speech.get().stop()
        }
        startForeground(NOTIFICATION_ID, buildNotification(Speech.get().state.value))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        renderJob?.cancel()
        scope?.cancel()
        runCatching { session.isActive = false; session.release() }
        super.onDestroy()
    }

    private fun render(state: PlayerState) {
        updateSession(state)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun updateSession(state: PlayerState) {
        val player = Speech.get()
        val isPlaying = state is PlayerState.Playing
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, player.chapterTitle.ifBlank { "书声" })
                .putString(MediaMetadata.METADATA_KEY_ARTIST, player.bookTitle)
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f
                )
                .build()
        )
    }

    private fun buildPlaceholder(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("书声")
            .setContentText("准备播放")
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun buildNotification(state: PlayerState): Notification {
        val player = Speech.get()
        val isPlaying = state is PlayerState.Playing
        val title = player.bookTitle.ifBlank { "书声" }
        val text = when (state) {
            is PlayerState.Playing -> "${player.chapterTitle} · 第 ${state.blockIdx + 1} 段"
            is PlayerState.Buffering -> "${player.chapterTitle} · 正在合成…"
            is PlayerState.Paused -> "${player.chapterTitle} · 已暂停"
            is PlayerState.Error -> state.message
            else -> "已停止"
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        builder.addAction(action(android.R.drawable.ic_media_previous, "上一段", ACTION_PREV, 1))
        builder.addAction(
            action(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "暂停" else "播放",
                ACTION_TOGGLE,
                2
            )
        )
        builder.addAction(action(android.R.drawable.ic_media_next, "下一段", ACTION_NEXT, 3))
        return builder.build()
    }

    private fun action(iconRes: Int, title: String, action: String, code: Int): Notification.Action {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        val pending = PendingIntent.getService(
            this,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Action.Builder(Icon.createWithResource(this, iconRes), title, pending).build()
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "朗读播放", NotificationManager.IMPORTANCE_LOW).apply {
                description = "书声播放控制"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_TOGGLE = "io.github.mungbean2435.bookvoice.TOGGLE"
        private const val ACTION_NEXT = "io.github.mungbean2435.bookvoice.NEXT"
        private const val ACTION_PREV = "io.github.mungbean2435.bookvoice.PREV"
        private const val ACTION_STOP = "io.github.mungbean2435.bookvoice.STOP"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }
    }
}
