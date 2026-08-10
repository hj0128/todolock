package com.hj0128.todolock

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hj0128.todolock.databinding.ActivityPopupBinding
import com.hj0128.todolock.databinding.ItemPopupRowBinding

/**
 * 잠금해제 직후 뜨는 '오늘의 할 일' 화면.
 * 행 수가 적어 RecyclerView 대신 ScrollView 안에 직접 붙입니다(레이아웃 예측이 쉬움).
 */
class TodayPopupActivity : AppCompatActivity() {

    private lateinit var b: ActivityPopupBinding

    /** 설정의 '팝업 미리보기' 로 열렸는지. 할 일이 없어도 닫지 않고 예시를 보여줍니다. */
    private var preview = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 고른 강조색을 입힙니다. setContentView 보다 먼저여야 합니다.
        ThemeConfig.apply(this)

        preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true

        // 완료된 것은 보여주지 않으므로, 오늘 '남은' 할 일이 없으면 아무것도 띄우지 않습니다.
        // 미리보기는 예외입니다 — 보러 왔는데 아무것도 안 뜨면 볼 수가 없습니다.
        if (!preview && TodoStore.pendingToday(this).isEmpty()) {
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
        // 순서는 목록 화면과 같은 비교자를 씁니다(모두 같은 날이라 날짜 항목은 무의미).
        val real = TodoStore.pendingToday(this).sortedWith(TodoStore.pendingOrder)

        // 미리보기인데 오늘 것이 없으면 예시로 채웁니다. 진짜 할 일이 있으면
        // 그쪽이 낫습니다 — 예시보다 자기 것으로 보는 편이 정확합니다.
        val sample = preview && real.isEmpty()
        val items = if (sample) sampleItems() else real

        b.tvCount.text =
            if (sample) "미리보기 · 아래는 예시입니다" else "남은 할 일 " + items.size + "개"

        b.container.removeAllViews()
        for (todo in items) {
            // 확인 전용 행. 체크박스는 두지 않고 점으로 기준선만 잡습니다.
            val row = ItemPopupRowBinding.inflate(layoutInflater, b.container, false)
            row.pTitle.text = todo.text

            // 기한 '날짜' 는 모두 오늘이라 생략합니다.
            // 시각을 정했으면 그것만 남기고(잠금해제 직후에 가장 급한 정보입니다),
            // 미리 알림이 있으면 뒤에 붙입니다.
            val sub = mutableListOf<String>()
            if (todo.hasDueTime) sub.add(TodoStore.formatMinutes(todo.dueMinutes) + "까지")
            if (todo.hasReminder) sub.add("🔔 " + TodoStore.prettyDateTime(todo.remindAt))

            if (sub.isEmpty()) {
                row.pSub.visibility = View.GONE
            } else {
                row.pSub.visibility = View.VISIBLE
                row.pSub.text = sub.joinToString(" · ")
            }

            // 메모 첫 줄. 앱을 열지 않고도 무엇을 해야 하는지 보이게 합니다.
            if (todo.hasMemo) {
                row.pMemo.visibility = View.VISIBLE
                row.pMemo.text = TodoStore.memoLine(todo)
            } else {
                row.pMemo.visibility = View.GONE
            }

            // GONE 이 아니라 INVISIBLE 입니다. 자리를 비워 두지 않으면 별표 유무에 따라
            // 알림 시각이 좌우로 밀려서 세로로 정렬되지 않습니다.
            row.pStar.visibility = if (todo.important) View.VISIBLE else View.INVISIBLE

            b.container.addView(row.root)
        }
    }

    /**
     * 오늘 남은 할 일이 없을 때 미리보기에 채워 넣는 예시.
     *
     * 저장하지 않고 이 화면에만 씁니다. 시각 · 미리 알림 · 메모 · 중요 표시가
     * 한 번에 보이도록 골랐습니다 — 색을 바꾼 뒤 무엇이 어떻게 보이는지
     * 확인하려는 것이 이 화면의 목적이기 때문입니다.
     */
    private fun sampleItems(): List<Todo> {
        val today = TodoStore.today()
        return listOf(
            Todo(
                id = 0L, text = "장 보러 가기", date = today,
                dueMinutes = 18 * 60, important = true
            ),
            Todo(
                id = 0L, text = "약 먹기", date = today,
                remindAt = TodoStore.dueMillis(today, 21 * 60)
            ),
            Todo(
                id = 0L, text = "전기요금 내기", date = today,
                memo = "지난달 고지서 확인"
            )
        )
    }

    companion object {
        /** 설정의 '팝업 미리보기' 로 여는 경우 */
        const val EXTRA_PREVIEW = "preview"
    }
}
