package com.hj0128.todolock

import android.app.Activity
import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat

/**
 * 강조색 팔레트 선택 (색상표 16색).
 *
 * 앱 화면은 테마 오버레이로 바꿉니다. 레이아웃이 ?attr/accent* 를 가리키고 있으므로
 * 액티비티에 오버레이를 얹으면 버튼·아이콘·상태바가 한 번에 따라옵니다.
 *
 * 위젯은 런처가 그리기 때문에 ?attr 이 풀리지 않습니다. 그래서 위젯 쪽은 색을
 * 여기서 직접 꺼내 RemoteViews 로 넣습니다(TodoWidget · TodoWidgetService).
 *
 * 색은 라이트/다크 두 벌이 같은 이름으로 있어(values, values-night) 어느 쪽을
 * 쓸지는 시스템 설정이 정합니다. 그래서 여기에는 다크용 분기가 없습니다.
 *
 * '임의의 색을 자유롭게' 가 아니라 16색 표인 이유: 안드로이드 테마는 컴파일된
 * 리소스라 고른 색으로 테마를 그 자리에서 만들 수 없습니다. 색마다 밝기·채도를
 * 맞춰 두어야 어떤 색에서도 글자가 읽히는데, 그 보정을 미리 해 둔 것이 이 표입니다.
 */
object ThemeConfig {

    private const val PREF = "todolock_theme"
    private const val KEY_PALETTE = "palette"

    /** 기본값(하늘색)은 테마에 이미 들어 있어 오버레이가 필요 없습니다. */
    const val DEFAULT = 0

    /** 색상표에 보여줄 순서 그대로. 접근성 설명에도 씁니다. */
    val NAMES = arrayOf(
        "하늘", "파랑", "남색", "보라", "자주", "분홍", "장미", "빨강",
        "주황", "호박", "노랑", "라임", "초록", "에메랄드", "청록", "회색"
    )

    /** 색상표에 찍을 동그라미 색 (= 제목·아이콘 색) */
    private val HEADING = intArrayOf(
        R.color.p0_heading, R.color.p1_heading, R.color.p2_heading, R.color.p3_heading,
        R.color.p4_heading, R.color.p5_heading, R.color.p6_heading, R.color.p7_heading,
        R.color.p8_heading, R.color.p9_heading, R.color.p10_heading, R.color.p11_heading,
        R.color.p12_heading, R.color.p13_heading, R.color.p14_heading, R.color.p15_heading
    )

    private val PANEL = intArrayOf(
        R.color.p0_panel, R.color.p1_panel, R.color.p2_panel, R.color.p3_panel,
        R.color.p4_panel, R.color.p5_panel, R.color.p6_panel, R.color.p7_panel,
        R.color.p8_panel, R.color.p9_panel, R.color.p10_panel, R.color.p11_panel,
        R.color.p12_panel, R.color.p13_panel, R.color.p14_panel, R.color.p15_panel
    )

    /** 색상표 동그라미 안에 그릴 체크 표시의 색 */
    private val ON = intArrayOf(
        R.color.p0_on, R.color.p1_on, R.color.p2_on, R.color.p3_on,
        R.color.p4_on, R.color.p5_on, R.color.p6_on, R.color.p7_on,
        R.color.p8_on, R.color.p9_on, R.color.p10_on, R.color.p11_on,
        R.color.p12_on, R.color.p13_on, R.color.p14_on, R.color.p15_on
    )

    private val OVERLAY = intArrayOf(
        0,
        R.style.ThemeOverlay_TodoLock_P1, R.style.ThemeOverlay_TodoLock_P2,
        R.style.ThemeOverlay_TodoLock_P3, R.style.ThemeOverlay_TodoLock_P4,
        R.style.ThemeOverlay_TodoLock_P5, R.style.ThemeOverlay_TodoLock_P6,
        R.style.ThemeOverlay_TodoLock_P7, R.style.ThemeOverlay_TodoLock_P8,
        R.style.ThemeOverlay_TodoLock_P9, R.style.ThemeOverlay_TodoLock_P10,
        R.style.ThemeOverlay_TodoLock_P11, R.style.ThemeOverlay_TodoLock_P12,
        R.style.ThemeOverlay_TodoLock_P13, R.style.ThemeOverlay_TodoLock_P14,
        R.style.ThemeOverlay_TodoLock_P15
    )

    val COUNT = NAMES.size

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun palette(ctx: Context): Int =
        prefs(ctx).getInt(KEY_PALETTE, DEFAULT).coerceIn(0, COUNT - 1)

    fun setPalette(ctx: Context, palette: Int) =
        prefs(ctx).edit().putInt(KEY_PALETTE, palette.coerceIn(0, COUNT - 1)).apply()

    /**
     * 액티비티 onCreate 의 맨 앞(setContentView 보다 먼저)에서 부릅니다.
     */
    fun apply(activity: Activity) {
        val overlay = OVERLAY[palette(activity)]
        if (overlay != 0) activity.setTheme(overlay)
    }

    /**
     * 지금 얹혀 있는 테마에서 색 하나를 꺼냅니다.
     *
     * 고른 팔레트가 오버레이로 덮여 있으므로 @color 를 직접 읽으면 언제나 하늘색이
     * 나옵니다. 코드에서 색을 넣어야 하는 곳(어댑터)은 반드시 이쪽을 거쳐야 합니다.
     */
    fun attrColor(ctx: Context, attr: Int, fallback: Int = Color.GRAY): Int {
        val ta = ctx.obtainStyledAttributes(intArrayOf(attr))
        val c = ta.getColor(0, fallback)
        ta.recycle()
        return c
    }

    /** 제목·아이콘 색 (위젯과 색상표용) */
    fun headingColor(ctx: Context, palette: Int = palette(ctx)): Int =
        ContextCompat.getColor(ctx, HEADING[palette.coerceIn(0, COUNT - 1)])

    /** 위젯 헤더·목록 판 배경색 */
    fun panelColor(ctx: Context, palette: Int = palette(ctx)): Int =
        ContextCompat.getColor(ctx, PANEL[palette.coerceIn(0, COUNT - 1)])

    /** 그 색 위에 얹는 글자·체크 색 */
    fun onColor(ctx: Context, palette: Int = palette(ctx)): Int =
        ContextCompat.getColor(ctx, ON[palette.coerceIn(0, COUNT - 1)])
}
