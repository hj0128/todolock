package com.example.todolock

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 예약된 미리 알림이 울릴 때, 그리고 그 알림의 '완료' 버튼을 눌렀을 때. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val id = intent.getLongExtra(Reminders.EXTRA_ID, 0L)
        if (id == 0L) return

        // 알람이 예약된 뒤 삭제된 할 일일 수 있습니다.
        val todo = TodoStore.load(ctx).firstOrNull { it.id == id } ?: return

        if (intent.action == Reminders.ACTION_DONE) {
            todo.done = true
            TodoStore.update(ctx, todo)
            Reminders.cancel(ctx, id)
            ctx.getSystemService(NotificationManager::class.java)
                ?.cancel(Notifications.reminderId(id))
            TodoStore.appendLog(ctx, "알림에서 완료: " + todo.text)
            return
        }

        if (todo.done) return

        // 알림창 항목은 어느 방식이든 남깁니다. 전체 팝업이 막혀도 놓치지 않도록.
        Notifications.showReminder(ctx, todo)

        if (TodoStore.getRemindStyle(ctx) == TodoStore.REMIND_POPUP) {
            try {
                ctx.startActivity(
                    Intent(ctx, ReminderPopupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(ReminderPopupActivity.EXTRA_ID, id)
                )
            } catch (e: Exception) {
                // '다른 앱 위에 표시' 권한이 없으면 팝업은 막힙니다. 알림은 이미 떴습니다.
                TodoStore.appendLog(ctx, "알림 팝업 실패 " + e.javaClass.simpleName)
            }
        }

        // 이미 알렸다고 남겨야 '놓친 알림 따라잡기' 가 중복으로 띄우지 않습니다.
        todo.notified = true
        TodoStore.update(ctx, todo)
        TodoStore.appendLog(ctx, "미리 알림: " + todo.text)
    }
}
