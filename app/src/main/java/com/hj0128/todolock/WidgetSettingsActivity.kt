package com.hj0128.todolock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hj0128.todolock.databinding.ActivityWidgetSettingsBinding

/**
 * 위젯의 톱니바퀴로 열리는 겉모습 설정.
 * 홈 화면 위에 카드로 떠서, 바꾸는 즉시 위젯에 반영됩니다.
 */
class WidgetSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityWidgetSettingsBinding

    /**
     * 들어올 때의 값. '완료' 없이 닫히면 이 값으로 되돌립니다.
     *
     * 슬라이더를 움직이는 동안 위젯이 바로 바뀌어야 결과를 보고 정할 수 있으므로,
     * 미리보기를 위해 값을 즉시 저장합니다. 그래서 취소 경로에서 직접 되돌려야 합니다.
     */
    private var origOpacity = 0
    private var origFontStep = 0

    /** '완료' 를 눌렀는지. 누르지 않고 닫았다면 되돌립니다. */
    private var committed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWidgetSettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        // 화면 회전으로 다시 만들어질 때는 이미 값이 바뀐 상태이므로
        // 저장소가 아니라 보관해 둔 원래 값을 되살려야 합니다.
        if (savedInstanceState != null) {
            origOpacity = savedInstanceState.getInt(KEY_ORIG_OPACITY, WidgetConfig.opacity(this))
            origFontStep = savedInstanceState.getInt(KEY_ORIG_FONT, WidgetConfig.fontStep(this))
        } else {
            origOpacity = WidgetConfig.opacity(this)
            origFontStep = WidgetConfig.fontStep(this)
        }

        // 초기값을 먼저 반영한 뒤 리스너를 붙입니다 (리스너 오작동 방지)
        val opacity = WidgetConfig.opacity(this)
        b.slOpacity.value = opacity.toFloat()
        showOpacity(opacity)

        b.rgFont.check(
            when (WidgetConfig.fontStep(this)) {
                0 -> R.id.rbSmall
                2 -> R.id.rbLarge
                else -> R.id.rbMedium
            }
        )

        b.slOpacity.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            WidgetConfig.setOpacity(this, v)
            showOpacity(v)
            TodoWidget.refresh(this)
        }

        b.rgFont.setOnCheckedChangeListener { _, id ->
            WidgetConfig.setFontStep(
                this,
                when (id) {
                    R.id.rbSmall -> 0
                    R.id.rbLarge -> 2
                    else -> 1
                }
            )
            TodoWidget.refresh(this)
        }

        b.btnDone.setOnClickListener {
            committed = true
            finish()
        }
        // 밖을 누르거나 뒤로 가면 확정하지 않은 것으로 봅니다 (onDestroy 에서 되돌림).
        b.scrim.setOnClickListener { finish() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ORIG_OPACITY, origOpacity)
        outState.putInt(KEY_ORIG_FONT, origFontStep)
    }

    override fun onDestroy() {
        // isFinishing 을 함께 보는 이유: 화면 회전으로 소멸될 때는 되돌리면 안 됩니다.
        if (isFinishing && !committed) {
            WidgetConfig.setOpacity(this, origOpacity)
            WidgetConfig.setFontStep(this, origFontStep)
            TodoWidget.refresh(this)
        }
        super.onDestroy()
    }

    private fun showOpacity(percent: Int) {
        b.tvOpacity.text = percent.toString() + "%"
    }

    private companion object {
        const val KEY_ORIG_OPACITY = "orig_opacity"
        const val KEY_ORIG_FONT = "orig_font"
    }
}
