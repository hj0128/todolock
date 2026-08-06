package com.hj0128.todolock

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.widget.PopupMenu
import com.hj0128.todolock.databinding.SheetAddTodoBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 홈 화면 아래에 낮게 뜨는 추가 · 수정 시트.
 *
 * 첫 줄에서 입력·중요·저장을 끝낼 수 있고, 기한과 미리 알림은 둘째 줄의
 * 셀렉트박스(PopupMenu)로 흔한 선택지를 먼저 주고 마지막에 달력/시계로 넘깁니다.
 *
 * existing 을 넘기면 그 값으로 채워진 '수정' 시트가 됩니다.
 * 프래그먼트 없이 BottomSheetDialog 만 쓰므로(프로젝트의 경량 구조 유지)
 * 화면을 회전하면 입력 중이던 내용은 유지되지 않습니다.
 */
class AddTodoSheet(
    private val ctx: Context,
    private val existing: Todo? = null,
    private val onSave: (text: String, date: String, remindAt: Long, important: Boolean) -> Unit
) {
    /** 기한. 고르지 않으면 오늘입니다. */
    private val due: Calendar =
        if (existing != null) TodoStore.parseDate(existing.date) else Calendar.getInstance()
    private var remindAt = existing?.remindAt ?: Todo.NO_REMIND
    private var important = existing?.important ?: false

    /**
     * @param onDismiss 시트가 닫힐 때(저장·취소·바깥 탭 모두) 불립니다.
     *   홈 화면 위에 떠 있는 QuickAddActivity 가 스스로 끝나기 위해 씁니다.
     */
    fun show(onDismiss: (() -> Unit)? = null) {
        val b = SheetAddTodoBinding.inflate(LayoutInflater.from(ctx))
        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(b.root)
        if (onDismiss != null) dialog.setOnDismissListener { onDismiss() }

        // 저장 아이콘은 추가·수정 모두 체크(✓)입니다. ＋ 는 메인의 '할 일 추가' 버튼 전용.
        if (existing != null) {
            b.etText.setText(existing.text)
            b.etText.setSelection(existing.text.length)
        }
        sync(b)

        b.btnStar.setOnClickListener {
            important = !important
            sync(b)
        }
        b.btnDue.setOnClickListener { dueMenu(b) }
        b.btnRemind.setOnClickListener { remindMenu(b) }

        b.btnSave.setOnClickListener { save(b, dialog) }
        b.etText.setOnEditorActionListener { _, _, _ -> save(b, dialog); true }

        // 뷰가 붙은 뒤에 포커스를 줘야 키보드가 올라옵니다.
        dialog.show()
        b.etText.requestFocus()
    }

    private fun save(b: SheetAddTodoBinding, dialog: BottomSheetDialog) {
        val text = b.etText.text.toString().trim()
        if (text.isEmpty()) {
            b.etText.error = "할 일을 입력하세요"
            return
        }
        onSave(text, TodoStore.format(due), remindAt, important)
        dialog.dismiss()
    }

    /** 세 컨트롤의 표시를 현재 선택 상태와 맞춥니다. */
    private fun sync(b: SheetAddTodoBinding) {
        b.btnDue.text = TodoStore.prettyDate(TodoStore.format(due))
        b.btnRemind.text =
            if (remindAt > Todo.NO_REMIND) TodoStore.prettyDateTime(remindAt) else "미리 알림"

        b.btnStar.setImageResource(
            if (important) R.drawable.ic_star else R.drawable.ic_star_border
        )
        b.btnStar.alpha = if (important) 1f else 0.45f
    }

    // ---------- 기한: 오늘 / 내일 / 날짜 선택 ----------

    private fun dueMenu(b: SheetAddTodoBinding) {
        val tomorrow = daysFromToday(1)
        val menu = PopupMenu(ctx, b.btnDue)
        menu.menu.add(0, 1, 0, "오늘")
        menu.menu.add(0, 2, 1, "내일 (" + dayOfWeek(tomorrow) + ")")
        menu.menu.add(0, 3, 2, "날짜 선택")

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> setDue(Calendar.getInstance(), b)
                2 -> setDue(tomorrow, b)
                else -> pickDueDate(b)
            }
            true
        }
        menu.show()
    }

    private fun setDue(cal: Calendar, b: SheetAddTodoBinding) {
        due.timeInMillis = cal.timeInMillis
        sync(b)
    }

    private fun pickDueDate(b: SheetAddTodoBinding) {
        DatePickerDialog(
            ctx,
            { _, y, m, d ->
                due.set(y, m, d)
                sync(b)
            },
            due.get(Calendar.YEAR), due.get(Calendar.MONTH), due.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ---------- 미리 알림: 내일 9시 / 다음 주 9시 / 날짜 및 시간 선택 ----------

    private fun remindMenu(b: SheetAddTodoBinding) {
        val tomorrow9 = atNine(1)
        val nextWeek9 = atNine(8)

        val menu = PopupMenu(ctx, b.btnRemind)
        menu.menu.add(0, 1, 0, "내일 (" + dayOfWeek(tomorrow9) + ") 9시")
        menu.menu.add(0, 2, 1, "다음 주 (" + dayOfWeek(nextWeek9) + ") 9시")
        menu.menu.add(0, 3, 2, "날짜 및 시간 선택")
        if (remindAt > Todo.NO_REMIND) menu.menu.add(0, 4, 3, "알림 삭제")

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> setRemind(tomorrow9.timeInMillis, b)
                2 -> setRemind(nextWeek9.timeInMillis, b)
                3 -> pickRemindDateTime(b)
                else -> setRemind(Todo.NO_REMIND, b)
            }
            true
        }
        menu.show()
    }

    private fun setRemind(ms: Long, b: SheetAddTodoBinding) {
        remindAt = ms
        sync(b)
    }

    /** 달력으로 날짜를 받은 뒤 이어서 시계로 시각을 받습니다. */
    private fun pickRemindDateTime(b: SheetAddTodoBinding) {
        val base = Calendar.getInstance()
        if (remindAt > Todo.NO_REMIND) base.timeInMillis = remindAt

        DatePickerDialog(
            ctx,
            { _, y, m, d ->
                TimePickerDialog(
                    ctx,
                    { _, h, min ->
                        val c = Calendar.getInstance()
                        c.set(y, m, d, h, min)
                        c.set(Calendar.SECOND, 0)
                        c.set(Calendar.MILLISECOND, 0)
                        setRemind(c.timeInMillis, b)
                    },
                    base.get(Calendar.HOUR_OF_DAY), base.get(Calendar.MINUTE), true
                ).show()
            },
            base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ---------- 날짜 계산 ----------

    private fun daysFromToday(days: Int): Calendar {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, days)
        return c
    }

    /** 오늘로부터 days 일 뒤 오전 9시 정각. */
    private fun atNine(days: Int): Calendar {
        val c = daysFromToday(days)
        c.set(Calendar.HOUR_OF_DAY, 9)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    private fun dayOfWeek(cal: Calendar): String =
        SimpleDateFormat("E", Locale.KOREA).format(cal.time)
}
