package com.hj0128.todolock

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 홈 화면 위젯에서 앱을 열지 않고 바로 추가·수정하기 위한 투명 액티비티.
 *
 * 위젯 자체에는 입력칸을 둘 수 없습니다(RemoteViews 는 EditText 를 지원하지 않습니다).
 * 그래서 배경이 투명한 액티비티를 띄워 시트만 보이게 하고, 시트가 닫히면 스스로 끝납니다.
 * 사용자에게는 홈 화면 위에 입력창이 뜬 것처럼 보입니다.
 */
class QuickAddActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val editId = intent?.getLongExtra(EXTRA_EDIT_ID, 0L) ?: 0L
        val existing =
            if (editId != 0L) TodoStore.load(this).firstOrNull { it.id == editId } else null

        // 수정하려던 항목이 이미 지워졌으면 조용히 닫습니다.
        if (editId != 0L && existing == null) {
            finish()
            return
        }

        AddTodoSheet(this, existing) { todo ->
            TodoStore.upsert(this, todo)
            announce(todo, Reminders.schedule(this, todo))
        }.show(onDismiss = { finish() })
    }

    /** 실제로 알람이 걸렸을 때만 알려준다고 말합니다. */
    private fun announce(todo: Todo, scheduled: Boolean) {
        if (!todo.hasReminder) return

        val at = TodoStore.prettyDateTime(todo.remindAt)
        val msg = if (!scheduled) {
            at + " — 이미 지난 시각이라 알림을 걸지 않았습니다"
        } else {
            at + "에 알려드립니다" +
                (if (Reminders.canBeExact(this)) "" else " (권한이 없어 몇 분 늦을 수 있음)")
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** 값이 있으면 그 할 일을 수정, 없으면 새로 추가 */
        const val EXTRA_EDIT_ID = "edit_id"
    }
}
