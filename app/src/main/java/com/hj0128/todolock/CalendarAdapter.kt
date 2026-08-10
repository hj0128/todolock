package com.hj0128.todolock

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * 달력 한 칸.
 *
 * 앞뒤 달의 날짜도 자리를 채우려고 함께 만듭니다(inMonth=false). 그 칸도 누를 수
 * 있고, 누르면 그 달로 넘어갑니다 — 30일과 1일이 나란히 보이는데 한쪽만 죽어
 * 있으면 이상합니다.
 *
 * 보여줄 것을 미리 다 담아 두는 이유: 칸이 스스로 저장소를 읽으면 한 달을 그릴
 * 때마다 마흔 번 넘게 읽게 됩니다. 세고 고르는 일은 CalendarActivity 가 한 번에 합니다.
 */
data class Day(
    /** "yyyy-MM-dd" */
    val key: String,
    val dayOfMonth: Int,
    /** 지금 보고 있는 달의 날짜인지. 아니면 흐리게 그립니다. */
    val inMonth: Boolean,
    val pending: Int,
    val done: Int,
    /** 미완료인데 기한이 지난 것이 있는지. 낮은 칸에서 점을 빨갛게 칠할지 정합니다. */
    val overdue: Boolean,
    /** 칸에 적어 보여줄 앞쪽 몇 개. 목록 화면과 같은 차례입니다. */
    val entries: List<Entry>,
    /** 그날 전체 개수. 칸에 다 못 적으면 '+N' 으로 알립니다. */
    val total: Int
) {
    data class Entry(val text: String, val done: Boolean, val overdue: Boolean)
}

/** 7칸 × 몇 줄의 날짜 격자. GridLayoutManager(7) 과 함께 씁니다. */
class CalendarAdapter(
    private val onPick: (Day) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.VH>() {

    private val days = mutableListOf<Day>()
    private var selected = ""
    private var today = ""

    /** 칸 높이(px). 0 이면 아직 재기 전입니다. */
    private var cellHeight = 0

    /** 칸에 할 일을 글로 적을지(달력이 꽉 찼을 때만), 점으로만 알릴지. */
    private var textMode = true

    /** 색·Paint 묶음. 격자에 붙을 때 한 번만 만들어 모든 칸이 나눠 씁니다. */
    private var style: DayCellStyle? = null

    class VH(val cell: DayCellView) : RecyclerView.ViewHolder(cell)

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        style = DayCellStyle(recyclerView.context)
    }

    override fun getItemCount(): Int = days.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val cellStyle = style ?: DayCellStyle(parent.context).also { style = it }
        val cell = DayCellView(parent.context, cellStyle)
        cell.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (cellHeight > 0) cellHeight else (cellStyle.circleRadius * 3).toInt()
        )
        return VH(cell)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val day = days[position]
        val cell = holder.cell

        // 화면을 꽉 채우도록 액티비티가 재어 준 높이로 늘립니다.
        val lp = cell.layoutParams
        if (cellHeight > 0 && lp.height != cellHeight) {
            lp.height = cellHeight
            cell.layoutParams = lp
        }

        cell.textMode = textMode
        cell.bind(day, day.key == selected, day.key == today)
        cell.setOnClickListener { onPick(day) }
    }

    fun submit(newDays: List<Day>, selectedKey: String, rowHeight: Int, asText: Boolean) {
        days.clear()
        days.addAll(newDays)
        selected = selectedKey
        today = TodoStore.today()
        cellHeight = rowHeight
        textMode = asText
        notifyDataSetChanged()
    }

    /** 지금 쓰고 있는 칸 높이(px). 0 이면 아직 재기 전입니다. */
    val rowHeight: Int get() = cellHeight

    /**
     * 끄는 동안 칸 높이만 갈아 끼웁니다.
     *
     * submit 과 달리 다시 묶지(bind) 않고 지금 붙어 있는 칸의 높이만 고칩니다 —
     * 손을 따라 매 프레임 불리는 자리라, 마흔두 칸을 다시 묶으면 손짓이 끊깁니다.
     */
    fun resizeCells(recyclerView: RecyclerView, height: Int, asText: Boolean) {
        val sameHeight = height <= 0 || cellHeight == height
        if (sameHeight && textMode == asText) return

        if (!sameHeight) cellHeight = height
        textMode = asText
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            (child as? DayCellView)?.textMode = asText
            if (!sameHeight) child.layoutParams = child.layoutParams.also { it.height = height }
        }
    }
}
