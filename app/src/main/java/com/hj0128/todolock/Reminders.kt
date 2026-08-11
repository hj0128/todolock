package com.hj0128.todolock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

/**
 * 할 일의 '미리 알림'을 AlarmManager 로 예약합니다.
 *
 * 알람은 앱 프로세스가 죽어도 시스템 쪽에 남지만 재부팅하면 사라지므로,
 * BootReceiver 와 앱 진입 시 rescheduleAll 로 다시 세웁니다.
 */
object Reminders {

    const val ACTION_FIRE = "com.hj0128.todolock.REMIND"
    const val ACTION_DONE = "com.hj0128.todolock.REMIND_DONE"
    const val ACTION_SNOOZE = "com.hj0128.todolock.REMIND_SNOOZE"

    /** 계속 울리던 것을 스스로 멈추는 시각에 옵니다. 알림은 그대로 남습니다. */
    const val ACTION_HUSH = "com.hj0128.todolock.REMIND_HUSH"

    const val EXTRA_ID = "todo_id"

    private const val REQ_FIRE = 3000
    private const val REQ_DONE = 4000
    private const val REQ_SNOOZE = 5000
    private const val REQ_HUSH = 6000

    /**
     * '확인할 때까지' 라도 이만큼 지나면 소리를 멈춥니다.
     *
     * 폰을 가방에 두고 나가면 아무도 누를 수 없는데, 그때까지 계속 울리면
     * 배터리를 태우고 주변 사람에게 민폐입니다. 알람 시계들이 쓰는 길이와
     * 같습니다. 소리만 멈추고 알림은 남으므로 나중에 봐도 사라지지 않습니다.
     */
    private const val RING_MS = 2L * 60L * 1000L

    /** '5분 뒤 다시' 의 5분. */
    private const val SNOOZE_MS = 5L * 60L * 1000L

    /**
     * 놓친 알림을 따라잡는 한도. 밤새 꺼져 있던 기기도 아침에 알려주되,
     * 며칠 전 알림이 갑자기 뜨는 일은 없게 합니다.
     */
    private const val CATCH_UP_MS = 12L * 60L * 60L * 1000L

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

    fun snoozePending(ctx: Context, id: Long): PendingIntent =
        pending(ctx, id, ACTION_SNOOZE, REQ_SNOOZE)

    private fun hushPending(ctx: Context, id: Long): PendingIntent =
        pending(ctx, id, ACTION_HUSH, REQ_HUSH)

    /**
     * 소리를 스스로 멈출 시각을 예약합니다. 계속 울리는 알림에만 씁니다.
     *
     * 정확 알람으로 걸지 않습니다 — 몇 분 늦게 멈춰도 손해가 없고, 이런 것까지
     * 정확 알람을 쓰면 시스템이 앱에 주는 몫을 알림 쪽에서 축내게 됩니다.
     */
    fun scheduleHush(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + RING_MS, hushPending(ctx, id)
            )
        } catch (e: Exception) {
            TodoStore.appendLog(ctx, "멈춤 예약 실패 " + e.javaClass.simpleName)
        }
    }

    fun cancelHush(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.cancel(hushPending(ctx, id))
        } catch (e: Exception) {
            // 취소 실패는 무해합니다
        }
    }

    /**
     * '5분 뒤 다시'. 알림 시각 자체를 뒤로 미뤄 예약을 다시 겁니다.
     *
     * 따로 미루기용 알람을 두지 않는 이유: 그러면 할 일에 적힌 알림 시각과
     * 실제로 울릴 시각이 어긋나, 앱에서 본 시각과 울리는 시각이 달라집니다.
     */
    fun snooze(ctx: Context, todo: Todo) {
        todo.remindAt = System.currentTimeMillis() + SNOOZE_MS
        // 다시 울려야 하므로 '이미 알렸음' 을 지웁니다.
        todo.notified = false
        TodoStore.update(ctx, todo)
        schedule(ctx, todo)
    }

    /**
     * 예약을 항상 먼저 취소하므로, 완료 처리나 알림 해제에도 같은 함수를 쓸 수 있습니다.
     * 이미 지난 시각과 완료된 항목은 예약하지 않습니다.
     *
     * @param catchUp 이미 지난 알림을 지금이라도 띄울지.
     *   알람 복구(부팅·앱 진입) 에서만 켭니다. 사용자가 시트에서 일부러 지난 시각을
     *   고른 경우까지 즉시 알리면 놀라게 되므로 기본값은 끔입니다.
     * @return 알람을 실제로 걸었는지. 호출한 쪽이 사용자에게 잘못된 안내를 하지 않도록.
     */
    fun schedule(ctx: Context, todo: Todo, catchUp: Boolean = false): Boolean {
        cancel(ctx, todo.id)
        if (todo.done || !todo.hasReminder) return false

        val at = todo.remindAt
        val now = System.currentTimeMillis()
        if (at <= now) {
            // 놓친 알림 따라잡기.
            // 기기가 꺼져 있었거나 앱이 교체되면 예약된 알람은 사라집니다.
            // 아직 알리지 않았고 너무 오래되지 않았으면, 지금이라도 알려줍니다.
            if (catchUp && !todo.notified && now - at <= CATCH_UP_MS) notifyNow(ctx, todo)
            return false
        }

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

    /** 부팅·앱 진입에서 알람을 되살립니다. 이 경로에서만 놓친 알림을 따라잡습니다. */
    fun rescheduleAll(ctx: Context) {
        for (t in TodoStore.load(ctx)) schedule(ctx, t, catchUp = true)
    }

    /** 지난 알림을 지금 띄우고, 다시 뜨지 않도록 표시해 둡니다. */
    private fun notifyNow(ctx: Context, todo: Todo) {
        Notifications.showReminder(ctx, todo)
        todo.notified = true
        TodoStore.update(ctx, todo)
        TodoStore.appendLog(ctx, "놓친 알림 표시: " + todo.text)
    }

    /**
     * 알림을 실제로 걸었는지 사용자에게 알립니다. 시트로 저장하는 화면이 모두 씁니다.
     *
     * 예약이 조용히 실패하면 사용자는 알 길이 없으므로, 걸리지 않은 이유(지난 시각)와
     * 제 시각을 못 맞출 수 있다는 사실(정확 알람 권한 없음)까지 여기서 말합니다.
     *
     * @param scheduled schedule() 이 돌려준 값
     */
    fun announce(ctx: Context, todo: Todo, scheduled: Boolean) {
        if (!todo.hasReminder) return

        val at = TodoStore.prettyDateTime(ctx, todo.remindAt)
        val msg = if (!scheduled) {
            ctx.getString(R.string.remind_past, at)
        } else {
            ctx.getString(R.string.remind_scheduled, at) +
                (if (canBeExact(ctx)) "" else ctx.getString(R.string.remind_inexact)) +
                (if (TodoStore.isRemindAfterDue(todo)) {
                    ctx.getString(R.string.remind_after_due_suffix)
                } else "")
        }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
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
