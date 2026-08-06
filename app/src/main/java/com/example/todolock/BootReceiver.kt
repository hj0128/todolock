package com.example.todolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅 / 앱 업데이트 후 서비스를 다시 살립니다. */
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

            if (TodoStore.isEnabled(ctx)) {
                UnlockService.start(ctx)
                Watchdog.arm(ctx)
                TodoStore.appendLog(ctx, "재부팅/업데이트 → 서비스 시작")
            }
        }
    }
}
