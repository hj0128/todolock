package com.example.todolock

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.todolock.databinding.ActivityPopupBinding
import com.example.todolock.databinding.ItemTodoBinding

/**
 * 잠금해제 직후 뜨는 '오늘의 할 일' 화면.
 * 행 수가 적어 RecyclerView 대신 ScrollView 안에 직접 붙입니다(레이아웃 예측이 쉬움).
 */
class TodayPopupActivity : AppCompatActivity() {

    private lateinit var b: ActivityPopupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 오늘 등록된 할 일이 아예 없으면 아무것도 띄우지 않습니다.
        if (TodoStore.forDate(this, TodoStore.today()).isEmpty()) {
            finish()
            return
        }

        b = ActivityPopupBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnClose.setOnClickListener { finish() }
        b.btnOpenApp.setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
        }
        b.scrim.setOnClickListener { finish() }

        render()
    }

    private fun render() {
        val items = TodoStore.forDate(this, TodoStore.today()).sortedBy { it.done }
        val remaining = items.count { !it.done }

        b.container.removeAllViews()
        for (todo in items) {
            val row = ItemTodoBinding.inflate(layoutInflater, b.container, false)
            TodoRow.bind(row, todo, onToggle = {
                TodoStore.update(this, it)
                b.root.post { if (!isFinishing) render() }
            }, onDelete = null)
            b.container.addView(row.root)
        }

        if (remaining == 0) {
            b.tvCount.text = "오늘 할 일 전부 끝냈어요 🎉"
            b.root.postDelayed({ if (!isFinishing) finish() }, 1100L)
        } else {
            b.tvCount.text = "남은 할 일 " + remaining + "개"
        }
    }
}
