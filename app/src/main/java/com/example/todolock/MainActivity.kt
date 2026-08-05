package com.example.todolock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todolock.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: TodoAdapter
    private val cal: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = TodoAdapter(
            mutableListOf(),
            onToggle = { TodoStore.update(this, it); refresh() },
            onDelete = { TodoStore.delete(this, it.id); refresh() }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.btnAdd.setOnClickListener { addTodo() }
        b.etInput.setOnEditorActionListener { _, _, _ -> addTodo(); true }

        b.btnPrev.setOnClickListener { cal.add(Calendar.DAY_OF_YEAR, -1); refresh() }
        b.btnNext.setOnClickListener { cal.add(Calendar.DAY_OF_YEAR, 1); refresh() }
        b.tvDate.setOnClickListener {
            cal.timeInMillis = System.currentTimeMillis()
            refresh()
        }

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

        b.btnTest.setOnClickListener {
            if (TodoStore.pendingToday(this).isEmpty()) {
                Toast.makeText(this, "오늘 남은 할 일이 없어 팝업이 뜨지 않습니다", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, TodayPopupActivity::class.java))
            }
        }

        b.btnRestart.setOnClickListener {
            UnlockService.stop(this)
            b.root.postDelayed({
                UnlockService.start(this)
                refresh()
                Toast.makeText(this, "감지 서비스를 다시 시작했습니다", Toast.LENGTH_SHORT).show()
            }, 400L)
        }

        askNotificationPermission()
        if (TodoStore.isEnabled(this)) UnlockService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // 절전으로 서비스가 종료된 경우 앱을 열 때마다 스스로 되살립니다.
        if (TodoStore.isEnabled(this)) {
            if (!UnlockService.isRunning(this)) UnlockService.start(this)
            Watchdog.arm(this)
        }
        refresh()
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

    private fun addTodo() {
        val text = b.etInput.text.toString().trim()
        if (text.isEmpty()) return
        TodoStore.add(this, text, TodoStore.format(cal))
        b.etInput.setText("")
        refresh()
    }

    private fun refresh() {
        val dateKey = TodoStore.format(cal)
        val pretty = SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(cal.time)
        b.tvDate.text = if (dateKey == TodoStore.today()) "오늘 · " + pretty else pretty

        val items = TodoStore.forDate(this, dateKey).sortedBy { it.done }
        adapter.submit(items)
        b.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        val overlayOk = Settings.canDrawOverlays(this)
        b.tvPermWarn.visibility = if (overlayOk) View.GONE else View.VISIBLE
        b.btnOverlay.text = if (overlayOk) "다른 앱 위에 표시 · 허용됨" else "다른 앱 위에 표시 권한 주기"
        b.btnBattery.text = if (isBatteryExempt()) "배터리 예외 · 완료" else "배터리 예외"

        b.tvDiag.text = buildDiagnostics(overlayOk)
    }

    private fun isBatteryExempt(): Boolean = try {
        val pm = getSystemService(PowerManager::class.java)
        pm != null && pm.isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) {
        false
    }

    /**
     * 잠금해제 → 팝업까지의 각 관문 상태를 그대로 보여줍니다.
     * 조용히 실패하는 단계를 눈으로 찾을 수 있어야 해서 넣었습니다.
     */
    private fun buildDiagnostics(overlayOk: Boolean): String {
        val running = UnlockService.isRunning(this)
        val notifOk = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val batteryOk = isBatteryExempt()

        val sb = StringBuilder("─── 진단 ───\n")
        sb.append(mark(TodoStore.isEnabled(this))).append(" 스위치 켜짐\n")
        sb.append(mark(running)).append(" 감지 서비스 실행 중\n")
        sb.append(mark(overlayOk)).append(" 다른 앱 위에 표시\n")
        sb.append(mark(notifOk)).append(" 알림 허용 (대체 표시용)\n")
        sb.append(mark(batteryOk)).append(" 배터리 최적화 예외\n")

        // 리시버가 살아 있었는지 판별하는 핵심 줄.
        // 브로드캐스트가 하나도 없으면 잠금해제 시점에 리시버가 없었다는 뜻입니다.
        val bcast = TodoStore.lastBroadcastMs(this)
        sb.append(mark(bcast > 0L)).append(" 마지막 브로드캐스트: ")
            .append(if (bcast > 0L) TodoStore.lastBroadcast(this) + " " + stamp(bcast) else "아직 없음")
            .append('\n')

        val unlock = TodoStore.lastUnlockMs(this)
        sb.append(mark(unlock > 0L)).append(" 마지막 잠금해제 감지: ")
            .append(if (unlock > 0L) stamp(unlock) else "아직 없음").append('\n')
        if (TodoStore.lastResult(this).isNotEmpty()) {
            sb.append("    └ 처리: ").append(TodoStore.lastResult(this)).append('\n')
        }

        val started = TodoStore.serviceStartedMs(this)
        if (started > 0L) sb.append("서비스 시작: ").append(stamp(started)).append('\n')
        val stopped = TodoStore.serviceStoppedMs(this)
        if (stopped > 0L) sb.append("서비스 종료: ").append(stamp(stopped)).append('\n')

        val err = TodoStore.serviceError(this)
        if (err.isNotEmpty()) sb.append("⚠ 오류: ").append(err).append('\n')

        sb.append("Android ").append(Build.VERSION.SDK_INT)
            .append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)

        val log = TodoStore.eventLog(this)
        if (log.isNotEmpty()) sb.append("\n─── 최근 기록 ───\n").append(log)

        if (!running) {
            sb.append("\n\n서비스가 죽어 있습니다 → 배터리 예외를 켜고 '감지 서비스 다시 시작'을 누르세요.")
        } else if (bcast == 0L) {
            sb.append("\n\n서비스는 살아 있는데 브로드캐스트가 하나도 없습니다. ")
                .append("화면이 꺼진 동안 앱이 재워진 것이므로 배터리 예외 설정이 필수입니다. ")
                .append("워치독이 15분마다 서비스를 되살립니다.")
        }
        return sb.toString()
    }

    private fun mark(ok: Boolean) = if (ok) "✔" else "✘"

    private fun stamp(ms: Long): String =
        SimpleDateFormat("M/d HH:mm:ss", Locale.KOREA).format(Date(ms))

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
