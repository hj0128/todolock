package com.hj0128.todolock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * 잠금해제 감지. 경로를 두 개 둡니다.
 *
 *  1. ACTION_USER_PRESENT — 정석. 잠금을 실제로 푼 순간.
 *  2. ACTION_SCREEN_ON + 키가드 감시 — 1번이 오지 않는 기기 대비.
 *     화면이 켜진 뒤 키가드가 풀리는 순간을 직접 폴링해서 잡습니다.
 *
 * 둘 다 매니페스트 등록이 불가능하므로 UnlockService 가 런타임에 등록합니다.
 * 어느 경로로 잡혔는지 진단에 남겨서 원인을 추적할 수 있게 합니다.
 */
class UnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val ctx = context.applicationContext

        // 브로드캐스트가 도달했다는 사실 자체를 먼저 남깁니다.
        // 이 값이 갱신되지 않으면 리시버(=서비스)가 죽어 있었던 것입니다.
        TodoStore.markBroadcast(ctx, action)

        when (action) {
            Intent.ACTION_USER_PRESENT -> handle(ctx, "USER_PRESENT")
            Intent.ACTION_SCREEN_ON -> watchKeyguard(ctx)
        }
    }

    /**
     * 화면이 켜진 뒤 키가드가 풀리는 순간까지 최대 20초 감시합니다.
     *
     * 0.2초 간격입니다. 이 경로로 잡히는 기기에서는 폴링 간격이 그대로 팝업이
     * 늦는 시간이 되므로 짧게 둡니다. 화면이 켜져 있고 잠긴 동안에만 도는
     * 검사라(최대 100회) 전력에는 영향이 없습니다.
     */
    private fun watchKeyguard(ctx: Context) {
        val km = ctx.getSystemService(KeyguardManager::class.java) ?: return

        if (!km.isKeyguardLocked) {
            // 잠금이 걸려 있지 않은 기기/상황 — 화면 켜짐이 곧 '사용 시작'입니다.
            handle(ctx, "SCREEN_ON(잠금 없음)")
            return
        }

        val h = Handler(Looper.getMainLooper())
        var tries = 0
        val poll = object : Runnable {
            override fun run() {
                if (!km.isKeyguardLocked) {
                    handle(ctx, "SCREEN_ON+키가드해제")
                    return
                }
                if (++tries < 100) h.postDelayed(this, POLL_MS)
            }
        }
        h.postDelayed(poll, POLL_MS)
    }

    private fun handle(ctx: Context, via: String) {
        // 두 경로가 같은 잠금해제를 중복 처리하지 않도록 한 번만 통과시킵니다.
        if (!TodoStore.claimHandling(ctx)) return

        TodoStore.markUnlock(ctx, "감지됨 · 처리 중")

        when (TodoStore.decide(ctx)) {
            TodoStore.DECIDE_DISABLED -> return finish(ctx, via, "표시 안 함 · 스위치가 꺼져 있음")
            TodoStore.DECIDE_NO_TODOS -> return finish(ctx, via, "표시 안 함 · 오늘 남은 할 일 없음")
            TodoStore.DECIDE_FREQUENCY -> return finish(ctx, via, "표시 안 함 · 표시 빈도 제한")
        }

        TodoStore.markShown(ctx)
        val remaining = TodoStore.pendingToday(ctx).size

        // 기다리지 않고 곧바로 띄웁니다. 잠금을 푼 순간 이미 화면에 있어야
        // '잠금해제하면 할 일이 뜬다' 로 느껴집니다.
        //
        // 예전에는 런처가 자리를 잡도록 0.5초를 줬는데, 그만큼 팝업이 늦게 떠서
        // 홈 화면이 한 번 보였다가 덮이는 모양이 됐습니다. 혹시 기기에 따라
        // 팝업이 런처에 가려지면 여기서 다시 지연을 주면 됩니다(진단 로그의
        // '팝업 표시' 기록은 남지만 화면에 안 보이는 증상).
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
                finish(ctx, via, "팝업 표시")
            } catch (e: Exception) {
                Notifications.showFallbackAlert(ctx, remaining)
                finish(ctx, via, "팝업 차단됨 → 알림 (" + e.javaClass.simpleName + ")")
            }
        } else {
            Notifications.showFallbackAlert(ctx, remaining)
            finish(ctx, via, "알림으로 대체 · '다른 앱 위에 표시' 권한 없음")
        }
    }

    private fun finish(ctx: Context, via: String, result: String) {
        TodoStore.markUnlock(ctx, result)
        TodoStore.appendLog(ctx, via + " → " + result)
    }

    private companion object {
        /** 키가드 감시 간격. 이 값이 곧 팝업이 늦는 시간입니다. */
        const val POLL_MS = 200L
    }
}