package com.example.todolock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * 잠금해제 브로드캐스트를 계속 받기 위한 포그라운드 서비스.
 * 알림 중요도는 MIN 이라 상태바에 거의 드러나지 않습니다.
 */
class UnlockService : Service() {

    private val receiver = UnlockReceiver()
    private var registered = false

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        startForeground(Notifications.ID_SERVICE, Notifications.serviceNotification(this))

        if (!registered) {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Notifications.ID_SERVICE, Notifications.serviceNotification(this))
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                // 이미 해제됨
            }
            registered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, UnlockService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: Exception) {
                // 백그라운드 시작 제한 등
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, UnlockService::class.java))
        }
    }
}
