package com.example.todolock

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.example.todolock.databinding.ItemTodoBinding

/** 목록 화면과 팝업 화면이 같은 행 디자인/동작을 공유합니다. */
object TodoRow {

    private const val OVERDUE = 0xFFC8443C.toInt()

    fun bind(
        b: ItemTodoBinding,
        todo: Todo,
        onToggle: (Todo) -> Unit,
        onDelete: ((Todo) -> Unit)? = null,
        onStar: ((Todo) -> Unit)? = null,
        showDate: Boolean = true
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

        // 날짜를 행마다 보여주므로 지난 날짜는 눈에 띄게 표시합니다.
        if (showDate) {
            b.tvDate.visibility = View.VISIBLE
            val overdue = !todo.done && todo.date < TodoStore.today()
            b.tvDate.text = if (overdue) {
                TodoStore.prettyDate(todo.date) + " · 지남"
            } else {
                TodoStore.prettyDate(todo.date)
            }
            b.tvDate.setTextColor(
                if (overdue) OVERDUE else secondaryColor(b.root.context)
            )
        } else {
            b.tvDate.visibility = View.GONE
        }

        b.cbDone.setOnCheckedChangeListener { _, checked ->
            todo.done = checked
            onToggle(todo)
        }
        b.rowContainer.setOnClickListener { b.cbDone.isChecked = !b.cbDone.isChecked }

        if (onStar == null) {
            b.btnStar.visibility = View.GONE
        } else {
            b.btnStar.visibility = View.VISIBLE
            b.btnStar.setImageResource(
                if (todo.important) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
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