package com.example.todolock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifications {

    const val CHANNEL_SERVICE = "todolock_service"
    const val CHANNEL_ALERT = "todolock_alert"
    const val ID_SERVICE = 1001
    const val ID_ALERT = 1002

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "잠금해제 감지",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "잠금해제를 감지하기 위해 실행 중인 상태 표시"
            setShowBadge(false)
        }
        nm.createNotificationChannel(service)

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "오늘의 할 일 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "잠금해제 시 오늘 할 일을 알려줍니다"
        }
        nm.createNotificationChannel(alert)
    }

    fun serviceNotification(ctx: Context): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("TodoLock 실행 중")
            .setContentText("잠금해제하면 오늘 할 일을 보여줍니다")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    /**
     * '다른 앱 위에 표시' 권한이 없어 백그라운드에서 화면을 띄울 수 없을 때의 대체 수단.
     * 전체화면 인텐트로 시도하고, 막히면 헤드업 알림으로 표시됩니다.
     */
    fun showFallbackAlert(ctx: Context, remaining: Int) {
        ensureChannels(ctx)
        val intent = Intent(ctx, TodayPopupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            ctx, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("오늘 할 일 " + remaining + "개 남았어요")
            .setContentText("눌러서 확인하기")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(ID_ALERT, n)
        } catch (e: SecurityException) {
            // 알림 권한 없음
        }
    }
}
