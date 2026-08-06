package com.example.todolock

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.todolock.databinding.ActivityPopupBinding
import com.example.todolock.databinding.ItemPopupRowBinding

/**
 * 잠금해제 직후 뜨는 '오늘의 할 일' 화면.
 * 행 수가 적어 RecyclerView 대신 ScrollView 안에 직접 붙입니다(레이아웃 예측이 쉬움).
 */
class TodayPopupActivity : AppCompatActivity() {

    private lateinit var b: ActivityPopupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 완료된 것은 보여주지 않으므로, 오늘 '남은' 할 일이 없으면 아무것도 띄우지 않습니다.
        if (TodoStore.pendingToday(this).isEmpty()) {
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
        // 오늘 남은 할 일만. 완료된 것은 확인할 필요가 없어 감춥니다.
        // 팝업은 오늘 것만 보여주므로 날짜도 감춥니다.
        // 목록 화면과 같은 우선순위: 중요 → 미리 알림 이른 순 → 등록순.
        val items = TodoStore.pendingToday(this)
            .sortedWith(
                compareByDescending<Todo> { it.important }
                    .thenBy { if (it.hasReminder) it.remindAt else Long.MAX_VALUE }
                    .thenBy { it.id }
            )

        b.tvCount.text = "남은 할 일 " + items.size + "개"

        // 위젯 항목 간격(widget_item.xml 의 layout_marginBottom)과 같은 값
        val gap = (2 * resources.displayMetrics.density).toInt()

        b.container.removeAllViews()
        for (todo in items) {
            // 위젯 행과 같은 구조의 전용 레이아웃. 확인 전용이라 누를 것은 없습니다.
            val row = ItemPopupRowBinding.inflate(layoutInflater, b.container, false)
            row.pTitle.text = todo.text

            // 기한은 모두 오늘이라 생략하고, 미리 알림만 보여줍니다.
            if (todo.hasReminder) {
                row.pSub.visibility = View.VISIBLE
                row.pSub.text = "🔔 " + TodoStore.prettyRemindShort(todo)
            } else {
                row.pSub.visibility = View.GONE
            }

            row.pStar.visibility = if (todo.important) View.VISIBLE else View.GONE

            // 위젯과 같은 층 구조: 목록 판 위에 항목 판이 떠 있게 보이도록.
            row.root.setBackgroundResource(R.drawable.popup_item_panel)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = gap
            b.container.addView(row.root, lp)
        }
    }
}
