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
 *
 * 이 서비스가 죽으면 UnlockReceiver 도 함께 사라져 잠금해제를 못 받습니다.
 * (삼성 One UI 등에서 절전 대상이 되면 실제로 죽습니다)
 * 그래서 시작·종료 시각과 실패 사유를 남겨 앱에서 확인할 수 있게 합니다.
 */
class UnlockService : Service() {

    private val receiver = UnlockReceiver()
    private var registered = false

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        promote()
        ensureReceiver()
        TodoStore.markServiceStarted(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote()
        // 프로세스가 재기동되어 onCreate 없이 여기로 들어오는 경우까지 대비합니다.
        ensureReceiver()
        TodoStore.markServiceStarted(this)
        return START_STICKY
    }

    /** 포그라운드로 승격. 실패 사유를 삼키지 않고 기록합니다. */
    private fun promote() {
        try {
            startForeground(Notifications.ID_SERVICE, Notifications.serviceNotification(this))
            TodoStore.setServiceError(this, null)
        } catch (e: Exception) {
            TodoStore.setServiceError(this, e.javaClass.simpleName + ": " + e.message)
        }
    }

    private fun ensureReceiver() {
        if (registered) return
        try {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registered = true
        } catch (e: Exception) {
            TodoStore.setServiceError(this, "리시버 등록 실패 " + e.javaClass.simpleName)
        }
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
        TodoStore.markServiceStopped(this)
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
                // 백그라운드 시작 제한 등 — 조용히 실패하면 원인을 알 수 없으므로 남깁니다.
                TodoStore.setServiceError(ctx, "서비스 시작 실패 " + e.javaClass.simpleName)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, UnlockService::class.java))
        }

        /** 실제로 살아 있는지 확인. O+ 에서는 자기 앱 서비스만 조회되므로 신뢰할 수 있습니다. */
        fun isRunning(ctx: Context): Boolean {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java) ?: return false
            return try {
                @Suppress("DEPRECATION")
                am.getRunningServices(64).any {
                    it.service.className == UnlockService::class.java.name
                }
            } catch (e: Exception) {
                false
            }
        }
    }
}