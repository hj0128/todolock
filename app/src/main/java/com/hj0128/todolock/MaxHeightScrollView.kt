package com.hj0128.todolock

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 최대 높이가 정해진 ScrollView.
 *
 * 팝업의 할 일이 10개쯤 쌓이면 카드가 화면 밖으로 자라나 '앱 열기 / 닫기' 버튼이 잘렸습니다.
 * ScrollView 는 android:maxHeight 를 존중하지 않으므로 측정 단계에서 직접 상한을 둡니다.
 * 내용이 적으면 그만큼만 차지하고(wrap_content), 넘치면 상한에서 멈추고 스크롤됩니다.
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = (resources.displayMetrics.heightPixels * MAX_SCREEN_RATIO).toInt()
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
        )
    }

    private companion object {
        /** 화면 높이의 이 비율까지만. 제목·개수·버튼이 들어갈 자리를 남겨둔 값입니다. */
        const val MAX_SCREEN_RATIO = 0.42f
    }
}
