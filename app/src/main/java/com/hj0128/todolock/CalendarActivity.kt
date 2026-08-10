package com.hj0128.todolock

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.NumberPicker
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hj0128.todolock.databinding.ActivityCalendarBinding
import com.hj0128.todolock.databinding.SheetPickDateBinding
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 한 달을 펼쳐 보는 화면.
 *
 * 목록 화면은 날짜순 한 줄이라 '언제 몰려 있는지' 와 '빈 날' 이 보이지 않습니다.
 * 그것만이 이 화면이 하는 일이고, 할 일을 다루는 방식(완료·중요·수정·삭제)은
 * 목록 화면과 똑같은 행(TodoRow)을 그대로 씁니다.
 *
 * 들어오면 달력이 화면을 꽉 채웁니다. 날짜를 눌러야 달력이 줄면서 그 아래에
 * 그날의 할 일이 붙습니다 — 달력을 보러 들어온 사람에게 목록을 먼저 들이밀지
 * 않으려는 것입니다.
 *
 * 손짓은 끄는 동안 화면이 따라 움직이고, 손을 떼면 가까운 자리로 붙습니다.
 * 세로로 끌면 내역 칸이 커지고 작아지며, 가로로 끌면 달력이 밀렸다가 달이
 * 넘어갑니다.
 */
class CalendarActivity : AppCompatActivity() {

    private lateinit var b: ActivityCalendarBinding
    private lateinit var grid: CalendarAdapter
    private lateinit var todos: TodoAdapter
    private lateinit var listLayout: LockableLayoutManager

    /** 보고 있는 달. 항상 그 달 1일로 맞춰 둡니다. */
    private val month: Calendar = Calendar.getInstance()

    /** 고른 날짜 "yyyy-MM-dd". null 이면 아무 날도 고르지 않은 상태입니다. */
    private var selected: String? = null

    /**
     * 마지막으로 고른 날짜. 내역을 접어도 지우지 않습니다.
     *
     * 8월 20일을 보다가 내역을 내렸는데 다시 올렸을 때 오늘이 열리면, 내리기
     * 전에 보던 날로 돌아가려고 한 번 더 눌러야 합니다.
     */
    private var lastPicked: String? = null

    /** 이번 달 격자의 줄 수(다섯 또는 여섯). 칸 높이를 이 값으로 나눕니다. */
    private var monthRows = 6

    /** 달력 칸과 내역 칸이 나눠 갖는 전체 높이(px). 처음 재고 나서 정해집니다. */
    private var available = 0

    /** 지금 내역 칸의 높이(px). 0 이면 접힌 것입니다. */
    private var panelHeight = 0

    // ---------- 손짓 ----------

    private enum class Drag {
        /** 우리가 다룰 손짓이 아님(목록 굴리기, 탭 등) */
        NONE,

        /** 아직 어느 쪽으로 끄는지 정해지지 않음 */
        UNDECIDED,

        /** 세로 — 내역 칸 크기 */
        PANEL,

        /** 가로 — 달 넘기기 */
        MONTH
    }

    private var drag = Drag.NONE
    private var downX = 0f
    private var downY = 0f
    private var downOnCalendar = true
    private var downListAtTop = true
    private var dragStartPanel = 0
    private var touchSlop = 0
    private val panelRect = Rect()
    private var settling: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 고른 강조색을 입힙니다. setContentView 보다 먼저여야 합니다.
        ThemeConfig.apply(this)
        b = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(b.root)

        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        selected = savedInstanceState?.getString(STATE_SELECTED)
        lastPicked = savedInstanceState?.getString(STATE_LAST_PICKED) ?: selected
        restoredPanelRatio = savedInstanceState?.getFloat(STATE_PANEL, 0f) ?: 0f
        // 보던 달은 따로 기억합니다. 고른 날이 없으면(달만 넘겨 둔 상태) 되살릴
        // 근거가 selected 쪽에 남지 않기 때문입니다.
        showMonthOf(savedInstanceState?.getString(STATE_MONTH) ?: TodoStore.today())

