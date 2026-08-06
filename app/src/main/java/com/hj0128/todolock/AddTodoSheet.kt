package com.hj0128.todolock

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
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
 * 메모는 같은 줄의 버튼으로 접었다 펴는 칸이라, 쓰지 않는 사람에게는 보이지 않습니다.
 *
 * existing 을 넘기면 그 값으로 채워진 '수정' 시트가 됩니다.
 * 프래그먼트 없이 BottomSheetDialog 만 쓰므로(프로젝트의 경량 구조 유지)
 * 화면을 회전하면 입력 중이던 내용은 유지되지 않습니다.
 */
class AddTodoSheet(
    private val ctx: Context,
    private val existing: Todo? = null,
    /**
     * 고른 값을 담은 할 일을 돌려줍니다. 수정이면 넘겨준 그 객체이고, 추가면 새 객체입니다.
     * 값을 하나씩 넘기면 필드가 늘 때마다 시그니처와 호출부가 같이 바뀌므로 통째로 줍니다.
     */
    private val onSave: (Todo) -> Unit
) {
    /** 기한. 고르지 않으면 오늘입니다. */
    private val due: Calendar =
        if (existing != null) TodoStore.parseDate(existing.date) else Calendar.getInstance()

    /** 기한 시각. 기본은 '없음' 이고, 기한 메뉴에서 따로 골라야 붙습니다. */
    private var dueMinutes = existing?.dueMinutes ?: Todo.NO_TIME
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
            // 이미 적어둔 메모는 감추지 않습니다. 접혀 있으면 있는 줄도 모릅니다.
            if (existing.hasMemo) {
                b.etMemo.setText(existing.memo)
                b.etMemo.visibility = View.VISIBLE
            }
        }
        sync(b)

        b.btnStar.setOnClickListener {
            important = !important
            sync(b)
        }
        b.btnDue.setOnClickListener { dueMenu(b) }
        b.btnRemind.setOnClickListener { remindMenu(b) }
        b.btnMemo.setOnClickListener { toggleMemo(b) }

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
        // 수정이면 원래 객체를 그대로 고칩니다. id 와 done 처럼 시트가 다루지 않는
        // 값이 살아 있어야 하고, 호출한 쪽이 들고 있는 참조와도 어긋나지 않습니다.
        val todo = existing ?: Todo(id = System.currentTimeMillis(), text = "", date = "")
        todo.text = text
        todo.date = TodoStore.format(due)
        todo.dueMinutes = dueMinutes
        todo.memo = b.etMemo.text.toString().trim()
        todo.important = important
        // 알림 시각이 바뀌었으면 '이미 알렸음' 을 지워야 새 시각에 다시 알립니다.
        if (todo.remindAt != remindAt) {
            todo.remindAt = remindAt
            todo.notified = false
        }

        onSave(todo)
        dialog.dismiss()
    }

    /**
     * 메모 칸을 펼치거나 접습니다.
     * 접을 때 적어둔 내용은 지우지 않습니다 — 잘못 눌렀다가 글이 날아가면 곤란합니다.
     * (접힌 채로 저장하면 그 내용이 그대로 저장됩니다)
     */
    private fun toggleMemo(b: SheetAddTodoBinding) {
        if (b.etMemo.visibility == View.VISIBLE) {
            b.etMemo.visibility = View.GONE
        } else {
            b.etMemo.visibility = View.VISIBLE
            b.etMemo.requestFocus()
        }
    }

    /** 세 컨트롤의 표시를 현재 선택 상태와 맞춥니다. */
    private fun sync(b: SheetAddTodoBinding) {
        b.btnDue.text = TodoStore.prettyDate(TodoStore.format(due)) +
            (if (dueMinutes >= 0) " " + TodoStore.formatMinutes(dueMinutes) else "")
        b.btnRemind.text =
            if (remindAt > Todo.NO_REMIND) TodoStore.prettyDateTime(remindAt) else "미리 알림"

        b.btnStar.setImageResource(
            if (important) R.drawable.ic_star else R.drawable.ic_star_border
        )
        b.btnStar.alpha = if (important) 1f else 0.45f
    }

    // ---------- 기한: 오늘 / 내일 / 날짜 선택 / 시간 ----------

    /**
     * 시각은 날짜와 같은 줄에서 고르되 별도 항목으로 둡니다.
     * 날짜를 고르면 시계까지 이어서 뜨는 방식이면 시각이 사실상 필수가 되는데,
     * 대부분의 할 일에는 시각이 필요 없습니다. 그래서 원하는 사람만 한 번 더
     * 누르게 하고, 기본은 지금까지처럼 날짜만입니다.
     */
    private fun dueMenu(b: SheetAddTodoBinding) {
        val tomorrow = daysFromToday(1)
        val menu = PopupMenu(ctx, b.btnDue)
        menu.menu.add(0, 1, 0, "오늘")
        menu.menu.add(0, 2, 1, "내일 (" + dayOfWeek(tomorrow) + ")")
        menu.menu.add(0, 3, 2, "날짜 선택")
        menu.menu.add(
            0, 4, 3,
            if (dueMinutes >= 0) "시간 변경 (" + TodoStore.formatMinutes(dueMinutes) + ")"
            else "시간 추가"
        )
        if (dueMinutes >= 0) menu.menu.add(0, 5, 4, "시간 지우기")

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> setDue(Calendar.getInstance(), b)
                2 -> setDue(tomorrow, b)
                3 -> pickDueDate(b)
                4 -> pickDueTime(b)
                else -> {
                    dueMinutes = Todo.NO_TIME
                    sync(b)
                }
            }
            true
        }
        menu.show()
    }

    /** 날짜만 바꿉니다. 이미 고른 시각은 그대로 둡니다. */
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

    /** 시계를 열어 기한 시각을 받습니다. 아직 없으면 9시에서 시작합니다. */
    private fun pickDueTime(b: SheetAddTodoBinding) {
        val start = if (dueMinutes >= 0) dueMinutes else 9 * 60
        TimePickerDialog(
            ctx,
            { _, h, min ->
                dueMinutes = h * 60 + min
                sync(b)
            },
            start / 60, start % 60, true
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
