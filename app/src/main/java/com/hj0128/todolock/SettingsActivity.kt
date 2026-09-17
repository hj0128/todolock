package com.hj0128.todolock

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hj0128.todolock.databinding.ActivitySettingsBinding

/**
 * 설정 화면.
 *
 * 목록 화면 아래에 카드로 붙어 있던 것을 분리했습니다.
 * 항목이 늘면서 카드가 길어져 할 일 목록이 눌리는 문제가 있었습니다.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    /**
     * 백업 파일을 만들 자리를 고르게 합니다.
     *
     * 문서 고르기 화면을 쓰므로 저장소 권한이 필요 없고, 드라이브처럼 기기 밖에
     * 두는 곳도 그대로 고를 수 있습니다 — 기기를 바꿀 때 옮겨야 하는 파일이라
     * 그편이 낫습니다.
     */
    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            if (it != null) writeBackup(it)
        }

    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            if (it != null) readBackup(it)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 고른 강조색을 입힙니다. setContentView 보다 먼저여야 합니다.
        ThemeConfig.apply(this)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.root.applySystemBarInsets()

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
            syncRepeatVisible()
            // 방식이 바뀌면 쓰는 채널도 바뀝니다. 여기서 다시 만들어야
            // 시스템 알림 설정에 쓰지 않는 채널이 남아 있지 않습니다.
            Notifications.ensureChannels(this)
        }

        // 울리는 길이는 '전체 팝업' 일 때만 고를 수 있습니다. 다른 방식에서는
        // 소리를 멈출 버튼이 화면에 보이지 않아 선택 자체가 성립하지 않습니다.
        b.rgRepeat.check(
            if (TodoStore.getRemindRepeat(this)) R.id.rbUntilChecked else R.id.rbRingOnce
        )
        b.rgRepeat.setOnCheckedChangeListener { _, id ->
            TodoStore.setRemindRepeat(this, id == R.id.rbUntilChecked)
        }
        syncRepeatVisible()

        buildPalette()

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

        // 오늘 남은 할 일이 없어도 예시를 채워 띄웁니다. 색을 바꾼 뒤 어떻게
        // 보이는지 확인하려는 것인데, 할 일이 없다고 아무것도 안 보여주면
        // 정작 궁금할 때 볼 수가 없습니다.
        b.btnTest.setOnClickListener {
            startActivity(
                Intent(this, TodayPopupActivity::class.java)
                    .putExtra(TodayPopupActivity.EXTRA_PREVIEW, true)
            )
        }

        b.btnExport.setOnClickListener { createBackup.launch(Backup.suggestedName()) }
        // json 만 걸러 두면 파일 관리자에 따라 백업 파일이 흐리게 보이는 일이
        // 있어, 아무 파일이나 고를 수 있게 두고 읽을 때 판별합니다.
        b.btnImport.setOnClickListener { openBackup.launch(arrayOf("*/*")) }
    }

    private fun writeBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(Backup.export(this).toByteArray())
            } ?: throw IllegalStateException("cannot open " + uri)
            Toast.makeText(this, R.string.backup_exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.backup_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 되돌리기 전에 몇 개인지 보여주고 물어봅니다.
     *
     * 합치기를 앞에 둔 이유: 기기를 옮기는 경우에는 두 선택의 결과가 같고,
     * 쓰던 기기에서 잘못 눌렀을 때 합치기 쪽이 적어 둔 것을 지우지 않습니다.
     */
    private fun readBackup(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        } catch (e: Exception) {
            null
        }
        val parsed = text?.let { Backup.parse(it) }
        if (parsed == null) {
            Toast.makeText(this, R.string.backup_not_a_backup, Toast.LENGTH_LONG).show()
            return
        }

        val count = resources.getQuantityString(
            R.plurals.task_count, parsed.todos.size, parsed.todos.size
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_import_title)
            .setMessage(getString(R.string.backup_import_msg, count))
            .setPositiveButton(R.string.backup_merge) { _, _ ->
                applyBackup(parsed, replace = false)
            }
            .setNegativeButton(R.string.backup_replace) { _, _ ->
                applyBackup(parsed, replace = true)
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun applyBackup(parsed: Backup.Parsed, replace: Boolean) {
        val count = Backup.restore(this, parsed, replace)
        Toast.makeText(
            this,
            resources.getQuantityString(R.plurals.backup_imported, count, count),
            Toast.LENGTH_SHORT
        ).show()

        // 색이나 팝업 설정이 함께 바뀌었을 수 있어 화면을 다시 엽니다.
        //
        // recreate() 가 아닌 이유: 그쪽은 화면에 떠 있던 선택 상태(라디오 · 스위치)까지
        // 되살리는데, 그 되살아난 값이 리스너를 타고 방금 되돌린 설정 위에 덮여
        // 씌어집니다. 실제로 가져온 '알림 방식' 이 가져오기 직전 값으로 되돌아갔습니다.
        // 새 화면으로 열면 저장된 값만 읽으므로 그런 일이 없습니다.
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        // 시스템 설정에 다녀오면 권한 상태가 바뀌므로 돌아올 때마다 다시 읽습니다.
        syncPermissionStates()
    }

    /**
     * 색상표를 채웁니다. 동그라미 하나가 팔레트 하나이고, 고른 것에만 체크가 보입니다.
     * 색이 16개라 XML 로 적으면 같은 덩어리가 열여섯 번 반복되므로 코드로 만듭니다.
     */
    private fun buildPalette() {
        val selected = ThemeConfig.palette(this)
        val names = ThemeConfig.names(this)
        // 한 줄에 8개가 들어가야 합니다. 36+2+2 = 40dp × 8 = 320dp 로 카드 안에 맞습니다.
        val dp = resources.displayMetrics.density
        val size = (36 * dp).toInt()
        val gap = (2 * dp).toInt()
        val pad = (8 * dp).toInt()

        b.gridPalette.removeAllViews()
        for (i in 0 until ThemeConfig.COUNT) {
            val dot = ImageView(this)
            dot.layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                setMargins(gap, gap, gap, gap)
            }
            dot.background = ContextCompat.getDrawable(this, R.drawable.swatch)
            dot.backgroundTintList =
                ColorStateList.valueOf(ThemeConfig.headingColor(this, i))
            dot.setImageResource(R.drawable.ic_check)
            dot.setColorFilter(ThemeConfig.onColor(this, i))
            dot.setPadding(pad, pad, pad, pad)
            // 고른 색에만 체크를 보입니다. 자리는 그대로 둬서 크기가 흔들리지 않습니다.
            dot.imageAlpha = if (i == selected) 255 else 0
            // 이름은 화면에 적지 않습니다 — 색을 보고 고르는 것이라 글자가 거들 게
            // 없습니다. 다만 화면 낭독기에는 필요해서 contentDescription 으로 남깁니다.
            dot.contentDescription = names[i]
            dot.setOnClickListener { pickPalette(i) }
            b.gridPalette.addView(dot)
        }
    }

    private fun pickPalette(palette: Int) {
        if (palette == ThemeConfig.palette(this)) return
        ThemeConfig.setPalette(this, palette)
        TodoWidget.refresh(this)
        // 테마는 화면을 만들 때 정해지므로 지금 보이는 화면은 다시 만들어야 바뀝니다.
        // 다른 화면은 다음에 열릴 때 새 색으로 뜹니다.
        recreate()
    }

    private fun syncRepeatVisible() {
        b.boxRepeat.visibility =
            if (TodoStore.getRemindStyle(this) == TodoStore.REMIND_POPUP) View.VISIBLE
            else View.GONE
    }

    private fun syncPermissionStates() {
        val overlayOk = Permissions.canShowPopup(this)
        b.tvPermWarn.visibility = if (overlayOk) View.GONE else View.VISIBLE
        b.btnOverlay.setText(
            if (overlayOk) R.string.perm_overlay_ok else R.string.perm_overlay_grant
        )

        // 경고는 아직 허용되지 않았을 때만 띄웁니다. 다 해둔 사람에게는 잔소리가 됩니다.
        val batteryOk = Permissions.isBatteryExempt(this)
        b.tvBatteryWarn.visibility = if (batteryOk) View.GONE else View.VISIBLE
        b.btnBattery.setText(
            if (batteryOk) R.string.perm_battery_ok else R.string.perm_battery_open
        )
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
        val hint = getString(
            if (exempt) R.string.toast_battery_done else R.string.toast_battery_how
        )
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
                Toast.makeText(this, R.string.toast_overlay_how, Toast.LENGTH_LONG).show()
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
        getString(R.string.toast_ongoing_how),
        getString(R.string.toast_ongoing_which)
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
            getString(R.string.toast_sound_how),
            getString(R.string.toast_sound_which)
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
