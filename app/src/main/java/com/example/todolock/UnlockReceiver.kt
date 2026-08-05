package com.example.todolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * ACTION_USER_PRESENT = 사용자가 실제로 잠금을 해제한 순간.
 * Android 8+ 에서는 매니페스트 등록으로 받을 수 없으므로
 * UnlockService 가 런타임에 등록합니다.
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val ctx = context.applicationContext
        if (!TodoStore.shouldShowNow(ctx)) return

        TodoStore.markShown(ctx)
        val remaining = TodoStore.pendingToday(ctx).size

        // 런처가 자리를 잡을 시간을 조금 준 뒤 띄웁니다.
        Handler(Looper.getMainLooper()).postDelayed({
            if (Settings.canDrawOverlays(ctx)) {
                val i = Intent(ctx, TodayPopupActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                }
                try {
                    ctx.startActivity(i)
                } catch (e: Exception) {
                    Notifications.showFallbackAlert(ctx, remaining)
                }
            } else {
                Notifications.showFallbackAlert(ctx, remaining)
            }
        }, 500L)
    }
}
