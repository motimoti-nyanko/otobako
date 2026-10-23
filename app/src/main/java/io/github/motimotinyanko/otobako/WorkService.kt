package io.github.motimotinyanko.otobako

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.motimotinyanko.otobako.data.Status
import io.github.motimotinyanko.otobako.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 処理中だけ動く前面サービス。進み具合を通知に出し、終わったら自分で止まる */
class WorkService : Service() {
    private var job: Job? = null
    private var lastText = ""
    private var lastAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "処理中", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, build("処理中", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (job?.isActive != true) {
            val repo = App.instance.repo
            job = repo.scope.launch {
                combine(repo.tracks, repo.export, repo.busy) { tracks, ex, busy -> Triple(tracks, ex, busy) }
                    .collect { (tracks, ex, busy) ->
                        if (!busy) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            job?.cancel()
                            return@collect
                        }
                        val text = if (ex != null) {
                            "書き出し中 ${ex.index + 1}/${ex.total}  ${ex.name}"
                        } else {
                            val n = tracks.count { it.isBusy }
                            val cur = tracks.firstOrNull { it.status == Status.DOWNLOADING }
                            "ダウンロード中 ${n}曲" + (cur?.sourceTitle?.takeIf { it.isNotEmpty() }?.let { "  $it" } ?: "")
                        }
                        val now = System.currentTimeMillis()
                        if (text == lastText && now - lastAt < 700) return@collect
                        lastText = text
                        lastAt = now
                        nm.notify(ID, build(text, ex?.let { (it.index + it.progress) / it.total }))
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(text: String, progress: Float?): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Otobako")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply { if (progress != null) setProgress(1000, (progress * 1000).toInt(), false) else setProgress(0, 0, true) }
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CH = "work"
        const val ID = 1
    }
}
