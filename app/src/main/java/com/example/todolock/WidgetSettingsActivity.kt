package com.example.todolock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.todolock.databinding.ActivityWidgetSettingsBinding

/**
 * 위젯의 톱니바퀴로 열리는 겉모습 설정.
 * 홈 화면 위에 카드로 떠서, 바꾸는 즉시 위젯에 반영됩니다.
 */
class WidgetSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityWidgetSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityWidgetSettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

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

        b.btnDone.setOnClickListener { finish() }
        b.scrim.setOnClickListener { finish() }
    }

    private fun showOpacity(percent: Int) {
        b.tvOpacity.text = percent.toString() + "%"
    }
}
