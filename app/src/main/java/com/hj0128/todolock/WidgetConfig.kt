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
    private const val KEY_TITLE_SP = "title_sp"

    /** 3단계(작게·보통·크게)를 쓰던 시절의 키. 지금은 값을 이어받는 데만 씁니다. */
    private const val KEY_FONT_STEP = "font_step"

    /** 완전히 투명해지면 위젯을 다시 찾을 수 없어 하한을 둡니다. */
    const val MIN_OPACITY = 20
    const val MAX_OPACITY = 100

    /**
     * 글자 크기는 제목 기준 하나로 정하고 나머지는 비율로 따라갑니다.
     * 세 값을 따로 고르게 하면 조합이 어긋나 층이 무너지는데, 실제로 원하는 것은
     * '전체적으로 크게/작게' 이지 '제목만 크게' 가 아닙니다.
     */
    const val MIN_TITLE_SP = 11f
    const val MAX_TITLE_SP = 24f
    const val DEFAULT_TITLE_SP = 16.5f

    /** 아랫줄(기한 · 미리 알림 · 메모)과 헤더의 제목 대비 비율. */
    private const val SUB_RATIO = 0.76f
    private const val HEADER_RATIO = 1.03f

    /** 3단계를 쓰던 사용자의 설정을 이어받기 위한 값(작게 · 보통 · 크게). */
    private val LEGACY_STEP_SP = floatArrayOf(14f, 16.5f, 19f)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun opacity(ctx: Context): Int =
        prefs(ctx).getInt(KEY_OPACITY, MAX_OPACITY).coerceIn(MIN_OPACITY, MAX_OPACITY)

    fun setOpacity(ctx: Context, percent: Int) =
        prefs(ctx).edit()
            .putInt(KEY_OPACITY, percent.coerceIn(MIN_OPACITY, MAX_OPACITY))
            .apply()

    /**
     * 제목 글자 크기(sp). 값을 정한 적이 없으면 예전 3단계 설정을 이어받고,
     * 그것도 없으면 기본값입니다.
     */
    fun titleSp(ctx: Context): Float {
        val p = prefs(ctx)
        if (p.contains(KEY_TITLE_SP)) {
            return p.getFloat(KEY_TITLE_SP, DEFAULT_TITLE_SP)
                .coerceIn(MIN_TITLE_SP, MAX_TITLE_SP)
        }
        val step = p.getInt(KEY_FONT_STEP, 1).coerceIn(0, LEGACY_STEP_SP.size - 1)
        return LEGACY_STEP_SP[step]
    }

    fun setTitleSp(ctx: Context, sp: Float) =
        prefs(ctx).edit()
            .putFloat(KEY_TITLE_SP, sp.coerceIn(MIN_TITLE_SP, MAX_TITLE_SP))
            .apply()

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

    fun subSp(ctx: Context): Float = titleSp(ctx) * SUB_RATIO

    /** 헤더는 항목 제목보다 반 단계만 크게 둬서 층이 구분되게 합니다. */
    fun headerSp(ctx: Context): Float = titleSp(ctx) * HEADER_RATIO
}
