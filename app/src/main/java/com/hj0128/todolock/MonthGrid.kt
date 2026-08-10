package com.hj0128.todolock

import android.content.Context
import java.util.Calendar

/**
 * 한 달 격자를 만드는 계산.
 *
 * 앱의 달력 화면과 홈 화면 위젯이 같은 규칙을 써야 합니다 — 줄 수를 세는 법이나
 * 칸에 적는 차례가 서로 다르면 같은 달이 두 군데서 다르게 보입니다.
 */
object MonthGrid {

    const val COLUMNS = 7

    /**
     * 그 달의 칸들을 만듭니다.
     *
     * 첫 줄을 채우려 앞 달 며칠을, 마지막 줄을 채우려 다음 달 며칠을 함께 넣습니다.
     * 줄 수는 달마다 계산합니다 — 여섯 줄로 고정하면 다섯 줄이면 되는 달에 빈 줄이
     * 하나 남습니다.
     *
     * @param maxEntries 칸에 담아 둘 할 일 수. 위젯처럼 글을 적지 않는 쪽은 0.
     */
    fun build(month: Calendar, byDate: Map<String, List<Todo>>, maxEntries: Int): List<Day> {
        val cur = Calendar.getInstance()
        cur.time = month.time
        cur.set(Calendar.DAY_OF_MONTH, 1)

        val monthIndex = cur.get(Calendar.MONTH)
        val daysInMonth = cur.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 1일이 무슨 요일인지 = 앞에 채워야 할 칸 수 (일요일이 0)
        val lead = cur.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val cells = ((lead + daysInMonth + COLUMNS - 1) / COLUMNS) * COLUMNS
        cur.add(Calendar.DAY_OF_MONTH, -lead)

        val out = ArrayList<Day>(cells)
        repeat(cells) {
            val key = TodoStore.format(cur)
            val items = byDate[key].orEmpty()
            // 칸에 적는 차례는 목록과 같습니다 — 남은 것 먼저, 완료는 뒤로.
            val ordered = if (maxEntries <= 0) {
                emptyList()
            } else {
                items.filter { !it.done }.sortedWith(TodoStore.pendingOrder) +
                    items.filter { it.done }
            }
            out.add(
                Day(
                    key = key,
                    dayOfMonth = cur.get(Calendar.DAY_OF_MONTH),
                    inMonth = cur.get(Calendar.MONTH) == monthIndex,
                    pending = items.count { !it.done },
                    done = items.count { it.done },
                    overdue = items.any { TodoStore.isOverdue(it) },
                    entries = ordered.take(maxEntries).map {
                        Day.Entry(it.text, it.done, TodoStore.isOverdue(it))
                    },
                    total = items.size
                )
            )
            cur.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    /** 저장소를 한 번만 읽어 그 달을 만듭니다. */
    fun build(ctx: Context, month: Calendar, maxEntries: Int): List<Day> =
        build(month, TodoStore.byDate(ctx), maxEntries)
}
