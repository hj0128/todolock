package com.example.todolock

import android.content.Intent
import android.os.Bundle
import android.view.View
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

        b.container.removeAllViews()
        for (todo in items) {
            // 확인 전용 행. 체크박스는 두지 않고 점으로 기준선만 잡습니다.
            val row = ItemPopupRowBinding.inflate(layoutInflater, b.container, false)
            row.pTitle.text = todo.text

            // 기한은 모두 오늘이라 생략하고, 미리 알림만 보여줍니다.
            if (todo.hasReminder) {
                row.pSub.visibility = View.VISIBLE
                row.pSub.text = "🔔 " + TodoStore.prettyRemindShort(todo)
            } else {
                row.pSub.visibility = View.GONE
            }

            // GONE 이 아니라 INVISIBLE 입니다. 자리를 비워 두지 않으면 별표 유무에 따라
            // 알림 시각이 좌우로 밀려서 세로로 정렬되지 않습니다.
            row.pStar.visibility = if (todo.important) View.VISIBLE else View.INVISIBLE

            b.container.addView(row.root)
        }
    }
}
