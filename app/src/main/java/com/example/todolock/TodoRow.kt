package com.example.todolock

import android.graphics.Paint
import android.view.View
import com.example.todolock.databinding.ItemTodoBinding

/** 목록 화면과 팝업 화면이 같은 행 디자인/동작을 공유합니다. */
object TodoRow {

    fun bind(
        b: ItemTodoBinding,
        todo: Todo,
        onToggle: (Todo) -> Unit,
        onDelete: ((Todo) -> Unit)?
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

        b.cbDone.setOnCheckedChangeListener { _, checked ->
            todo.done = checked
            onToggle(todo)
        }
        b.rowContainer.setOnClickListener { b.cbDone.isChecked = !b.cbDone.isChecked }

        if (onDelete == null) {
            b.btnDelete.visibility = View.GONE
        } else {
            b.btnDelete.visibility = View.VISIBLE
            b.btnDelete.setOnClickListener { onDelete.invoke(todo) }
        }
    }
}
