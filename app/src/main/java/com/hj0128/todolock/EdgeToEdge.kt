package com.hj0128.todolock

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 35 부터는 엣지투엣지가 강제돼 상태바 뒤까지 그려집니다.
 * 레이아웃의 fitsSystemWindows 는 이제 효과가 없어(강제로 decorFitsSystemWindows(false))
 * 시스템 바 인셋만큼 직접 패딩을 더해 줍니다. XML 에 이미 있는 패딩은 유지합니다.
 */
fun View.applySystemBarInsets() {
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
        insets
    }
}
