package com.hj0128.todolock

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
    private const val KEY_REMIND_STYLE = "remind_style"
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

    /**
     * 미리 알림을 어떤 세기로 알릴지.
     * 헤드업 알림은 항상 알림창에도 남으므로 '팝업만' 같은 선택지는 없습니다.
     */
    const val REMIND_SHADE = 0      // 알림창에만 (헤드업 없음)
    const val REMIND_HEADS_UP = 1   // 화면 위에 잠깐 + 알림창
    const val REMIND_POPUP = 2      // 화면 전체 팝업 + 알림창

    /** shouldShowNow 가 false 를 반환한 '이유'. 진단 표시에 씁니다. */
    const val DECIDE_SHOW = 0
    const val DECIDE_DISABLED = 1
    const val DECIDE_NO_TODOS = 2
    const val DECIDE_FREQUENCY = 3

    private fun keyFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = keyFormat().format(Date())

    fun format(cal: Calendar): String = keyFormat().format(cal.time)

    /** "yyyy-MM-dd" → Calendar. 값이 깨져 있으면 오늘로 둡니다. (수정 시트 초기값용) */
    fun parseDate(dateKey: String): Calendar {
        val cal = Calendar.getInstance()
        try {
            val d = keyFormat().parse(dateKey)
            if (d != null) cal.time = d
        } catch (e: Exception) {
            // 오늘 기준 유지
        }
        return cal
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- CRUD ----------

    /**
     * 목록 전체를 읽습니다.
     *
     * 쓰기는 모두 '읽고 → 고치고 → 통째로 저장' 이라, 쓰는 쪽(update·upsert·delete)에
     * @Synchronized 를 걸어 한 항목의 저장이 끝나기 전에 다른 저장이 끼어들지 못하게
     * 합니다. 알림의 '완료' 버튼(ReminderReceiver)·위젯 토글(TodoWidget)·화면이
     * 서로 다른 진입점이라 동시에 들어올 수 있습니다.
     */
    @Synchronized
    fun load(ctx: Context): MutableList<Todo> {
        val raw = prefs(ctx).getString(KEY_TODOS, "[]") ?: "[]"
        val out = mutableListOf<Todo>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Todo(
                        id = o.optLong("id", System.nanoTime()),
                        text = o.optString("text", ""),
                        date = o.optString("date", today()),
                        // 시각이 없던 옛 데이터는 '날짜만' 으로 읽힙니다.
                        dueMinutes = o.optInt("dueMinutes", Todo.NO_TIME),
                        memo = o.optString("memo", ""),
                        done = o.optBoolean("done", false),
                        important = o.optBoolean("important", false),
                        // 알림이 없던 옛 데이터는 '알림 없음' 으로 읽힙니다.
                        remindAt = o.optLong("remindAt", Todo.NO_REMIND),
                        notified = o.optBoolean("notified", false)
                    )
                )
            }
        } catch (e: Exception) {
            // 데이터가 깨진 경우 빈 목록으로 시작
        }
        return out
    }

    @Synchronized
    fun save(ctx: Context, list: List<Todo>) {
        val arr = JSONArray()
        for (t in list) {
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("text", t.text)
                    .put("date", t.date)
                    .put("dueMinutes", t.dueMinutes)
                    .put("memo", t.memo)
                    .put("done", t.done)
                    .put("important", t.important)
                    .put("remindAt", t.remindAt)
                    .put("notified", t.notified)
            )
        }
        prefs(ctx).edit().putString(KEY_TODOS, arr.toString()).apply()

        // 저장은 데이터가 바뀌는 유일한 지점이라, 홈 화면 위젯 갱신을 여기 한 곳에 둡니다.
        TodoWidget.refresh(ctx)
    }

    fun forDate(ctx: Context, date: String): List<Todo> =
        load(ctx).filter { it.date == date }

    fun pendingToday(ctx: Context): List<Todo> =
        load(ctx).filter { it.date == today() && !it.done }

    /**
     * 있으면 갈아 끼우고 없으면 새로 넣습니다. 추가·수정 시트가 쓰는 저장 경로입니다.
     *
     * 필드별 인자를 받지 않는 이유: 할 일에 값이 하나 늘 때마다 이 함수와 호출부가
     * 전부 따라 바뀝니다. 통째로 받으면 늘어나는 값은 Todo 안에서만 삽니다.
     */
    @Synchronized
    fun upsert(ctx: Context, todo: Todo) {
        val list = load(ctx)
        val idx = list.indexOfFirst { it.id == todo.id }
        if (idx >= 0) list[idx] = todo else list.add(todo)
        save(ctx, list)
    }

    /**
     * 미완료 정렬 우선순위:
     * 날짜(오래된 것 먼저) → 중요 → 기한 시각(이른 것 먼저) → 미리 알림 → 등록순.
     *
     * 날짜는 여전히 최우선이라 중요 표시로도 날짜 경계를 넘지 못합니다.
     * 시각·알림을 정하지 않은 항목은 값이 각각 -1 과 0 이라 그대로 두면 맨 앞으로
     * 오므로, 정렬 키에서만 가장 큰 값으로 바꿔 뒤로 보냅니다.
     */
    fun pendingSorted(ctx: Context): List<Todo> =
        load(ctx).filter { !it.done }.sortedWith(pendingOrder)

    /** 목록·위젯·잠금해제 팝업이 같은 순서를 쓰도록 비교자를 한곳에 둡니다. */
    val pendingOrder: Comparator<Todo> =
        compareBy<Todo> { it.date }
            .thenByDescending { it.important }
            .thenBy { if (it.hasDueTime) it.dueMinutes else Int.MAX_VALUE }
            .thenBy { if (it.hasReminder) it.remindAt else Long.MAX_VALUE }
            .thenBy { it.id }

    // ---------- 기한 / 미리 알림 ----------

    /**
     * 기한이 이미 지났는지.
     *
     * 시각을 정한 항목은 당일에도 그 시각이 지나면 '지남' 입니다 — 시각을 정하는
     * 이유가 그것이기 때문입니다. 시각이 없으면 지금까지처럼 날짜로만 판단하므로,
     * 날짜만 쓰던 사람에게는 아무것도 달라지지 않습니다.
     */
    fun isOverdue(t: Todo): Boolean {
        if (t.done) return false
        val today = today()
        if (t.date != today) return t.date < today
        return t.hasDueTime && t.dueMinutes < nowMinutes()
    }

    /**
     * 기한이 오늘이고 아직 남아 있는지. 목록·위젯에서 강조 표시할지 판단합니다.
     * 시각이 지나 '지남' 이 된 항목은 제외합니다 — 그쪽은 빨간색이 이깁니다.
     */
    fun isDueToday(t: Todo): Boolean = !t.done && t.date == today() && !isOverdue(t)

    /** 자정부터 지금까지의 분. 기한 시각과 같은 단위로 비교하기 위해. */
    private fun nowMinutes(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** 기한 시각을 "14:00" 으로. */
    fun formatMinutes(minutes: Int): String =
        String.format(Locale.KOREA, "%02d:%02d", minutes / 60, minutes % 60)

    /**
     * 목록·팝업·위젯에 한 줄로 넣을 메모 미리보기.
     * 여러 줄이면 내용이 있는 첫 줄만 씁니다 — 빈 줄로 시작하는 메모도 흔합니다.
     */
    fun memoLine(t: Todo): String =
        t.memo.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""

    /**
     * 목록·위젯·알림에 쓰는 기한 표기.
     * 시각을 정하지 않았으면 지금까지와 똑같이 날짜만 나옵니다.
     */
    fun prettyDue(t: Todo): String =
        if (t.hasDueTime) prettyDate(t.date) + " " + formatMinutes(t.dueMinutes)
        else prettyDate(t.date)

    /**
     * 미리 알림 표기. 목록·위젯·팝업·시트·토스트가 모두 이 한 가지 형식을 씁니다.
     *
     * 언제나 "8월 6일 (목) 09:00" 처럼 날짜와 시각을 함께 씁니다. 기한과 같은 날이면
     * 시각만 보여주던 때가 있었는데, 그러면 줄마다 형식이 달라져 훑어볼 때 오히려
     * 읽는 품이 듭니다.
     *
     * 날짜에는 '오늘/내일/어제' 를 붙이지 않습니다 — 같은 줄에서 기한이 이미 그 말을
     * 쓰고 있어, 양쪽에 나오면 어느 쪽 이야기인지 헷갈립니다.
     */
    fun prettyDateTime(ms: Long): String {
        if (ms <= 0L) return ""
        val cal = Calendar.getInstance()
        cal.timeInMillis = ms
        return plainDate(format(cal)) + " " +
            SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(ms))
    }

    /** 완료: 최근 완료된 것이 위로. */
    fun doneSorted(ctx: Context): List<Todo> =
        load(ctx).filter { it.done }.sortedWith(
            compareByDescending<Todo> { it.date }.thenByDescending { it.id }
        )

    /** "8월 5일 (수)". 오늘·내일 같은 말을 붙이지 않은 날짜입니다. */
    fun plainDate(dateKey: String): String = try {
        val d = keyFormat().parse(dateKey)
        if (d != null) SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(d) else dateKey
    } catch (e: Exception) {
        dateKey
    }

    /**
     * "오늘 · 8월 5일 (수)" 처럼 사람이 읽는 날짜. 목록에서 날짜를 행마다 보여주므로 필요합니다.
     * 기한 쪽에만 씁니다 — 알림 쪽은 plainDate 입니다.
     */
    fun prettyDate(dateKey: String): String {
        val cal = Calendar.getInstance()
        val today = format(cal)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = format(cal)
        cal.add(Calendar.DAY_OF_YEAR, -2)
        val yesterday = format(cal)

        val base = plainDate(dateKey)
        return when (dateKey) {
            today -> "오늘 · " + base
            tomorrow -> "내일 · " + base
            yesterday -> "어제 · " + base
            else -> base
        }
    }

    /**
     * 이미 있는 항목만 갈아 끼웁니다. upsert 와 달리 없으면 아무것도 하지 않습니다 —
     * 알림·위젯처럼 예전에 읽어둔 항목을 나중에 저장하는 경로에서, 그 사이에 지워진
     * 할 일이 되살아나는 것을 막습니다.
     */
    @Synchronized
    fun update(ctx: Context, todo: Todo) {
        val list = load(ctx)
        val idx = list.indexOfFirst { it.id == todo.id }
        if (idx >= 0) {
            list[idx] = todo
            save(ctx, list)
        }
    }

    @Synchronized
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

    fun getRemindStyle(ctx: Context): Int =
        prefs(ctx).getInt(KEY_REMIND_STYLE, REMIND_HEADS_UP)

    fun setRemindStyle(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_REMIND_STYLE, v).apply()

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
                // 음수(저장된 시각이 미래)면 시계가 뒤로 간 것이므로 막지 않습니다.
                val elapsed = System.currentTimeMillis() - p.getLong(KEY_LAST_SHOWN_MS, 0L)
                elapsed < 0L || elapsed >= 60L * 60L * 1000L
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

    /**
     * 같은 잠금해제를 두 경로(USER_PRESENT / SCREEN_ON+키가드)에서 중복 처리하지 않도록.
     *
     * 경과 시간이 음수인 경우(= 저장된 시각이 미래)는 '최근'으로 보지 않습니다.
     * 시계가 뒤로 가면(시간대 변경·수동 조정·NTP 보정) 그 시각이 미래가 되는데,
     * 이를 최근으로 판정하면 잠금해제 처리가 영구히 막힙니다.
     */
    fun claimHandling(ctx: Context, withinMs: Long = 4000L): Boolean {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val elapsed = now - p.getLong(KEY_LAST_HANDLED_MS, 0L)
        if (elapsed in 0 until withinMs) return false
        p.edit().putLong(KEY_LAST_HANDLED_MS, now).apply()
        return true
    }
}
