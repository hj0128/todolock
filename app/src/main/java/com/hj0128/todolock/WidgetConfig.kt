package com.hj0128.todolock

import android.content.Context

/**
 * 위젯 겉모습 설정(불투명도 · 글꼴 크기).
 *
 * 할 일 데이터와 성격이 달라 별도 저장소에 둡니다.
 * 위젯 본체(TodoWidget)와 행 공급자(TodoWidgetService) 가 같은 값을 읽어야 하므로
 * 크기 표까지 여기 모아 둡니다.
 */
object WidgetConfig {

    private const val PREF = "todolock_widget"
    private const val KEY_OPACITY = "opacity"
    private const val KEY_FONT = "font_step"

    /** 완전히 투명해지면 위젯을 다시 찾을 수 없어 하한을 둡니다. */
    const val MIN_OPACITY = 20
    const val MAX_OPACITY = 100

    /** 0 = 작게, 1 = 보통, 2 = 크게 */
    const val FONT_STEPS = 3

    /**
     * 세 단계 모두 한 칸씩 올려 잡았습니다. 예전 '크게'(제목 16.5sp)가 실제로는
     * 보통 크기로 읽혔고, 아랫줄은 9~11.5sp 라 본문 최소 가독선 아래였습니다.
     *
     * 아랫줄(기한 · 미리 알림 · 메모)은 제목 대비 비율도 함께 올렸습니다.
     * 부가 정보가 아니라 '언제까지 · 언제 알림 · 무슨 내용' 이라 실제로 읽는 줄입니다.
     */
    private val TITLE_SP = floatArrayOf(14f, 16.5f, 19f)
    private val SUB_SP = floatArrayOf(11f, 12.5f, 14.5f)
    /** 헤더는 항목 제목보다 반 단계만 크게 둬서 층이 구분되게 합니다. */
    private val HEADER_SP = floatArrayOf(15f, 17f, 19.5f)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun opacity(ctx: Context): Int =
        prefs(ctx).getInt(KEY_OPACITY, MAX_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    fun setOpacity(ctx: Context, percent: Int) =
        prefs(ctx).edit()
            .putInt(KEY_OPACITY, percent.coerceIn(MIN_OPACITY, MAX_OPACITY))
            .apply()

    fun fontStep(ctx: Context): Int =
        prefs(ctx).getInt(KEY_FONT, 1).coerceIn(0, FONT_STEPS - 1)

    fun setFontStep(ctx: Context, step: Int) =
        prefs(ctx).edit().putInt(KEY_FONT, step.coerceIn(0, FONT_STEPS - 1)).apply()

    /**
     * 헤더와 각 할 일 행(앞쪽 판)의 알파. 설정값을 그대로 씁니다.
     */
    fun panelAlpha(ctx: Context): Int = opacity(ctx) * 255 / 100

    /**
     * 뒤쪽 목록 판의 알파. 앞쪽 판보다 옅게 해서 두 층이 구분되도록 합니다.
     * 설정값에 비례하므로 불투명도를 낮추면 둘 다 함께 옅어집니다.
     */
    fun listAlpha(ctx: Context): Int = (panelAlpha(ctx) * LIST_RATIO).toInt()

    private const val LIST_RATIO = 0.45f

    fun titleSp(ctx: Context): Float = TITLE_SP[fontStep(ctx)]

    fun subSp(ctx: Context): Float = SUB_SP[fontStep(ctx)]

    fun headerSp(ctx: Context): Float = HEADER_SP[fontStep(ctx)]
}
