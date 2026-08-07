package com.hj0128.todolock

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hj0128.todolock.databinding.SheetAddTodoBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Calendar

/**
 * 홈 화면 아래에 낮게 뜨는 추가 · 수정 시트.
 *
 * 첫 줄에서 입력·중요·저장을 끝낼 수 있고, 기한과 미리 알림은 둘째 줄의 버튼을
 * 누르면 곧바로 달력이 열리고 날짜를 고르면 이어서 시계가 뜹니다.
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
     * 달력·시계를 열기 직전의 키보드 상태.
     * 다이얼로그가 닫히면서 시스템이 키보드를 내려버리므로, 원래 열려 있었으면
     * 되돌려 놓습니다. 닫혀 있었으면 아무것도 하지 않아 그대로 닫힌 채 남습니다.
     */
    private var imeWasVisible = false

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
        b.btnDue.setOnClickListener { pickDue(b) }
        b.btnRemind.setOnClickListener { pickRemind(b) }
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
        val dueLabel = TodoStore.prettyDate(TodoStore.format(due)) +
            (if (dueMinutes >= 0) " " + TodoStore.formatMinutes(dueMinutes) else "")
        b.btnDue.text = dueLabel
        b.btnRemind.text =
            if (remindAt > Todo.NO_REMIND) TodoStore.prettyDateTime(remindAt) else "미리 알림"

        // 기한 뒤에 울리는 알림은 십중팔구 잘못 고른 것입니다. 막지는 않고 알립니다.
        val late = TodoStore.isRemindAfterDue(TodoStore.format(due), dueMinutes, remindAt)
        b.tvWarn.visibility = if (late) View.VISIBLE else View.GONE
        if (late) b.tvWarn.text = "⚠ 기한(" + dueLabel + ") 보다 늦은 알림입니다"

        b.btnStar.setImageResource(
            if (important) R.drawable.ic_star else R.drawable.ic_star_border
        )
        b.btnStar.alpha = if (important) 1f else 0.45f
    }

    // ---------- 기한: 달력 → (선택) 시계 ----------

    /**
     * 기한 버튼을 누르면 곧바로 달력이 열리고, 날짜를 고르면 이어서 시계가 뜹니다.
     *
     * '오늘/내일' 같은 후보 메뉴를 두지 않습니다. 한 번 더 누르게 만드는 대신
     * 달력에서 오늘 날짜가 이미 선택된 채로 열리므로, 오늘로 두려면 확인만 누르면
     * 됩니다. 후보가 있어도 결국 달력을 여는 경우가 대부분이었습니다.
     *
     * 시각은 선택입니다 — 시계에서 취소하면 날짜만 남습니다(이미 있던 시각도 지워집니다).
     */
    private fun pickDue(b: SheetAddTodoBinding) {
        imeWasVisible = isImeVisible(b)
        val picker = DatePickerDialog(
            ctx,
            { _, y, m, d ->
                due.set(y, m, d)
                sync(b)
                pickDueTime(b)
            },
            due.get(Calendar.YEAR), due.get(Calendar.MONTH), due.get(Calendar.DAY_OF_MONTH)
        )
        picker.setOnCancelListener { restoreIme(b) }
        showKeepingIme(picker)
    }

    /** 기한 시각. 아직 없으면 9시에서 시작하고, 취소하면 '시각 없음' 이 됩니다. */
    private fun pickDueTime(b: SheetAddTodoBinding) {
        val start = if (dueMinutes >= 0) dueMinutes else 9 * 60
        val picker = TimePickerDialog(
            ctx,
            { _, h, min ->
                dueMinutes = h * 60 + min
                sync(b)
            },
            start / 60, start % 60, true
        )
        picker.setOnCancelListener {
            dueMinutes = Todo.NO_TIME
            sync(b)
        }
        picker.setOnDismissListener { restoreIme(b) }
        showKeepingIme(picker)
    }

    // ---------- 미리 알림: 달력 → 시계 ----------

    /**
     * 기한과 같은 흐름입니다. 다만 알림은 시각이 있어야 성립하므로,
     * 시계에서 취소하면 알림 자체가 해제됩니다(= 알림 삭제 경로이기도 합니다).
     */
    private fun pickRemind(b: SheetAddTodoBinding) {
        imeWasVisible = isImeVisible(b)
        val base = Calendar.getInstance()
        if (remindAt > Todo.NO_REMIND) base.timeInMillis = remindAt

        val picker = DatePickerDialog(
            ctx,
            { _, y, m, d -> pickRemindTime(b, y, m, d, base) },
            base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)
        )
        picker.setOnCancelListener { restoreIme(b) }
        showKeepingIme(picker)
    }

    private fun pickRemindTime(b: SheetAddTodoBinding, y: Int, m: Int, d: Int, base: Calendar) {
        val startH = if (remindAt > Todo.NO_REMIND) base.get(Calendar.HOUR_OF_DAY) else 9
        val startM = if (remindAt > Todo.NO_REMIND) base.get(Calendar.MINUTE) else 0

        val picker = TimePickerDialog(
            ctx,
            { _, h, min ->
                val c = Calendar.getInstance()
                c.set(y, m, d, h, min)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                remindAt = c.timeInMillis
                sync(b)
            },
            startH, startM, true
        )
        picker.setOnCancelListener {
            remindAt = Todo.NO_REMIND
            sync(b)
        }
        picker.setOnDismissListener { restoreIme(b) }
        showKeepingIme(picker)
    }

    /**
     * 키보드를 건드리지 않고 다이얼로그를 띄웁니다.
     *
     * 그냥 show() 하면 다이얼로그 창이 입력 포커스를 가져가면서 키보드가 내려갑니다.
     * 띄우는 순간만 '포커스 받지 않는 창' 으로 만들었다가 곧바로 되돌리면, 시스템이
     * 키보드 상태를 다시 계산하지 않아 열려 있던 건 열린 채, 닫혀 있던 건 닫힌 채로
     * 남습니다. 달력·시계를 쓰는 동안에도 시트의 입력칸은 그대로입니다.
     */
    private fun isImeVisible(b: SheetAddTodoBinding): Boolean =
        ViewCompat.getRootWindowInsets(b.root)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false

    /**
     * 다이얼로그가 닫힌 뒤 키보드를 원래대로 되돌립니다.
     * 다이얼로그 창이 사라지면서 포커스가 시트로 돌아오는데, 그때 시스템이 키보드를
     * 내리므로 한 박자 뒤(post)에 다시 올립니다.
     */
    private fun restoreIme(b: SheetAddTodoBinding) {
        if (!imeWasVisible) return
        b.root.post {
            val target = if (b.etMemo.hasFocus()) b.etMemo else b.etText
            target.requestFocus()
            ctx.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun showKeepingIme(dialog: Dialog) {
        val window = dialog.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
        dialog.show()
        // 띄운 뒤 곧바로 되돌려야 달력·시계를 실제로 조작할 수 있습니다.
        window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }
}
