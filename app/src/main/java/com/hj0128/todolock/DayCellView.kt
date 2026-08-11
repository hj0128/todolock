package com.hj0128.todolock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat

/**
 * 칸을 그리는 데 쓰는 색·굵기·크기 묶음.
 *
 * 한 달이 마흔두 칸이라 칸마다 테마를 다시 읽고 Paint 를 새로 만들면 그 자체가
 * 부담입니다. 격자 하나에 하나만 만들어 모든 칸이 나눠 씁니다. 그리기는 한
 * 스레드에서 차례로 일어나므로 색만 그때그때 바꿔 써도 안전합니다.
 */
class DayCellStyle(ctx: Context) {

    private val dm = ctx.resources.displayMetrics

    val fill = ThemeConfig.attrColor(ctx, R.attr.accentFill)
    val onFill = ThemeConfig.attrColor(ctx, R.attr.onAccentFill)
    val accent = ThemeConfig.attrColor(ctx, R.attr.accentHeading)
    val stroke = ThemeConfig.attrColor(ctx, R.attr.accentStroke)
    val text = ThemeConfig.attrColor(ctx, android.R.attr.textColorPrimary)
    val secondary = ThemeConfig.attrColor(ctx, android.R.attr.textColorSecondary)
    val overdue = ContextCompat.getColor(ctx, R.color.overdue)

    val circleRadius = dp(15f)
    /** 칸이 낮아져도 이보다 작게는 줄이지 않습니다. 숫자가 동그라미를 비집고 나옵니다. */
    val minCircleRadius = dp(11f)
    val ringWidth = dp(1.5f)

    val topPadding = dp(3f)
    val sidePadding = dp(3f)
    /** 숫자와 그 아래 내용 사이 */
    val contentGapSize = dp(1.5f)

    /** 한 항목을 감싸는 조각의 안쪽 여백과 모서리 */
    val chipPaddingX = dp(3f)
    val chipPaddingY = dp(1f)
    val chipRadius = dp(3f)
    /** 조각과 조각 사이 */
    val chipGap = dp(1.5f)
    /** 조각 바탕은 글자색을 아주 옅게 깔아 씁니다 */
    val chipBackAlpha = 38
    val chipBackAlphaDone = 22
    val contentGap = contentGapSize

    val dotRadius = dp(2.5f)
    val dotGap = dp(3f)

    val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, dm)
    }

    /** 칸 안에 적는 할 일 한 줄 */
    val entryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, dm)
    }

    val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 조각 하나의 높이 (글자 + 안쪽 여백) */
    val chipHeight = (entryPaint.descent() - entryPaint.ascent()) + chipPaddingY * 2

    /** 한 항목이 차지하는 높이 (조각 + 아래 틈) */
    val entryLineHeight = chipHeight + chipGap

    val normalFace: Typeface = Typeface.DEFAULT
    val boldFace: Typeface = Typeface.DEFAULT_BOLD

    private fun dp(v: Float) = v * dm.density
}

/**
 * 달력 한 칸을 캔버스에 바로 그리는 뷰.
 *
 * 처음에는 XML 로 만들었습니다 — 칸마다 LinearLayout 하나와 TextView 둘.
 * 그런데 한 달이면 그게 백스물여섯 개가 되고, TextView 는 글자를 재는 것만으로도
 * 품이 들어 달력을 여는 데 반 초 넘게 쓰였습니다. 여기서는 자식 뷰가 없어
 * 재고 배치할 것도 없습니다.
 *
 * 날짜 숫자를 칸 위쪽에 붙이는 이유: 그 아래로 할 일을 적어 내려가는데, 숫자가
 * 가운데 있으면 내용이 있는 날과 없는 날의 숫자 높이가 어긋납니다.
 *
 * 누를 때 물결(ripple)을 두지 않았습니다. 칸마다 물결 그림을 하나씩 만들어야 하고,
 * 어차피 누르는 즉시 그 날짜에 동그라미가 채워져 눌렸다는 것이 바로 보입니다.
 */
class DayCellView(context: Context, private val style: DayCellStyle) : View(context) {

    private var day: Day? = null
    private var isSelected = false
    private var isToday = false
    private val chipRect = RectF()

