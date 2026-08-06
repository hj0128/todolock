package com.example.todolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅 / 앱 업데이트 후 서비스 · 알람 · 위젯을 다시 살립니다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val ctx = context.applicationContext

            // 예약된 알람은 재부팅으로 사라집니다.
            // 잠금해제 팝업 스위치와 무관한 기능이므로 항상 다시 세웁니다.
            Reminders.rescheduleAll(ctx)

            // 재부팅·앱 교체 후 위젯은 initialLayout(빈 목록) 으로 되돌아갑니다.
            // 갱신은 지금까지 데이터가 바뀔 때만 했기 때문에, 아무 일도 없으면
            // 30분 주기 갱신이 돌 때까지 계속 비어 보였습니다.
            TodoWidget.refresh(ctx)

            if (TodoStore.isEnabled(ctx)) {
                UnlockService.start(ctx)
                Watchdog.arm(ctx)
                TodoStore.appendLog(ctx, "재부팅/업데이트 → 서비스 시작")
            }
        }
    }
}