        // 다른 달 칸을 누르면 그 달로 넘어갑니다. 30일과 1일이 나란히 보이는데
        // 한쪽만 눌리지 않으면 이상합니다.
        grid = CalendarAdapter { day ->
            choose(day.key)
            if (!day.inMonth) showMonthOf(day.key)
            refresh()
            if (panelHeight <= 0) animatePanelTo(smallHeight())
        }
        b.grid.layoutManager = GridLayoutManager(this, 7)
        b.grid.adapter = grid
        // 한 달이 최대 마흔두 칸인데 재활용 풀의 기본 한도는 다섯 개뿐이라,
        // 다시 채울 때마다 나머지를 새로 만들게 됩니다. 한 달치를 담아 둡니다.
        b.grid.recycledViewPool.setMaxRecycledViews(0, 42)

        /*
          칸 높이는 격자를 재고 나서야 알 수 있습니다. 재기 전에는 refresh 가
          빈 목록을 넣어 칸을 아예 만들지 않고, 여기서 높이를 알게 된 뒤 한 번에
          제 크기로 채웁니다. 그리기 전에(false) 끝내므로 작은 달력이 비쳤다가
          커지는 일도 없습니다.

          끄는 중이거나 붙는 중일 때는 손이 정한 높이를 그대로 두어야 하므로
          손대지 않습니다.
        */
        b.grid.viewTreeObserver.addOnPreDrawListener {
            if (drag != Drag.NONE || settling != null) return@addOnPreDrawListener true
            measureAvailable()
            val wanted = rowHeight()
            if (wanted > 0 && (wanted != grid.rowHeight || grid.itemCount == 0)) {
                refresh()
                false
            } else {
                true
            }
        }

        // 목록 화면과 같은 동작을 그대로 씁니다.
        todos = TodoAdapter(
            mutableListOf(),
            onToggle = { TodoStore.update(this, it); Reminders.schedule(this, it); refresh() },
            onDelete = { Reminders.cancel(this, it.id); TodoStore.delete(this, it.id); refresh() },
            onStar = { TodoStore.update(this, it); refresh() },
            onEdit = { openAddSheet(it) }
        )
        listLayout = LockableLayoutManager(this)
        b.recycler.layoutManager = listLayout
        b.recycler.adapter = todos

        // 내역이 떠 있으면 뒤로 가기는 화면을 닫지 않고 내역만 접습니다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (panelHeight > 0) {
                    animatePanelTo(0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        b.btnBack.setOnClickListener { finish() }
        b.tvMonth.setOnClickListener { pickDate() }
        b.fabAdd.setOnClickListener { openAddSheet() }
        b.btnToday.setOnClickListener {
            showMonthOf(TodoStore.today())
            closePanelNow()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** 화면을 돌리거나 다크 모드가 바뀌어도 보던 달·날짜·칸 크기가 그대로 남게 합니다. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selected)
        outState.putString(STATE_LAST_PICKED, lastPicked)
        outState.putString(STATE_MONTH, TodoStore.format(month))
        // 화면 크기가 달라질 수 있으므로 px 가 아니라 비율로 남깁니다.
        outState.putFloat(STATE_PANEL, if (available > 0) panelHeight / available.toFloat() else 0f)
    }

    private var restoredPanelRatio = 0f

    // ---------- 아래에 붙는 그날 내역 ----------

    /**
     * 두 칸이 나눠 갖는 전체 높이를 잽니다. 한 번만 재면 되지만, 화면이 돌면
     * 달라지므로 값이 바뀔 때마다 갱신합니다.
     */
    private fun measureAvailable() {
        val total = b.calendarBox.height + b.dayPanel.height
        if (total <= 0 || total == available) return

        available = total
        if (restoredPanelRatio > 0f) {
            // 회전 등으로 되살아난 경우. 재기 전에는 넣을 수 없어 여기서 넣습니다.
            setPanelHeight((available * restoredPanelRatio).roundToInt())
            restoredPanelRatio = 0f
        }
    }

