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
            if (TodoStore.isEnabled(context)) {
                UnlockService.start(context.applicationContext)
            }
        }
    }
}
