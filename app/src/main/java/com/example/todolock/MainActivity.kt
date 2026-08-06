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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todolock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: TodoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = TodoAdapter(
            mutableListOf(),
            // 완료로 바뀌면 예약을 지우고, 완료를 해제하면 다시 걸어야 하므로
            // 양쪽 다 schedule 을 통과시킵니다 (내부에서 취소를 먼저 합니다).
            onToggle = { TodoStore.update(this, it); Reminders.schedule(this, it); refresh() },
            onDelete = { Reminders.cancel(this, it.id); TodoStore.delete(this, it.id); refresh() },
            onStar = { TodoStore.update(this, it); refresh() },
            onEdit = { openEditSheet(it) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.btnOpenAdd.setOnClickListener { openAddSheet() }

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
        // 재부팅·강제 종료로 알람이 날아갔을 수 있으므로 앱을 열 때마다 다시 세웁니다.
        Reminders.rescheduleAll(this)
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

    private fun openAddSheet() {
        AddTodoSheet(this) { text, date, remindAt, important ->
            val todo = TodoStore.add(this, text, date, important, remindAt)
            announceReminder(todo, Reminders.schedule(this, todo))
            refresh()
        }.show()
    }

    /** 목록에서 행 본문을 탭했을 때. 같은 시트를 기존 값으로 채워 엽니다. */
    private fun openEditSheet(todo: Todo) {
        AddTodoSheet(this, todo) { text, date, remindAt, important ->
            todo.text = text
            todo.date = date
            todo.important = important
            // 알림 시각이 바뀌었으면 '이미 알렸음' 표시를 지워야 새 시각에 알립니다.
            if (todo.remindAt != remindAt) {
                todo.remindAt = remindAt
                todo.notified = false
            }
            TodoStore.update(this, todo)
            // 기한·알림이 바뀌었을 수 있으므로 예약을 다시 세웁니다(내부에서 취소 먼저).
            announceReminder(todo, Reminders.schedule(this, todo))
            refresh()
        }.show()
    }

    /** 실제로 알람이 걸렸을 때만 알려준다고 말합니다. 지난 시각은 예약되지 않습니다. */
    private fun announceReminder(todo: Todo, scheduled: Boolean) {
        if (!todo.hasReminder) return

        val at = TodoStore.prettyDateTime(todo.remindAt)
        val msg = if (!scheduled) {
            at + " — 이미 지난 시각이라 알림을 걸지 않았습니다"
        } else {
            // 정확 알람 권한이 없으면 몇 분 늦으므로 그 사실을 같이 알려줍니다.
            at + "에 알려드립니다" +
                (if (Reminders.canBeExact(this)) "" else " (권한이 없어 몇 분 늦을 수 있음)")
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun refresh() {
        // 날짜별로 갈아타지 않고 전부 한 목록에 보여주고, 완료는 아래 섹션으로 내립니다.
        val pending = TodoStore.pendingSorted(this)
        val done = TodoStore.doneSorted(this)

        val rows = mutableListOf<Row>()
        if (pending.isNotEmpty()) {
            rows.add(Row.Header("할 일 " + pending.size + "개"))
            pending.forEach { rows.add(Row.Item(it)) }
        }
        if (done.isNotEmpty()) {
            rows.add(Row.Header("완료 " + done.size + "개"))
            done.forEach { rows.add(Row.Item(it)) }
        }

        adapter.submit(rows)
        b.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        val overlayOk = Settings.canDrawOverlays(this)
        b.tvPermWarn.visibility = if (overlayOk) View.GONE else View.VISIBLE
        b.btnOverlay.text = if (overlayOk) "다른 앱 위에 표시 · 허용됨" else "다른 앱 위에 표시 권한 주기"
        b.btnBattery.text = if (isBatteryExempt()) "배터리 예외 · 완료" else "배터리 예외"
    }

    private fun isBatteryExempt(): Boolean = try {
        val pm = getSystemService(PowerManager::class.java)
        pm != null && pm.isIgnoringBatteryOptimizations(packageName)
    } catch (e: Exception) {
        false
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
