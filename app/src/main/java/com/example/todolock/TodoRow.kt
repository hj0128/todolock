package com.example.todolock

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.example.todolock.databinding.ItemTodoBinding

/** 목록 화면과 팝업 화면이 같은 행 디자인/동작을 공유합니다. */
object TodoRow {

    private fun overdueColor(ctx: Context): Int =
        androidx.core.content.ContextCompat.getColor(ctx, R.color.overdue)

    /**
     * onToggle 이 null 이면 체크박스를 감추고 행 탭도 받지 않습니다(읽기 전용 행).
     * 잠금해제 팝업처럼 '확인만' 하는 화면에서 씁니다.
     *
     * 완료 처리는 체크박스로만 합니다. 행 본문을 탭하면 onEdit 이 불립니다.
     *
     * @param showDate 아래 줄에 기한을 넣을지. 팝업은 오늘 것만 보여주므로 끕니다.
     * @param showReminder 아래 줄에 미리 알림 시각을 넣을지.
     * @param starIndicator onStar 가 없을 때도 중요 항목에 ★ 를 보여줄지.
     *   팝업처럼 누를 수는 없지만 중요 여부는 알려야 하는 화면에서 씁니다.
     */
    fun bind(
        b: ItemTodoBinding,
        todo: Todo,
        onToggle: ((Todo) -> Unit)? = null,
        onDelete: ((Todo) -> Unit)? = null,
        onStar: ((Todo) -> Unit)? = null,
        onEdit: ((Todo) -> Unit)? = null,
        showDate: Boolean = true,
        showReminder: Boolean = true,
        starIndicator: Boolean = false
    ) {
        b.cbDone.setOnCheckedChangeListener(null)
        b.cbDone.isChecked = todo.done

        b.tvText.text = todo.text
        b.tvText.paintFlags = if (todo.done) {
            b.tvText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            b.tvText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        b.tvText.alpha = if (todo.done) 0.4f else 1f

        // 아래 줄은 조각을 모아 만듭니다. 넣을 게 없으면 줄 자체를 감춥니다.
        val overdue = TodoStore.isOverdue(todo)
        val parts = mutableListOf<String>()
        if (showDate) {
            parts.add(TodoStore.prettyDate(todo.date))
            if (overdue) parts.add("지남")
        }
        if (showReminder && todo.hasReminder) {
            parts.add("🔔 " + TodoStore.prettyRemindShort(todo))
        }

        if (parts.isEmpty()) {
            b.tvDate.visibility = View.GONE
        } else {
            b.tvDate.visibility = View.VISIBLE
            b.tvDate.text = parts.joinToString(" · ")
            // 기한이 지난 것은 눈에 띄게. 기한을 감춘 화면에서는 강조하지 않습니다.
            b.tvDate.setTextColor(
                if (overdue && showDate) overdueColor(b.root.context)
                else secondaryColor(b.root.context)
            )
        }

        if (onToggle == null) {
            b.cbDone.visibility = View.GONE
        } else {
            b.cbDone.visibility = View.VISIBLE
            b.cbDone.setOnCheckedChangeListener { _, checked ->
                todo.done = checked
                onToggle(todo)
            }
        }

        // 행 본문 탭 = 수정. 완료는 체크박스가 직접 처리합니다.
        if (onEdit == null) {
            b.rowContainer.setOnClickListener(null)
            b.rowContainer.isClickable = false
        } else {
            b.rowContainer.setOnClickListener { onEdit.invoke(todo) }
        }

        if (onStar == null) {
            // 누를 수는 없지만 중요 여부는 알려야 하는 화면(팝업)용 표시.
            if (starIndicator && todo.important) {
                b.btnStar.visibility = View.VISIBLE
                b.btnStar.setImageResource(R.drawable.ic_star)
                b.btnStar.alpha = 1f
                b.btnStar.contentDescription = "중요"
                b.btnStar.setOnClickListener(null)
                b.btnStar.isClickable = false
            } else {
                b.btnStar.visibility = View.GONE
            }
        } else {
            b.btnStar.visibility = View.VISIBLE
            b.btnStar.setImageResource(
                if (todo.important) R.drawable.ic_star else R.drawable.ic_star_border
            )
            b.btnStar.alpha = if (todo.important) 1f else 0.45f
            b.btnStar.contentDescription = if (todo.important) "중요 해제" else "중요로 표시"
            b.btnStar.setOnClickListener {
                todo.important = !todo.important
                onStar.invoke(todo)
            }
        }

        if (onDelete == null) {
            b.btnDelete.visibility = View.GONE
        } else {
            b.btnDelete.visibility = View.VISIBLE
            b.btnDelete.setOnClickListener { onDelete.invoke(todo) }
        }
    }

    /** 다크/라이트 어느 쪽에서도 맞는 보조 텍스트 색을 테마에서 꺼내옵니다. */
    private fun secondaryColor(ctx: Context): Int {
        val ta = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        val c = ta.getColor(0, Color.GRAY)
        ta.recycle()
        return c
    }
}