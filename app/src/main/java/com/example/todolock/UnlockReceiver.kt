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
 *
 * 어느 단계에서 막혔는지 앱 진단 화면에서 보이도록 처리 결과를 매번 기록합니다.
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val ctx = context.applicationContext

        // 브로드캐스트가 도달했다는 사실 자체를 먼저 남깁니다.
        // (이 줄이 갱신되지 않으면 서비스/리시버가 죽은 것입니다)
        TodoStore.markUnlock(ctx, "감지됨 · 처리 중")

        when (TodoStore.decide(ctx)) {
            TodoStore.DECIDE_DISABLED -> {
                TodoStore.markUnlock(ctx, "표시 안 함 · 스위치가 꺼져 있음")
                return
            }
            TodoStore.DECIDE_NO_TODOS -> {
                TodoStore.markUnlock(ctx, "표시 안 함 · 오늘 남은 할 일 없음")
                return
            }
            TodoStore.DECIDE_FREQUENCY -> {
                TodoStore.markUnlock(ctx, "표시 안 함 · 표시 빈도 제한")
                return
            }
        }

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
                    TodoStore.markUnlock(ctx, "팝업 표시")
                } catch (e: Exception) {
                    Notifications.showFallbackAlert(ctx, remaining)
                    TodoStore.markUnlock(ctx, "팝업 차단됨 → 알림 (" + e.javaClass.simpleName + ")")
                }
            } else {
                Notifications.showFallbackAlert(ctx, remaining)
                TodoStore.markUnlock(ctx, "알림으로 대체 · '다른 앱 위에 표시' 권한 없음")
            }
        }, 500L)
    }
}