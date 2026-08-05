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
    private const val KEY_LAST_BCAST_MS = "last_bcast_ms"
    private const val KEY_LAST_BCAST = "last_bcast"
    private const val KEY_EVENT_LOG = "event_log"
    private const val KEY_LAST_HANDLED_MS = "last_handled_ms"
    private const val KEY_REG_MODE = "reg_mode"

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
                        o.optBoolean("done", false),
                        o.optBoolean("important", false)
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
                    .put("important", t.important)
            )
        }
        prefs(ctx).edit().putString(KEY_TODOS, arr.toString()).apply()
    }

    fun forDate(ctx: Context, date: String): List<Todo> =
        load(ctx).filter { it.date == date }

    fun pendingToday(ctx: Context): List<Todo> =
        load(ctx).filter { it.date == today() && !it.done }

    fun add(ctx: Context, text: String, date: String, important: Boolean = false) {
        val list = load(ctx)
        list.add(Todo(System.currentTimeMillis(), text, date, false, important))
        save(ctx, list)
    }

    /** 미완료: 중요 먼저 → 날짜 이른 순. 한 화면에 전부 보여주기 위한 정렬. */
    fun pendingSorted(ctx: Context): List<Todo> =
        load(ctx).filter { !it.done }.sortedWith(
            compareByDescending<Todo> { it.important }.thenBy { it.date }.thenBy { it.id }
        )

    /** 완료: 최근 완료된 것이 위로. */
    fun doneSorted(ctx: Context): List<Todo> =
        load(ctx).filter { it.done }.sortedWith(
            compareByDescending<Todo> { it.date }.thenByDescending { it.id }
        )

    /** "오늘 · 8월 5일 (수)" 처럼 사람이 읽는 날짜. 목록에서 날짜를 행마다 보여주므로 필요합니다. */
    fun prettyDate(dateKey: String): String {
        val cal = Calendar.getInstance()
        val today = format(cal)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = format(cal)
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val yesterday = format(cal)

        val base = try {
            val d = keyFormat().parse(dateKey)
            if (d != null) SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(d) else dateKey
        } catch (e: Exception) {
            dateKey
        }

        return when (dateKey) {
            today -> "오늘 · " + base
            tomorrow -> "내일 · " + base
            yesterday -> "어제 · " + base
            else -> base
        }
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

    /**
     * 리시버가 브로드캐스트를 '받았다'는 사실 자체를 기록합니다.
     * 잠금해제 처리로 이어지지 않은 것(SCREEN_ON 등)까지 남기므로,
     * 이 값이 비어 있으면 리시버 자체가 죽은 것으로 판단할 수 있습니다.
     */
    fun markBroadcast(ctx: Context, action: String) {
        prefs(ctx).edit()
            .putLong(KEY_LAST_BCAST_MS, System.currentTimeMillis())
            .putString(KEY_LAST_BCAST, action)
            .apply()
    }

    fun lastBroadcastMs(ctx: Context): Long = prefs(ctx).getLong(KEY_LAST_BCAST_MS, 0L)

    fun lastBroadcast(ctx: Context): String = prefs(ctx).getString(KEY_LAST_BCAST, "") ?: ""

    /** 최근 이벤트 6건만 유지하는 간단한 로그. 원인 추적용. */
    fun appendLog(ctx: Context, line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val prev = eventLog(ctx)
        val merged = (stamp + " " + line + (if (prev.isEmpty()) "" else "\n" + prev))
            .lines().take(6).joinToString("\n")
        prefs(ctx).edit().putString(KEY_EVENT_LOG, merged).apply()
    }

    fun eventLog(ctx: Context): String = prefs(ctx).getString(KEY_EVENT_LOG, "") ?: ""

    fun clearLog(ctx: Context) = prefs(ctx).edit().remove(KEY_EVENT_LOG).apply()

    /** 어떤 방식으로 리시버 등록이 성공했는지 (플래그 문제 진단용). */
    fun setRegMode(ctx: Context, mode: String) =
        prefs(ctx).edit().putString(KEY_REG_MODE, mode).apply()

    fun regMode(ctx: Context): String = prefs(ctx).getString(KEY_REG_MODE, "") ?: ""

    /** 같은 잠금해제를 두 경로(USER_PRESENT / SCREEN_ON+키가드)에서 중복 처리하지 않도록. */
    fun claimHandling(ctx: Context, withinMs: Long = 4000L): Boolean {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_LAST_HANDLED_MS, 0L) < withinMs) return false
        p.edit().putLong(KEY_LAST_HANDLED_MS, now).apply()
        return true
    }
}
