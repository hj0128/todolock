package com.hj0128.todolock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 감지 서비스를 되살리는 워치독.
 *
 * 삼성 One UI 등은 화면이 꺼진 동안 앱 프로세스를 재우거나 종료합니다.
 * UnlockReceiver 는 UnlockService 가 '런타임에' 등록하므로, 서비스가 죽으면
 * 잠금해제 시점에 리시버가 아예 존재하지 않게 됩니다.
 * (앱을 열면 onResume 이 서비스를 되살려서 진단에는 '실행 중' 으로 보이는 함정이 있습니다)
 *
 * 알람은 앱 프로세스가 죽어도 시스템 쪽에 남아 있으므로,
 * 프로세스를 깨워 서비스를 다시 세울 수 있는 유일한 경로입니다.
 */
class Watchdog : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext

        // 다음 알람을 먼저 예약합니다. 한 번 놓치면 사슬이 끊기므로 무엇보다 우선.
        arm(ctx)

        if (!TodoStore.isEnabled(ctx)) return

        if (!UnlockService.isRunning(ctx)) {
            TodoStore.appendLog(ctx, "워치독: 서비스가 죽어 있어 재시작")
            UnlockService.start(ctx)
        }
    }

    companion object {
        private const val ACTION = "com.hj0128.todolock.WATCHDOG"
        private const val REQUEST = 2000
        private const val INTERVAL_MS = 15L * 60L * 1000L

        private fun pending(ctx: Context): PendingIntent {
            val i = Intent(ctx, Watchdog::class.java).setAction(ACTION)
            return PendingIntent.getBroadcast(
                ctx, REQUEST, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * 정확한 알람(setExactAndAllowWhileIdle)은 SCHEDULE_EXACT_ALARM 권한이 필요해서 쓰지 않고,
         * Doze 중에도 깨어나는 setAndAllowWhileIdle 을 매번 다시 예약하는 방식으로 이어갑니다.
         * 정확도는 떨어지지만(수 분 오차) 서비스 부활에는 충분합니다.
         */
        fun arm(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + INTERVAL_MS,
                    pending(ctx)
                )
            } catch (e: Exception) {
                TodoStore.setServiceError(ctx, "알람 예약 실패 " + e.javaClass.simpleName)
            }
        }

        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            try {
                am.cancel(pending(ctx))
            } catch (e: Exception) {
                // 무시
            }
        }
    }
}