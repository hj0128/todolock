package com.hj0128.todolock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hj0128.todolock.databinding.ActivitySettingsBinding

/**
 * 설정 화면.
 *
 * 목록 화면 아래에 카드로 붙어 있던 것을 분리했습니다.
 * 항목이 늘면서 카드가 길어져 할 일 목록이 눌리는 문제가 있었습니다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 고른 강조색을 입힙니다. setContentView 보다 먼저여야 합니다.
        ThemeConfig.apply(this)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }

        // 초기 상태를 먼저 반영한 뒤 리스너를 붙입니다 (리스너 오작동 방지)
        b.swEnabled.isChecked = TodoStore.isEnabled(this)
        b.swEnabled.setOnCheckedChangeListener { _, checked ->
            TodoStore.setEnabled(this, checked)
            if (checked) {
                UnlockService.start(this)
                Watchdog.arm(this)
            } else {
                UnlockService.stop(this)
                Watchdog.cancel(this)
            }
        }

        b.rgMode.check(
            when (TodoStore.getMode(this)) {
                TodoStore.MODE_HOURLY -> R.id.rbHourly
                TodoStore.MODE_ONCE_A_DAY -> R.id.rbOnce
                else -> R.id.rbAlways
            }
        )
        b.rgMode.setOnCheckedChangeListener { _, id ->
            TodoStore.setMode(
                this,
                when (id) {
                    R.id.rbHourly -> TodoStore.MODE_HOURLY
                    R.id.rbOnce -> TodoStore.MODE_ONCE_A_DAY
                    else -> TodoStore.MODE_ALWAYS
                }
            )
        }

        b.rgRemind.check(
            when (TodoStore.getRemindStyle(this)) {
                TodoStore.REMIND_SHADE -> R.id.rbShade
                TodoStore.REMIND_POPUP -> R.id.rbPopup
                else -> R.id.rbHeadsUp
            }
        )
        b.rgRemind.setOnCheckedChangeListener { _, id ->
            TodoStore.setRemindStyle(
                this,
                when (id) {
                    R.id.rbShade -> TodoStore.REMIND_SHADE
                    R.id.rbPopup -> TodoStore.REMIND_POPUP
                    else -> TodoStore.REMIND_HEADS_UP
                }
            )
        }

        b.rgPalette.check(
            when (ThemeConfig.palette(this)) {
                ThemeConfig.GREEN -> R.id.rbGreen
                ThemeConfig.PURPLE -> R.id.rbPurple
                ThemeConfig.ORANGE -> R.id.rbOrange
                else -> R.id.rbSky
            }
        )
        b.rgPalette.setOnCheckedChangeListener { _, id ->
            val picked = when (id) {
                R.id.rbGreen -> ThemeConfig.GREEN
                R.id.rbPurple -> ThemeConfig.PURPLE
                R.id.rbOrange -> ThemeConfig.ORANGE
                else -> ThemeConfig.SKY
            }
            if (picked != ThemeConfig.palette(this)) {
                ThemeConfig.setPalette(this, picked)
                TodoWidget.refresh(this)
                // 테마는 화면을 만들 때 정해지므로, 지금 보이는 화면은 다시 만들어야
                // 바뀝니다. 다른 화면은 다음에 열릴 때 새 색으로 뜹니다.
                recreate()
            }
        }

        b.btnOverlay.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + packageName)
                    )
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }

        b.btnBattery.setOnClickListener { openBatterySettings() }
        b.btnHideOngoing.setOnClickListener { openServiceChannelSettings() }
        b.btnRemindSound.setOnClickListener { openRemindChannelSettings() }

        b.btnTest.setOnClickListener {
            if (TodoStore.pendingToday(this).isEmpty()) {
                Toast.makeText(this, "오늘 남은 할 일이 없어 팝업이 뜨지 않습니다", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, TodayPopupActivity::class.java))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 시스템 설정에 다녀오면 권한 상태가 바뀌므로 돌아올 때마다 다시 읽습니다.
        syncPermissionStates()
    }

    private fun syncPermissionStates() {
        val overlayOk = Permissions.canShowPopup(this)
        b.tvPermWarn.visibility = if (overlayOk) View.GONE else View.VISIBLE
        b.btnOverlay.text =
            if (overlayOk) "다른 앱 위에 표시 · 허용됨" else "다른 앱 위에 표시 권한 주기"

        // 경고는 아직 허용되지 않았을 때만 띄웁니다. 다 해둔 사람에게는 잔소리가 됩니다.
        val batteryOk = Permissions.isBatteryExempt(this)
        b.tvBatteryWarn.visibility = if (batteryOk) View.GONE else View.VISIBLE
        b.btnBattery.text =
            if (batteryOk) "앱 정보 열기 · 절전 예외 완료" else "앱 정보 열기 (배터리 → 제한 없음)"
    }

    /**
     * 절전 예외를 사용자가 직접 고르도록 앱 정보 화면으로 보냅니다.
     *
     * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 로 허용 팝업을 바로 띄우면 한 번에
     * 끝나지만, 그러려면 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 권한을 선언해야 합니다.
     * 그 권한은 Play 정책상 허용 사례가 좁아 심사 반려 위험이 커서 선언하지 않기로
     * 했습니다(권한 없이 그 인텐트를 던지면 시스템이 거부합니다).
     *
     * 그래서 앱 정보 화면까지만 데려다주고 '배터리 → 제한 없음' 을 사용자가 고릅니다.
     * 삼성 One UI 의 앱별 배터리 설정도 공개 인텐트가 없어 어차피 이 경로가 유일하고,
     * 목록형 화면(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)과 달리 수십 개 앱
     * 사이에서 우리 앱을 찾아 헤맬 필요가 없습니다.
     *
     * 이미 예외 상태여도 화면은 그대로 엽니다. 앱 정보는 '알람 및 리마인더' 등
     * 다른 설정으로 가는 통로이기도 해서, 여기서 막으면 갈 길이 없어집니다.
     */
    private fun openBatterySettings() {
        val exempt = Permissions.isBatteryExempt(this)
        val hint = if (exempt) {
            "절전 예외는 이미 완료 상태입니다"
        } else {
            "'배터리' 로 들어가 '제한 없음' 을 고르세요"
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)
                )
            )
            Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // 앱 정보 화면이 막힌 기기를 위한 대체 경로 (목록에서 직접 찾아야 합니다)
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                Toast.makeText(this, "목록에서 TodoLock 을 찾아 허용하세요", Toast.LENGTH_LONG)
                    .show()
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /**
     * 상시 표시되는 서비스 알림의 채널 설정으로 보냅니다.
     *
     * 포그라운드 서비스 알림은 앱이 지울 수 없습니다(시스템이 표시를 강제합니다).
     * 사용자가 그 채널을 끄면 알림만 사라지고 서비스는 계속 돌아,
     * 잠금해제 감지 기능은 그대로 유지됩니다.
     */
    private fun openServiceChannelSettings() = openChannelSettings(
        Notifications.CHANNEL_SERVICE,
        "이 화면에서 알림을 끄면 상단 표시가 사라집니다",
        "'잠금해제 감지' 항목을 끄세요"
    )

    /**
     * 미리 알림의 소리·진동 설정으로 보냅니다.
     *
     * 소리와 진동은 채널이 결정하고, 채널은 만들어진 뒤 앱이 바꿀 수 없습니다
     * (setSound / setVibrate 는 Android 8+ 에서 무시됩니다).
     * 앱에 무음·진동·소리 선택을 두려면 채널을 그만큼 더 만들어야 하는데,
     * 이미 헤드업/알림창으로 갈라져 있어 조합이 곱해지고 시스템 설정 목록이
     * 어지러워집니다. 게다가 시스템 설정이 항상 앱을 이깁니다.
     * 그래서 기능이 더 많은 시스템 화면으로 안내하는 쪽을 택했습니다.
     */
    private fun openRemindChannelSettings() {
        // 지금 쓰는 방식의 채널을 열어야 사용자가 실제로 받는 알림이 바뀝니다.
        val channel = if (TodoStore.getRemindStyle(this) == TodoStore.REMIND_HEADS_UP) {
            Notifications.CHANNEL_REMIND
        } else {
            Notifications.CHANNEL_REMIND_QUIET
        }
        openChannelSettings(
            channel,
            "이 화면에서 소리·진동을 고를 수 있습니다",
            "'할 일 미리 알림' 항목에서 소리·진동을 고르세요"
        )
    }

    /**
     * 특정 알림 채널 설정 화면으로 보냅니다.
     * 채널 화면이 없는 기기를 위해 앱 알림 설정 → 전체 설정으로 단계적으로 물러납니다.
     */
    private fun openChannelSettings(channelId: String, hint: String, fallbackHint: String) {
        // 채널이 아직 없으면 설정 화면이 비어 보이므로 먼저 만들어 둡니다.
        Notifications.ensureChannels(this)

        // 채널은 Android 8 부터입니다. 그 아래는 앱 알림 설정으로 보냅니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                )
                Toast.makeText(this, hint, Toast.LENGTH_LONG).show()
                return
            } catch (e: Exception) {
                // 기기에 따라 채널 설정 화면이 없을 수 있어 아래로 넘어갑니다
            }
        }
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
            Toast.makeText(this, fallbackHint, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