    /** '조금' 자리. 달력이 6, 내역이 4 정도를 나눠 갖습니다. */
    private fun smallHeight(): Int = (available * PANEL_SMALL_RATIO).roundToInt()

    /**
     * 내역 칸의 높이를 지금 이 값으로 만듭니다. 끄는 동안 매 프레임 불립니다.
     *
     * 칸 높이까지 여기서 함께 갈아 끼웁니다 — 달력이 줄어드는데 날짜 칸이
     * 그대로면 아랫줄이 잘려 나갑니다. 다시 묶지(bind) 않고 높이만 고치므로
     * 한 번의 배치로 끝납니다.
     */
    private fun setPanelHeight(px: Int) {
        if (available <= 0) return
        val h = px.coerceIn(0, available)
        panelHeight = h

        b.dayPanel.visibility = if (h > 0) View.VISIBLE else View.GONE
        b.dayPanel.layoutParams = b.dayPanel.layoutParams.also { it.height = h }

        val calendarH = available - h
        b.calendarBox.visibility = if (calendarH > 0) View.VISIBLE else View.GONE

        // 내역을 조금이라도 펴면 칸에는 점만 찍습니다. 좁아진 자리에 글을
        // 욱여넣으면 잘린 토막만 남습니다.
        val gridH = calendarH - b.weekRow.height
        if (gridH > 0 && monthRows > 0) grid.resizeCells(b.grid, gridH / monthRows, h <= 0)

        // 크게 펴 두었을 때만 목록이 굴러갑니다. 조금 펴 둔 칸은 훑어보는
        // 자리이고, 굴려 보려면 위로 밀어 크게 펴면 됩니다.
        listLayout.scrollEnabled = h >= available * LIST_SCROLL_RATIO
        if (!listLayout.scrollEnabled) b.recycler.scrollToPosition(0)

        syncTodayButton()
    }

    /** 지금 높이에서 가장 가까운 자리(접힘·조금·많이)로 붙입니다. */
    private fun settlePanel() {
        val stops = intArrayOf(0, smallHeight(), available)
        var best = stops[0]
        for (s in stops) if (abs(s - panelHeight) < abs(best - panelHeight)) best = s
        animatePanelTo(best)
    }

