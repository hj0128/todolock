package com.hj0128.todolock

import android.app.NotificationManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hj0128.todolock.databinding.ActivityReminderPopupBinding

/**
 * 미리 알림 방식이 '전체 팝업' 일 때 화면을 덮는 창.
 *
 * 알람에서 곧바로 액티비티를 띄우는 것은 백그라운드 실행 제한에 걸리지만,
 * 이 앱은 '다른 앱 위에 표시' 권한을 쓰고 있어(잠금해제 팝업과 같은 경로) 가능합니다.
 * 권한이 없으면 ReminderReceiver 가 실패를 삼키고 알림만 남습니다.
 */
class ReminderPopupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L
        val todo = if (id != 0L) TodoStore.load(this).firstOrNull { it.id == id } else null

        // 알림이 뜬 뒤 지워졌거나 이미 완료된 항목이면 띄우지 않습니다.
        if (todo == null || todo.done) {
            finish()
            return
        }

        val b = ActivityReminderPopupBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvText.text = todo.text
        b.tvWhen.text = TodoStore.prettyDue(todo) + " 기한"

        // 메모는 첫 줄만이 아니라 전문을 보여줍니다. 화면을 덮는 창이라 자리가 있습니다.
        b.tvMemo.visibility = if (todo.hasMemo) View.VISIBLE else View.GONE
        b.tvMemo.text = todo.memo

        b.btnDone.setOnClickListener {
            todo.done = true
            TodoStore.update(this, todo)
            Reminders.cancel(this, todo.id)
            clearNotification(todo.id)
            finish()
        }
        b.btnLater.setOnClickListener { finish() }
        b.scrim.setOnClickListener { finish() }
    }

    /** 팝업에서 처리했으면 알림창에 남은 같은 알림도 치웁니다. */
    private fun clearNotification(todoId: Long) {
        try {
            getSystemService(NotificationManager::class.java)
                ?.cancel(Notifications.reminderId(todoId))
        } catch (e: Exception) {
            // 알림 정리 실패가 완료 처리를 막지 않도록 무시합니다
        }
    }

    companion object {
        const val EXTRA_ID = "todo_id"
    }
}
