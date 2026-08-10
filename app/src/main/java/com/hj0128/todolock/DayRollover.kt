package com.hj0128.todolock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 시간이 지나서 위젯의 글자가 달라지는 순간에 한 번씩 깨어나 다시 그립니다.
 *
 * 위젯은 만들어 둔 화면을 런처가 들고 있을 뿐이라, 시간이 흘러도 스스로 바뀌지
 * 않습니다. 9일에 10일치를 적으면 '내일' 로 그려지는데 10일이 되어도 계속
 * '내일' 이었고, 앱을 열어야만 고쳐졌습니다.
 *
 * 데이터가 바뀔 때는 TodoStore.save 가 이미 위젯을 다시 그립니다. 여기서 다루는
 * 것은 아무것도 하지 않았는데 화면이 달라져야 하는 두 순간뿐입니다.
 *
 *   자정        — '내일' 이 '오늘' 이 되고, 어제 것이 지난 것이 됩니다
 *   기한 시각   — 오늘 기한이 지나 그 줄이 빨갛게 바뀝니다
 *
 * 30분마다 한 번씩 깨워 확인하던 것을 이 방식으로 바꿨습니다. 하루 마흔여덟 번
 * 깨워 대부분은 바뀐 것이 없다고 확인하고 마는 대신, 바뀌는 순간에만 깨웁니다.
 */
class DayRollover : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // refresh 안에서 다음 순간을 다시 잡습니다.
        TodoWidget.refresh(context.applicationContext)
    }

    companion object {

        /**
         * 화면이 다음으로 달라지는 순간에 깨어나도록 잡습니다.
         * 이미 잡혀 있으면 갈아 끼웁니다.
         *
         * 정확 알람을 쓰지 않습니다 — 화면 글자를 고쳐 쓰는 일이라 몇 분 늦어도
         * 문제가 없습니다. 정확 알람은 실제로 우는 미리 알림 쪽에만 씁니다.
         */
        fun arm(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextChange(ctx), pending(ctx))
            } catch (e: Exception) {
                // 잡지 못해도 앱을 열면 그때 다시 맞춰집니다
            }
        }

        /** 위젯이 하나도 남지 않으면 깨울 이유도 없습니다. */
        fun cancel(ctx: Context) {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx))
        }

        private fun nextChange(ctx: Context): Long {
            val now = System.currentTimeMillis()
            val today = TodoStore.today()

            // 오늘 기한 시각이 지나면 그 줄이 빨갛게 바뀝니다. 그중 가장 이른 것.
            val nextDue = TodoStore.load(ctx)
                .filter { !it.done && it.date == today && it.hasDueTime }
                .map { TodoStore.dueMillis(it.date, it.dueMinutes) }
                .filter { it > now }
                .minOrNull()

            return minOf(nextMidnight(), nextDue ?: Long.MAX_VALUE)
        }

        private fun nextMidnight(): Long {
            val at = Calendar.getInstance()
            at.add(Calendar.DAY_OF_YEAR, 1)
            at.set(Calendar.HOUR_OF_DAY, 0)
            at.set(Calendar.MINUTE, 0)
            // 정각이 아니라 몇 초 뒤로 둡니다. 정각에 깨면 기기 시계에 따라
            // 아직 어제로 읽히는 순간이 있습니다.
            at.set(Calendar.SECOND, 5)
            at.set(Calendar.MILLISECOND, 0)
            return at.timeInMillis
        }

        private fun pending(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, REQ,
                Intent(ctx, DayRollover::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private const val REQ = 700
    }
}
