package com.example.todolock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * SharedPreferences + JSON 기반 초경량 저장소.
 * Room / 애노테이션 프로세서 없이 동작하므로 빌드가 단순합니다.
 */
object TodoStore {

    private const val PREF = "todolock_prefs"
    private const val KEY_TODOS = "todos"
    private const val KEY_MODE = "popup_mode"            // 0=매번, 1=1시간 간격, 2=하루 1번
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_SHOWN_MS = "last_shown_ms"
    private const val KEY_LAST_SHOWN_DAY = "last_shown_day"

    // ---------- 진단용 (어느 단계에서 막혔는지 앱에서 바로 보이게) ----------
    private const val KEY_SVC_STARTED_MS = "svc_started_ms"
    private const val KEY_SVC_STOPPED_MS = "svc_stopped_ms"
    private const val KEY_SVC_ERROR = "svc_error"
    private const val KEY_LAST_UNLOCK_MS = "last_unlock_ms"
    private const val KEY_LAST_RESULT = "last_result"

    const val MODE_ALWAYS = 0
    const val MODE_HOURLY = 1
    const val MODE_ONCE_A_DAY = 2

    /** shouldShowNow 가 false 를 반환한 '이유'. 진단 표시에 씁니다. */
    const val DECIDE_SHOW = 0
    const val DECIDE_DISABLED = 1
    const val DECIDE_NO_TODOS = 2
    const val DECIDE_FREQUENCY = 3

    private fun keyFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = keyFormat().format(Date())

    fun format(cal: Calendar): String = keyFormat().format(cal.time)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- CRUD ----------

    fun load(ctx: Context): MutableList<Todo> {
        val raw = prefs(ctx).getString(KEY_TODOS, "[]") ?: "[]"
        val out = mutableListOf<Todo>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Todo(
                        o.optLong("id", System.nanoTime()),
                        o.optString("text", ""),
                        o.optString("date", today()),
                        o.optBoolean("done", false)
                    )
                )
            }
        } catch (e: Exception) {
            // 데이터가 깨진 경우 빈 목록으로 시작
        }
        return out
    }

    fun save(ctx: Context, list: List<Todo>) {
        val arr = JSONArray()
        for (t in list) {
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("text", t.text)
                    .put("date", t.date)
                    .put("done", t.done)
            )
        }
        prefs(ctx).edit().putString(KEY_TODOS, arr.toString()).apply()
    }

    fun forDate(ctx: Context, date: String): List<Todo> =
        load(ctx).filter { it.date == date }

    fun pendingToday(ctx: Context): List<Todo> =
        load(ctx).filter { it.date == today() && !it.done }

    fun add(ctx: Context, text: String, date: String) {
        val list = load(ctx)
        list.add(Todo(System.currentTimeMillis(), text, date, false))
        save(ctx, list)
    }

    fun update(ctx: Context, todo: Todo) {
        val list = load(ctx)
        val idx = list.indexOfFirst { it.id == todo.id }
        if (idx >= 0) {
            list[idx] = todo
            save(ctx, list)
        }
    }

    fun delete(ctx: Context, id: Long) {
        val list = load(ctx)
        list.removeAll { it.id == id }
        save(ctx, list)
    }

    // ---------- 설정 ----------

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    fun getMode(ctx: Context): Int = prefs(ctx).getInt(KEY_MODE, MODE_ALWAYS)

    fun setMode(ctx: Context, m: Int) = prefs(ctx).edit().putInt(KEY_MODE, m).apply()

    /**
     * 팝업을 띄울지, 아니면 왜 안 띄우는지를 판단합니다.
     * shouldShowNow 와 달리 '이유'를 돌려주므로 진단 화면에 그대로 쓸 수 있습니다.
     */
    fun decide(ctx: Context): Int {
        if (!isEnabled(ctx)) return DECIDE_DISABLED
        if (pendingToday(ctx).isEmpty()) return DECIDE_NO_TODOS

        val p = prefs(ctx)
        val ok = when (getMode(ctx)) {
            MODE_HOURLY -> {
                val last = p.getLong(KEY_LAST_SHOWN_MS, 0L)
                System.currentTimeMillis() - last >= 60L * 60L * 1000L
            }
            MODE_ONCE_A_DAY -> p.getString(KEY_LAST_SHOWN_DAY, "") != today()
            else -> true
        }
        return if (ok) DECIDE_SHOW else DECIDE_FREQUENCY
    }

    /** 잠금해제 시점에 팝업을 띄워야 하는지 판단. 오늘 남은 할 일이 없으면 항상 false. */
    fun shouldShowNow(ctx: Context): Boolean = decide(ctx) == DECIDE_SHOW

    fun markShown(ctx: Context) {
        prefs(ctx).edit()
            .putLong(KEY_LAST_SHOWN_MS, System.currentTimeMillis())
            .putString(KEY_LAST_SHOWN_DAY, today())
            .apply()
    }

    // ---------- 진단 기록 ----------

    fun markServiceStarted(ctx: Context) =
        prefs(ctx).edit().putLong(KEY_SVC_STARTED_MS, System.currentTimeMillis()).apply()

    fun markServiceStopped(ctx: Context) =
        prefs(ctx).edit().putLong(KEY_SVC_STOPPED_MS, System.currentTimeMillis()).apply()

    fun serviceStartedMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SVC_STARTED_MS, 0L)

    fun serviceStoppedMs(ctx: Context): Long = prefs(ctx).getLong(KEY_SVC_STOPPED_MS, 0L)

    fun setServiceError(ctx: Context, msg: String?) =
        prefs(ctx).edit().putString(KEY_SVC_ERROR, msg ?: "").apply()

    fun serviceError(ctx: Context): String = prefs(ctx).getString(KEY_SVC_ERROR, "") ?: ""

    /** 잠금해제 브로드캐스트를 실제로 받은 시각 + 그때 어떻게 처리했는지. */
    fun markUnlock(ctx: Context, result: String) {
        prefs(ctx).edit()
            .putLong(KEY_LAST_UNLOCK_MS, System.currentTimeMillis())
            .putString(KEY_LAST_RESULT, result)
            .apply()
    }

    fun lastUnlockMs(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_UNLOCK_MS, 0L)

    fun lastResult(ctx: Context): String = prefs(ctx).getString(KEY_LAST_RESULT, "") ?: ""
}
