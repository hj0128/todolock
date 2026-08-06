package com.example.todolock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifications {

    const val CHANNEL_SERVICE = "todolock_service"
    const val CHANNEL_ALERT = "todolock_alert"
    const val CHANNEL_REMIND = "todolock_remind"

    /**
     * '알림창만' 용 채널.
     * 채널 중요도는 한 번 만들어지면 코드로 바꿀 수 없으므로(생성 후엔 사용자 소유),
     * 헤드업 없는 모드는 중요도가 다른 별도 채널로 둬야 합니다.
     */
    const val CHANNEL_REMIND_QUIET = "todolock_remind_quiet"
    const val ID_SERVICE = 1001
    const val ID_ALERT = 1002

    /**
     * 미리 알림은 할 일마다 따로 떠야 하므로 id 를 할 일 id 에서 만듭니다.
     * 2000 부터 시작해 위의 고정 id 와 겹치지 않습니다.
     */
    fun reminderId(todoId: Long): Int = 2000 + (todoId % 100000L).toInt()

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        val service = NotificationChannel(
            CHANNEL_SERVICE,
            "잠금해제 감지",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "잠금해제를 감지하기 위해 실행 중인 상태 표시"
            setShowBadge(false)
        }
        nm.createNotificationChannel(service)

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "오늘의 할 일 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "잠금해제 시 오늘 할 일을 알려줍니다"
        }
        nm.createNotificationChannel(alert)

        val remind = NotificationChannel(
            CHANNEL_REMIND,
            "할 일 미리 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "설정한 기한보다 미리 알려줍니다"
        }
        nm.createNotificationChannel(remind)

        // 이름·설명은 생성 후에도 바꿀 수 있습니다(중요도만 고정).
        // '알림창' 과 '전체 팝업' 두 방식이 이 채널을 공유하므로,
        // 특정 방식 이름을 붙이지 않고 성질(헤드업 없음)로 표현합니다.
        val remindQuiet = NotificationChannel(
            CHANNEL_REMIND_QUIET,
            "할 일 미리 알림 (조용히)",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "화면 위에 배너로 띄우지 않습니다"
        }
        nm.createNotificationChannel(remindQuiet)
    }

    /** 기한 전에 울리는 개별 할 일 알림. '완료' 를 누르면 앱을 열지 않고 바로 처리됩니다. */
    fun showReminder(ctx: Context, todo: Todo) {
        ensureChannels(ctx)

        val open = PendingIntent.getActivity(
            ctx, reminderId(todo.id),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val due = TodoStore.prettyDate(todo.date) + " 기한"

        // 헤드업은 '헤드업' 방식에서만 띄웁니다.
        // '전체 팝업' 은 팝업이 주역이라 헤드업까지 뜨면 같은 알림이 두 번 보입니다.
        // 그래도 알림창 항목은 남겨서, 팝업이 막혔을 때의 대비책과 '완료' 버튼을 유지합니다.
        val headsUp = TodoStore.getRemindStyle(ctx) == TodoStore.REMIND_HEADS_UP

        val n = NotificationCompat.Builder(
            ctx,
            if (headsUp) CHANNEL_REMIND else CHANNEL_REMIND_QUIET
        )
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(todo.text)
            .setContentText(due)
            .setPriority(
                if (headsUp) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "완료", Reminders.donePending(ctx, todo.id))
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(reminderId(todo.id), n)
        } catch (e: SecurityException) {
            // 알림 권한 없음
        }
    }

    fun serviceNotification(ctx: Context): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("TodoLock 실행 중")
            .setContentText("잠금해제하면 오늘 할 일을 보여줍니다")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    /**
     * '다른 앱 위에 표시' 권한이 없어 백그라운드에서 화면을 띄울 수 없을 때의 대체 수단.
     * 전체화면 인텐트로 시도하고, 막히면 헤드업 알림으로 표시됩니다.
     */
    fun showFallbackAlert(ctx: Context, remaining: Int) {
        ensureChannels(ctx)
        val intent = Intent(ctx, TodayPopupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            ctx, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("오늘 할 일 " + remaining + "개 남았어요")
            .setContentText("눌러서 확인하기")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java)?.notify(ID_ALERT, n)
        } catch (e: SecurityException) {
            // 알림 권한 없음
        }
    }
}
