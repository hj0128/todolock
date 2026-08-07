package com.hj0128.todolock

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import com.hj0128.todolock.databinding.ItemTodoBinding

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
     */
    fun bind(
        b: ItemTodoBinding,
        todo: Todo,
        onToggle: ((Todo) -> Unit)? = null,
        onDelete: ((Todo) -> Unit)? = null,
        onStar: ((Todo) -> Unit)? = null,
        onEdit: ((Todo) -> Unit)? = null
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

        // 아래 줄은 기한과 미리 알림을 이어 붙입니다.
        // '지남' 이라는 말은 붙이지 않습니다 — 날짜가 이미 '어제' 라고 말하고 있고
        // 빨간색이 지났다는 사실까지 말하고 있어, 같은 이야기를 세 번 하게 됩니다.
        val overdue = TodoStore.isOverdue(todo)
        val due = TodoStore.prettyDue(todo)
        val parts = mutableListOf(due)
        if (todo.hasReminder) parts.add("🔔 " + TodoStore.prettyDateTime(todo.remindAt))

        // 오늘 기한은 눈에 띄어야 합니다 — '지남'(빨강)만 강조되고 정작 오늘 할 일이
        // 어제·모레와 같은 회색이면, 목록에서 가장 급한 줄이 가장 안 보입니다.
        // 색은 날짜 부분에만 입힙니다. 줄 전체를 물들이면 뒤에 붙는 미리 알림까지
        // 같은 무게로 읽혀 오히려 초점이 흐려집니다.
        val line = parts.joinToString(" · ")
        b.tvDate.text = if (TodoStore.isDueToday(todo)) emphasize(b, line, due.length) else line
        b.tvDate.setTextColor(
            if (overdue) overdueColor(b.root.context) else secondaryColor(b.root.context)
        )

        // 메모는 첫 줄만. 열어보지 않아도 무슨 내용인지 알 수 있으면 충분합니다.
        if (todo.hasMemo) {
            b.tvMemo.visibility = View.VISIBLE
            b.tvMemo.text = TodoStore.memoLine(todo)
            b.tvMemo.alpha = if (todo.done) 0.35f else 0.75f
        } else {
            b.tvMemo.visibility = View.GONE
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
            b.btnStar.visibility = View.GONE
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

    /** 앞에서부터 length 글자에만 파란 굵은 글씨를 입힙니다(= 기한 날짜 부분). */
    private fun emphasize(b: ItemTodoBinding, text: String, length: Int): CharSequence {
        val s = SpannableString(text)
        val color = androidx.core.content.ContextCompat.getColor(
            b.root.context, R.color.sky_heading
        )
        s.setSpan(ForegroundColorSpan(color), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** 다크/라이트 어느 쪽에서도 맞는 보조 텍스트 색을 테마에서 꺼내옵니다. */
    private fun secondaryColor(ctx: Context): Int {
        val ta = ctx.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        val c = ta.getColor(0, Color.GRAY)
        ta.recycle()
        return c
    }
}