    private fun animatePanelTo(target: Int) {
        settling?.cancel()
        if (available <= 0 || panelHeight == target) {
            finishSettle(target)
            return
        }
        settling = ValueAnimator.ofInt(panelHeight, target).apply {
            duration = SETTLE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { setPanelHeight(it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    settling = null
                    finishSettle(target)
                }
            })
            start()
        }
    }

    private fun finishSettle(target: Int) {
        setPanelHeight(target)
        // 접혔으면 달력에 남은 '고름' 표시도 지웁니다.
        if (target <= 0) selected = null
        refresh()
    }

    /** 달을 넘길 때처럼 애니메이션 없이 곧바로 접습니다. */
    private fun closePanelNow() {
        settling?.cancel()
        settling = null
        selected = null
        setPanelHeight(0)
    }

    /**
     * '조금' 단계에서 목록 굴리기를 잠그는 배치 담당.
     *
     * 잠그지 않으면 위로 미는 손짓이 '목록 굴리기' 인지 '칸 키우기' 인지 매번
     * 애매해집니다. 실제로 잠그기 전에는, 목록이 아래 여백을 갖고 있어 세 줄짜리
     * 날에도 굴릴 수 있는 상태라 칸이 영영 커지지 않았습니다.
     */
    private class LockableLayoutManager(ctx: Context) : LinearLayoutManager(ctx) {
        var scrollEnabled = true
        override fun canScrollVertically(): Boolean = scrollEnabled && super.canScrollVertically()
    }

    // ---------- 달 이동 ----------

    private fun showMonthOf(dateKey: String) {
        month.time = TodoStore.parseDate(dateKey).time
        month.set(Calendar.DAY_OF_MONTH, 1)
    }

    /**
     * 달을 넘기면 고른 날짜를 놓고 내역도 접습니다.
     * 넘어간 달에 없는 날짜를 아래에서 계속 보여주면 위아래가 서로 다른 말을 합니다.
     */
    private fun shiftMonth(delta: Int) {
        month.add(Calendar.MONTH, delta)
        closePanelNow()
        refresh()
    }

    // ---------- 손짓 ----------

    /**
     * 손짓을 액티비티에서 직접 받습니다.
     *
     * 처음에는 격자에만 걸고 fling 으로 봤는데 두 가지가 새어 나갔습니다 —
     * 격자 바깥(빈 자리)에서 민 손짓은 아예 닿지 않았고, 천천히 끄는 손짓은
     * fling 으로 인식되지 않았습니다. 지금은 누른 자리부터 끝까지 직접 따라갑니다.
     *
     * 방향은 한 번만 정합니다. 세로로 끌기 시작했는데 손이 좌우로 흔들린다고
     * 달이 넘어가면 안 됩니다.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downOnCalendar = startedOnCalendar(ev.y)
                // 목록이 어디까지 굴려져 있었는지는 누른 순간의 것을 씁니다.
                // 끄는 도중에 맨 위까지 굴러가 버리면, 굴리려던 손짓까지 칸
                // 크기 바꾸기로 새어 들어갑니다.
                downListAtTop = !b.recycler.canScrollVertically(-1)
                dragStartPanel = panelHeight
                drag = if (settling != null) Drag.NONE else Drag.UNDECIDED
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY

                if (drag == Drag.UNDECIDED && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    drag = decideDrag(dx, dy)
                    if (drag != Drag.NONE) cancelChildren(ev)
                }
                when (drag) {
                    Drag.MONTH -> {
                        // 다음 달을 미리 그려 두지 않으므로 그대로 따라가면 옆에
                        // 빈 자리가 크게 드러납니다. 손짓의 일부만 따라갑니다.
                        b.calendarBox.translationX = dx * MONTH_FOLLOW
                        return true
                    }
                    Drag.PANEL -> {
                        setPanelHeight(dragStartPanel + (downY - ev.y).toInt())
                        return true
                    }
                    else -> Unit
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val was = drag
                drag = Drag.NONE
                when (was) {
                    Drag.MONTH -> {
                        settleMonth(ev.x - downX)
                        return true
                    }
                    Drag.PANEL -> {
                        settlePanel()
                        return true
                    }
                    else -> Unit
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun decideDrag(dx: Float, dy: Float): Drag {
        if (abs(dx) > abs(dy)) return Drag.MONTH
        if (!canDragPanel(dy)) return Drag.NONE

        // 아무 날도 고르지 않은 채 위로 올리면 날짜를 하나 정해야 합니다.
        if (selected == null) {
            choose(dayForBlindSwipe())
            refresh()
        }
        return Drag.PANEL
    }

    private fun canDragPanel(dy: Float): Boolean {
        if (downOnCalendar) return true
        // 목록 쪽에서 시작한 손짓. 위로는 더 커질 여지가 있을 때만, 아래로는
        // 목록이 맨 위에 닿아 있을 때만 칸 크기로 봅니다.
        return if (dy < 0) dragStartPanel < available else downListAtTop
    }

    /** 손짓이 달력 쪽에서 시작했는지. 내역이 접혀 있으면 화면 전체가 달력입니다. */
    private fun startedOnCalendar(y: Float): Boolean {
        if (b.dayPanel.visibility != View.VISIBLE) return true
        b.dayPanel.getGlobalVisibleRect(panelRect)
        return y < panelRect.top
    }

    /**
     * 우리가 손짓을 가져갔다는 사실을 아래 뷰들에 알립니다.
     * 이러지 않으면 끌던 손을 뗄 때 지나간 날짜나 행이 함께 눌립니다.
     */
    private fun cancelChildren(ev: MotionEvent) {
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    /**
     * 가로로 끌던 손을 뗐을 때. 충분히 밀었으면 남은 만큼 마저 밀어내고 달을
     * 바꾼 뒤 반대쪽에서 새 달이 들어옵니다. 모자라면 제자리로 돌아옵니다.
     */
    private fun settleMonth(dx: Float) {
        val view = b.calendarBox
        if (abs(dx) < MONTH_MIN_DP * resources.displayMetrics.density) {
            view.animate().translationX(0f).setDuration(SETTLE_MS).start()
            return
        }

        // 왼쪽으로 밀면 다음 달, 오른쪽으로 밀면 지난 달
        val delta = if (dx < 0) 1 else -1
        val away = -delta * view.width * MONTH_FOLLOW
        view.animate()
            .translationX(away)
            .alpha(0f)
            .setDuration(MONTH_OUT_MS)
            .withEndAction {
                shiftMonth(delta)
                view.translationX = -away
                view.animate().translationX(0f).alpha(1f).setDuration(MONTH_IN_MS).start()
            }
            .start()
    }

    /** 날짜를 고릅니다. 내역을 접어도 남도록 마지막으로 고른 날에도 적어 둡니다. */
    private fun choose(key: String) {
        selected = key
        lastPicked = key
    }

    /**
     * 고른 날 없이 위로 올렸을 때 열어 줄 날짜.
     *
     * 직전에 고른 날이 지금 보고 있는 달에 있으면 그 날입니다 — 내렸다가 다시
     * 올리면 보던 날로 돌아와야지 오늘로 튀면 안 됩니다.
     *
     * 그런 날이 없으면 오늘이고, 오늘도 이 달이 아니면 그 달 1일입니다.
     * 2027년을 넘겨보다가 위로 올렸다고 오늘로 튀면 보던 자리를 잃습니다.
     */
    private fun dayForBlindSwipe(): String {
        val shown = TodoStore.format(month).take(7)
        lastPicked?.let { if (it.take(7) == shown) return it }

        val today = TodoStore.today()
        return if (today.take(7) == shown) today else TodoStore.format(month)
    }

    // ---------- 날짜 고르기 ----------

    /**
     * 달 이름을 누르면 연·월·일을 직접 고르는 판이 올라옵니다.
     *
     * 미는 것만으로는 몇 년 뒤로 가기 어렵습니다. 고르고 나면 그 날짜를 고른 것으로
     * 보고 아래 내역까지 펴 줍니다 — 날짜까지 골랐는데 달만 넘어가면 한 번 더
     * 눌러야 합니다.
     */
    private fun pickDate() {
        val sb = SheetPickDateBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sb.root)

        val start = TodoStore.parseDate(selected ?: TodoStore.format(month))
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)

        // 글자는 formatter 가 아니라 displayedValues 로 넣습니다. formatter 는 값이
        // 한 번 바뀌기 전까지 가운데 칸에 맨 숫자를 보여주는 오래된 문제가 있습니다.
        sb.pickYear.minValue = thisYear - YEAR_SPAN
        sb.pickYear.maxValue = thisYear + YEAR_SPAN
        sb.pickYear.displayedValues =
            Array(YEAR_SPAN * 2 + 1) { (thisYear - YEAR_SPAN + it).toString() + "년" }
        sb.pickYear.value = start.get(Calendar.YEAR)

        sb.pickMonth.minValue = 1
        sb.pickMonth.maxValue = 12
        sb.pickMonth.displayedValues = Array(12) { (it + 1).toString() + "월" }
        sb.pickMonth.value = start.get(Calendar.MONTH) + 1

        sb.pickDay.minValue = 1
        syncDayRange(sb)
        sb.pickDay.value = start.get(Calendar.DAY_OF_MONTH)

        // 연·월이 바뀌면 그 달에 없는 날(2월 30일 같은)을 고를 수 없게 다시 맞춥니다.
        val onYearMonth = NumberPicker.OnValueChangeListener { _, _, _ -> syncDayRange(sb) }
        sb.pickYear.setOnValueChangedListener(onYearMonth)
        sb.pickMonth.setOnValueChangedListener(onYearMonth)

        sb.btnCancel.setOnClickListener { dialog.dismiss() }
        sb.btnGo.setOnClickListener {
            val picked = Calendar.getInstance()
            picked.set(sb.pickYear.value, sb.pickMonth.value - 1, sb.pickDay.value)

            val key = TodoStore.format(picked)
            choose(key)
            showMonthOf(key)
            refresh()
            if (panelHeight <= 0) animatePanelTo(smallHeight())
            dialog.dismiss()
        }
        dialog.show()
    }

    /** '일' 스피너의 끝을 고른 연·월의 마지막 날에 맞춥니다. */
    private fun syncDayRange(sb: SheetPickDateBinding) {
        val cal = Calendar.getInstance()
        cal.set(sb.pickYear.value, sb.pickMonth.value - 1, 1)
        val last = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (sb.pickDay.maxValue == last) return

        // 글자 배열이 남아 있으면 길이가 맞지 않아 범위를 넓힐 수 없습니다.
        sb.pickDay.displayedValues = null
        sb.pickDay.maxValue = last
        sb.pickDay.displayedValues = Array(last) { (it + 1).toString() + "일" }
    }

    // ---------- 할 일 ----------

    /**
     * 추가·수정 모두 목록 화면과 같은 시트입니다.
     * 새로 추가할 때는 고른 날짜가 기한의 초기값이 됩니다 — 8월 20일을 보고 있는데
     * 오늘 날짜로 채워지면 매번 다시 골라야 합니다. 고른 날이 없으면 오늘입니다.
     */
    private fun openAddSheet(existing: Todo? = null) {
        AddTodoSheet(this, existing, selected ?: TodoStore.today()) { todo ->
            TodoStore.upsert(this, todo)
            Reminders.announce(this, todo, Reminders.schedule(this, todo))
            // 기한을 다른 날로 바꿔 저장했으면 그 날로 따라갑니다.
            // 방금 넣은 것이 어디에도 안 보이면 사라진 줄 압니다.
            choose(todo.date)
            showMonthOf(todo.date)
            refresh()
            if (panelHeight <= 0) animatePanelTo(smallHeight())
        }.show()
    }

    private fun refresh() {
        val byDate = TodoStore.byDate(this)

        b.tvMonth.text = TodoStore.prettyMonth(month) + "  ▾"
        syncTodayButton()

        val days = buildMonth(byDate)
        monthRows = days.size / 7
        val rowHeight = rowHeight()
        // 아직 격자를 재기 전이면 칸을 만들지 않습니다. 잘못된 높이로 만들어
        // 두면 곧바로 버리게 되고, 그 헛수고가 화면 여는 시간이 됩니다.
        grid.submit(
            if (rowHeight > 0) days else emptyList(), selected ?: "", rowHeight, panelHeight <= 0
        )

        // 내역이 접혀 있으면 아래는 그릴 것이 없습니다.
        val key = selected ?: return

        // 목록 화면과 같은 순서입니다 — 남은 것 먼저, 완료는 아래로.
        val items = byDate[key].orEmpty()
        val pending = items.filter { !it.done }.sortedWith(TodoStore.pendingOrder)
        val done = items.filter { it.done }.sortedByDescending { it.id }

        b.tvDayTitle.text = dayTitle(key, pending.size, done.size)
        todos.submit((pending + done).map { Row.Item(it) })
        b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 격자가 남은 자리를 꽉 채우는 칸 높이(px). 아직 재기 전이면 0. */
    private fun rowHeight(): Int {
        val gridH = b.grid.height
        return if (gridH > 0 && monthRows > 0) gridH / monthRows else 0
    }

    /**
     * '오늘' 되돌아가기는 오늘이 아닌 달을 볼 때만 뜹니다.
     *
     * 내역이 펴져 있으면 감춥니다 — 그때는 달을 훑어보는 중이 아니고, 아래
     * 목록을 가리게 됩니다. 달을 넘기면 내역이 함께 접히므로, 훑어보는 동안은
     * 언제나 보입니다.
     */
    private fun syncTodayButton() {
        val sameMonth = TodoStore.format(month).take(7) == TodoStore.today().take(7)
        b.btnToday.visibility =
            if (panelHeight <= 0 && !sameMonth) View.VISIBLE else View.GONE
    }

    /** "오늘 · 8월 9일 (일) · 할 일 2개" */
    private fun dayTitle(key: String, pending: Int, done: Int): String {
        val counts = mutableListOf<String>()
        if (pending > 0) counts.add("할 일 " + pending + "개")
        if (done > 0) counts.add("완료 " + done + "개")
        return TodoStore.prettyDate(key) +
            (if (counts.isEmpty()) "" else " · " + counts.joinToString(" · "))
    }

    /**
     * 그 달의 격자를 만듭니다.
     *
     * 첫 줄을 채우려 앞 달 며칠을, 마지막 줄을 채우려 다음 달 며칠을 함께 넣습니다.
     * 줄 수는 달마다 계산합니다 — 여섯 줄로 고정하면 다섯 줄이면 되는 달에 빈 줄이
     * 하나 남습니다.
     */
    private fun buildMonth(byDate: Map<String, List<Todo>>): List<Day> {
        val cur = Calendar.getInstance()
        cur.time = month.time
        val monthIndex = cur.get(Calendar.MONTH)
        val daysInMonth = cur.getActualMaximum(Calendar.DAY_OF_MONTH)

        // 1일이 무슨 요일인지 = 앞에 채워야 할 칸 수 (일요일이 0)
        val lead = cur.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        val cells = ((lead + daysInMonth + 6) / 7) * 7
        cur.add(Calendar.DAY_OF_MONTH, -lead)

        val out = ArrayList<Day>(cells)
        repeat(cells) {
            val key = TodoStore.format(cur)
            val items = byDate[key].orEmpty()
            // 칸에 적는 차례도 아래 목록과 같습니다 — 남은 것 먼저, 완료는 뒤로.
            val ordered = items.filter { !it.done }.sortedWith(TodoStore.pendingOrder) +
                items.filter { it.done }
            out.add(
                Day(
                    key = key,
                    dayOfMonth = cur.get(Calendar.DAY_OF_MONTH),
                    inMonth = cur.get(Calendar.MONTH) == monthIndex,
                    pending = items.count { !it.done },
                    done = items.count { it.done },
                    overdue = items.any { TodoStore.isOverdue(it) },
                    entries = ordered.take(MAX_CELL_ENTRIES).map {
                        Day.Entry(it.text, it.done, TodoStore.isOverdue(it))
                    },
                    total = items.size
                )
            )
            cur.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    companion object {
        private const val STATE_SELECTED = "selected"
        private const val STATE_LAST_PICKED = "last_picked"
        private const val STATE_MONTH = "month"
        private const val STATE_PANEL = "panel"

        /** 날짜 고르기의 연도 폭. 올해 기준 앞뒤로 이만큼씩 돌릴 수 있습니다. */
        private const val YEAR_SPAN = 30

        /**
         * 칸 하나에 담아 두는 할 일 수. 가장 큰 칸에 들어가는 줄 수보다 넉넉하면
         * 되고, 넘치는 몫은 칸이 '+N' 으로 알립니다.
         */
        private const val MAX_CELL_ENTRIES = 5

        /** '조금' 자리에서 내역이 갖는 몫 */
        private const val PANEL_SMALL_RATIO = 0.42f

        /** 이만큼 넘게 펴야 목록이 굴러갑니다('많이' 에 가까울 때) */
        private const val LIST_SCROLL_RATIO = 0.8f

        /** 가로로 끌 때 실제로 따라가는 정도. 옆의 빈 자리를 덜 드러냅니다. */
        private const val MONTH_FOLLOW = 0.35f

        /** 이만큼(dp) 넘게 밀어야 달이 넘어갑니다 */
        private const val MONTH_MIN_DP = 56f

        private const val SETTLE_MS = 200L
        private const val MONTH_OUT_MS = 130L
        private const val MONTH_IN_MS = 170L
    }
}