    /**
     * 칸에 할 일을 글로 적을지, 점으로만 알릴지.
     *
     * 달력이 화면을 꽉 채울 때만 글입니다. 아래 내역을 펴면 칸이 낮아지는데,
     * 그 좁은 자리에 글을 욱여넣으면 잘린 토막만 남아 읽히지도 않습니다.
     */
    var textMode = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        isClickable = true
        isFocusable = true
    }

    fun bind(day: Day, selected: Boolean, today: Boolean) {
        this.day = day
        this.isSelected = selected
        this.isToday = today
        contentDescription = TodoStore.plainDate(context, day.key)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val d = day ?: return
        val dim = if (d.inMonth) 1f else DIM

        val cx = width / 2f
        val radius = minOf(style.circleRadius, maxOf(style.minCircleRadius, height / 4f))
        val cy = style.topPadding + radius

        if (isSelected) {
            style.shapePaint.style = Paint.Style.FILL
            style.shapePaint.color = style.fill
            style.shapePaint.alpha = (255 * dim).toInt()
            canvas.drawCircle(cx, cy, radius, style.shapePaint)
        } else if (isToday) {
            style.shapePaint.style = Paint.Style.STROKE
            style.shapePaint.strokeWidth = style.ringWidth
            style.shapePaint.color = style.stroke
            style.shapePaint.alpha = (255 * dim).toInt()
            canvas.drawCircle(cx, cy, radius - style.ringWidth / 2f, style.shapePaint)
        }

        val np = style.numberPaint
        np.typeface = if (isSelected || isToday) style.boldFace else style.normalFace
        np.color = when {
            isSelected -> style.onFill
            isToday -> style.accent
            else -> style.text
        }
        np.alpha = (255 * dim).toInt()
        canvas.drawText(
            d.dayOfMonth.toString(), cx, cy - (np.descent() + np.ascent()) / 2f, np
        )

        if (d.total == 0) return

        val contentTop = cy + radius + style.contentGap
        val room = height - contentTop - style.topPadding
        val lines = if (textMode) (room / style.entryLineHeight).toInt() else 0

        if (lines <= 0) {
            drawDots(canvas, d, cx, contentTop, dim)
        } else {
            drawEntries(canvas, d, contentTop, lines, dim)
        }
    }

    /**
     * 칸에 적어 내려갑니다.
     *
     * 한 항목마다 옅은 바탕을 깔아 조각으로 만듭니다 — 글자만 줄줄이 놓으면 두
     * 줄짜리 할 일 하나인지 한 줄짜리 둘인지 구분되지 않습니다.
     */
    private fun drawEntries(canvas: Canvas, d: Day, top: Float, lines: Int, dim: Float) {
        val p = style.entryPaint
        val back = style.shapePaint
        back.style = Paint.Style.FILL
        p.isStrikeThruText = false

        val left = style.sidePadding
        val right = width - style.sidePadding

        // 다 못 적으면 마지막 줄을 '+N' 으로 내줍니다. 여기까지 왔다면 줄이
        // 둘 이상이거나 할 일이 하나뿐이므로, 내줄 줄이 없어 곤란해지지 않습니다.
        val bodyLines =
            if (d.total > lines) lines - 1 else minOf(lines, d.entries.size)
        val more = if (d.total > bodyLines) "+" + (d.total - bodyLines) else null

        val textLeft = left + style.chipPaddingX
        val maxWidth = right - textLeft - style.chipPaddingX

        var y = top
        for (i in 0 until minOf(bodyLines, d.entries.size)) {
            val e = d.entries[i]
            val tone = when {
                e.overdue -> style.overdue
                e.done -> style.secondary
                else -> style.accent
            }

            chipRect.set(left, y, right, y + style.chipHeight)
            back.color = tone
            back.alpha =
                ((if (e.done) style.chipBackAlphaDone else style.chipBackAlpha) * dim).toInt()
            canvas.drawRoundRect(chipRect, style.chipRadius, style.chipRadius, back)

            p.color = if (e.done) style.secondary else tone
            p.alpha = (255 * dim * (if (e.done) 0.6f else 1f)).toInt()
            p.isStrikeThruText = e.done
            canvas.drawText(
                fit(e.text, maxWidth, p),
                textLeft,
                y + style.chipPaddingY - p.ascent(),
                p
            )
            y += style.entryLineHeight
        }
        p.isStrikeThruText = false

        if (more == null) return
        p.color = style.secondary
        p.alpha = (255 * dim * 0.75f).toInt()
        canvas.drawText(more, textLeft, y + style.chipPaddingY - p.ascent(), p)
    }

    /** 글을 제대로 적을 수 없는 낮은 칸에서. 남은 개수를 점으로 알립니다. */
    private fun drawDots(canvas: Canvas, d: Day, cx: Float, top: Float, dim: Float) {
        val count = when {
            d.pending > 0 -> minOf(d.pending, 3)
            else -> 1
        }
        style.shapePaint.style = Paint.Style.FILL
        style.shapePaint.color = when {
            d.overdue -> style.overdue
            isSelected -> style.onFill
            d.pending == 0 -> style.secondary
            else -> style.accent
        }
        style.shapePaint.alpha = (255 * dim * (if (d.pending == 0) 0.45f else 1f)).toInt()

        val step = style.dotRadius * 2 + style.dotGap
        val spanWidth = count * style.dotRadius * 2 + (count - 1) * style.dotGap
        var x = cx - spanWidth / 2f + style.dotRadius
        val y = top + style.dotRadius
        repeat(count) {
            canvas.drawCircle(x, y, style.dotRadius, style.shapePaint)
            x += step
        }
    }

    /** 칸 폭에 맞춰 자르고 꼬리에 말줄임을 답니다. */
    private fun fit(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val tail = "…"
        val room = maxWidth - paint.measureText(tail)
        if (room <= 0f) return tail
        val kept = paint.breakText(text, true, room, null)
        return text.substring(0, kept) + tail
    }

    private companion object {
        /** 다른 달 날짜의 흐림 정도 */
        const val DIM = 0.35f
    }
}
