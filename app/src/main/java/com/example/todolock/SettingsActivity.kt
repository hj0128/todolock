package com.example.todolock

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.todolock.databinding.ActivitySettingsBinding

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

        b.btnBattery.setOnClickListener { requestBatteryExemption() }
        b.btnAppInfo.setOnClickListener { openAppInfo() }
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
        val overlayOk = Settings.canDrawOverlays(this)
        b.tvPermWarn.visibility = if (overlayOk) View.GONE else View.VISIBLE
        b.btnOverlay.text =
            if (overlayOk) "다른 앱 위에 표시 · 허용됨" else "다른 앱 위에 표시 권한 주기"
        b.btnBattery.text = if (isBatteryExempt()) "배터리 예외 · 완료" else "배터리 예외"
    }

    private fun isBatteryExempt(): Boolean = try {
        val pm = getSystemService(PowerManager::class.java)
        pm != null && pm.isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) {
        false
    }

    /**
     * 배터리 최적화 예외.
     * 시스템 앱 목록으로 보내면 사용자가 앱을 찾아 헤매게 되므로
     * 우리 패키지를 지정해 '허용' 팝업을 바로 띄웁니다. 막히면 목록으로 대체합니다.
     */
    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "이미 배터리 최적화 예외 상태입니다", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + packageName)
                )
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    /**
     * 앱 정보 화면으로 보냅니다.
     *
     * 삼성 One UI 의 앱별 배터리 설정('제한 없음')과 '사용하지 않는 앱 절전' 목록은
     * 공개 인텐트가 없어 앱에서 직접 열 수 없습니다. 내부 컴포넌트를 지정해 여는
     * 방법은 One UI 버전마다 달라지고 막히기도 해서 쓰지 않습니다.
     * 앱 정보까지만 보내면 거기서 '배터리' 를 한 번 더 누르면 됩니다.
     */
    private fun openAppInfo() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName)
                )
            )
            Toast.makeText(this, "'배터리' 로 들어가 '제한 없음' 을 고르세요", Toast.LENGTH_LONG)
                .show()
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
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
