package com.example.todolock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todolock.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
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
            if (checked) UnlockService.start(this) else UnlockService.stop(this)
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

        b.btnBattery.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

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
        refresh()
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
