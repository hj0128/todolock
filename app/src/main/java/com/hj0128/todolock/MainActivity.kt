package com.hj0128.todolock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.hj0128.todolock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: TodoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 고른 강조색을 입힙니다. setContentView 보다 먼저여야 합니다.
        ThemeConfig.apply(this)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = TodoAdapter(
            mutableListOf(),
            // 완료로 바뀌면 예약을 지우고, 완료를 해제하면 다시 걸어야 하므로
            // 양쪽 다 schedule 을 통과시킵니다 (내부에서 취소를 먼저 합니다).
            onToggle = { TodoStore.update(this, it); Reminders.schedule(this, it); refresh() },
            onDelete = { Reminders.cancel(this, it.id); TodoStore.delete(this, it.id); refresh() },
            onStar = { TodoStore.update(this, it); refresh() },
            onEdit = { openSheet(it) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.btnOpenAdd.setOnClickListener { openSheet() }
        b.btnCalendar.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }
        b.btnSettings.setOnClickListener { openSettings() }
        b.cardWarn.setOnClickListener { openSettings() }

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
        // 위젯이 빈 상태로 남아 있어도 앱을 열면 스스로 복구됩니다.
        TodoWidget.refresh(this)
        refresh()
    }

    /**
     * 추가와 수정은 같은 시트입니다. existing 이 있으면 그 값으로 채워져 열립니다.
     * (목록에서 행 본문을 탭하면 수정)
     */
    private fun openSheet(existing: Todo? = null) {
        AddTodoSheet(this, existing) { todo ->
            TodoStore.upsert(this, todo)
            // 기한·알림이 바뀌었을 수 있으므로 예약을 다시 세웁니다(내부에서 취소 먼저).
            Reminders.announce(this, todo, Reminders.schedule(this, todo))
            refresh()
        }.show()
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

        syncWarning()
    }

    /**
     * 권한이 없어 잠금해제 팝업이 동작하지 못하는 상태를 목록 화면에 알립니다.
     *
     * 설정 화면에도 같은 경고가 있지만, 설정을 열어보지 않은 사람은 팝업이 왜
     * 안 뜨는지 알 수 없습니다. 스위치를 끈 사람에게는 띄우지 않습니다 —
     * 기능을 원하지 않는 것이므로 잔소리가 됩니다.
     */
    private fun syncWarning() {
        val warning = if (TodoStore.isEnabled(this)) Permissions.warning(this) else null
        if (warning == null) {
            b.cardWarn.visibility = View.GONE
        } else {
            b.cardWarn.visibility = View.VISIBLE
            b.tvWarn.text = warning
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
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
