package com.example.todolock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * 할 일의 '미리 알림'을 AlarmManager 로 예약합니다.
 *
 * 알람은 앱 프로세스가 죽어도 시스템 쪽에 남지만 재부팅하면 사라지므로,
 * BootReceiver 와 앱 진입 시 rescheduleAll 로 다시 세웁니다.
 */
object Reminders {

    const val ACTION_FIRE = "com.example.todolock.REMIND"
    const val ACTION_DONE = "com.example.todolock.REMIND_DONE"
    const val EXTRA_ID = "todo_id"

    private const val REQ_FIRE = 3000
    private const val REQ_DONE = 4000

    /**
     * PendingIntent 는 extras 를 비교하지 않으므로, data URI 로 할 일을 구분해야
     * 서로 다른 할 일의 알람이 덮어써지지 않습니다.
     */
    private fun pending(ctx: Context, id: Long, action: String, request: Int): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("todolock://todo/" + id))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, request, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun donePending(ctx: Context, id: Long): PendingIntent =
        pending(ctx, id, ACTION_DONE, REQ_DONE)

    /**
     * 예약을 항상 먼저 취소하므로, 완료 처리나 알림 해제에도 같은 함수를 쓸 수 있습니다.
     * 이미 지난 시각과 완료된 항목은 예약하지 않습니다.
     *
     * @return 알람을 실제로 걸었는지. 호출한 쪽이 사용자에게 잘못된 안내를 하지 않도록.
     */
    fun schedule(ctx: Context, todo: Todo): Boolean {
        cancel(ctx, todo.id)
        if (todo.done || !todo.hasReminder) return false

        val at = todo.remindAt
        if (at <= System.currentTimeMillis()) return false

        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        try {
            if (canBeExact(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, firePending(ctx, todo.id))
            } else {
                // 정확 알람 권한이 없으면 몇 분 늦을 수 있지만 Doze 중에도 깨어납니다.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, firePending(ctx, todo.id))
            }
        } catch (e: Exception) {
            TodoStore.appendLog(ctx, "알림 예약 실패 " + e.javaClass.simpleName)
            return false
        }
        return true
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(firePending(ctx, id))
        } catch (e: Exception) {
            // 취소 실패는 무해합니다
        }
    }

    fun rescheduleAll(ctx: Context) {
        for (t in TodoStore.load(ctx)) schedule(ctx, t)
    }

    /** Android 12+ 는 정확 알람에 별도 권한이 필요합니다. */
    fun canBeExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun firePending(ctx: Context, id: Long): PendingIntent =
        pending(ctx, id, ACTION_FIRE, REQ_FIRE)
}
