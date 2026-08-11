package com.hj0128.todolock

import android.app.NotificationManager
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
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
        // 고른 강조색을 입힙니다. setContentView 보다 먼저여야 합니다.
        ThemeConfig.apply(this)

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
        b.tvWhen.text = getString(R.string.due_label, TodoStore.prettyDue(this, todo))

        // 메모는 첫 줄만이 아니라 전문을 보여줍니다. 화면을 덮는 창이라 자리가 있습니다.
        b.tvMemo.visibility = if (todo.hasMemo) View.VISIBLE else View.GONE
        b.tvMemo.text = todo.memo

        // '확인할 때까지' 로 두면 소리가 이어지므로, 이 창이 곧 알람 화면입니다.
        // 그때만 미루기 버튼을 보여줍니다.
        val rings = TodoStore.ringsUntilChecked(this)
        b.btnSnooze.visibility = if (rings) View.VISIBLE else View.GONE
        b.btnSnooze.setOnClickListener {
            Reminders.cancelHush(this, id)
            stopRinging(id)
            Reminders.snooze(this, todo)
            finish()
        }

        // 닫는 길은 '닫기' 버튼입니다.
        //
        // 바깥(스크림)을 눌러도, 뒤로 가기를 해도 닫히지 않습니다. 이 창은 다른 일을
        // 하는 도중에 갑자기 뜨기 때문에, 하던 동작이 그대로 이어져 눌리면 내용을
        // 보지도 못한 채 사라집니다. 알림을 놓치지 않는 것이 이 창의 목적입니다.
        //
        // 갇히지는 않습니다 — '닫기' 는 항상 화면에 있고, 홈으로 나갈 수도 있습니다.
        //
        // 닫으면 소리도 멈춥니다. 이 창을 본 것이 곧 '확인' 이라, 창을 닫았는데도
        // 계속 울리면 어디서 멈추는지 알 수 없게 됩니다. 대신 알림창에는 그대로
        // 남겨서, 닫았다고 할 일을 잊지는 않게 합니다.
        b.btnLater.setOnClickListener {
            if (rings) {
                Reminders.cancelHush(this, id)
                Notifications.hushReminder(this, todo)
            }
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 아무것도 하지 않습니다 (뒤로 가기로 닫히지 않게)
            }
        })
    }

    /** 미루기는 알림 자체를 걷습니다 — 5분 뒤에 새로 뜹니다. */
    private fun stopRinging(id: Long) {
        getSystemService(NotificationManager::class.java)?.cancel(Notifications.reminderId(id))
    }

    companion object {
        const val EXTRA_ID = "todo_id"
    }
}
