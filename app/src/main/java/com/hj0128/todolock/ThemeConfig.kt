package com.hj0128.todolock

import android.app.Activity
import android.content.Context
import androidx.core.content.ContextCompat

/**
 * 강조색 팔레트 선택.
 *
 * 앱 화면은 테마 오버레이로 바꿉니다. 레이아웃이 ?attr/accent* 를 가리키고 있으므로
 * 액티비티에 오버레이를 얹으면 버튼·아이콘·상태바가 한 번에 따라옵니다.
 *
 * 위젯은 런처가 그리기 때문에 ?attr 이 풀리지 않습니다. 그래서 위젯 쪽은 색을
 * 여기서 직접 꺼내 RemoteViews 로 넣습니다(TodoWidget · TodoWidgetService).
 *
 * 색 자체는 라이트/다크 두 벌이 같은 이름으로 있어(values, values-night)
 * 어느 쪽을 쓸지는 시스템 설정이 정합니다.
 */
object ThemeConfig {

    private const val PREF = "todolock_theme"
    private const val KEY_PALETTE = "palette"

    const val SKY = 0
    const val GREEN = 1
    const val PURPLE = 2
    const val ORANGE = 3

    /** 설정 화면에 보여줄 순서와 이름. */
    val NAMES = arrayOf("하늘", "초록", "보라", "주황")

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun palette(ctx: Context): Int =
        prefs(ctx).getInt(KEY_PALETTE, SKY).coerceIn(SKY, ORANGE)

    fun setPalette(ctx: Context, palette: Int) =
        prefs(ctx).edit().putInt(KEY_PALETTE, palette.coerceIn(SKY, ORANGE)).apply()

    /**
     * 액티비티 onCreate 의 맨 앞(setContentView 보다 먼저)에서 부릅니다.
     * 기본값인 하늘색은 테마에 이미 들어 있어 얹을 것이 없습니다.
     */
    fun apply(activity: Activity) {
        when (palette(activity)) {
            GREEN -> activity.setTheme(R.style.ThemeOverlay_TodoLock_Green)
            PURPLE -> activity.setTheme(R.style.ThemeOverlay_TodoLock_Purple)
            ORANGE -> activity.setTheme(R.style.ThemeOverlay_TodoLock_Orange)
        }
    }

    /** 제목·아이콘 색 (위젯용) */
    fun headingColor(ctx: Context): Int = ContextCompat.getColor(
        ctx,
        when (palette(ctx)) {
            GREEN -> R.color.green_heading
            PURPLE -> R.color.purple_heading
            ORANGE -> R.color.orange_heading
            else -> R.color.sky_heading
        }
    )

    /** 위젯 헤더·목록 판 배경색 */
    fun panelColor(ctx: Context): Int = ContextCompat.getColor(
        ctx,
        when (palette(ctx)) {
            GREEN -> R.color.green_panel
            PURPLE -> R.color.purple_panel
            ORANGE -> R.color.orange_panel
            else -> R.color.sky_panel
        }
    )
}
