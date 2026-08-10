package com.hj0128.todolock

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 할 일과 설정을 파일 하나로 내보내고 되돌립니다.
 *
 * 기기를 바꾸면 앱을 새로 깔게 되는데, 그때 지금까지 적어 둔 것이 통째로
 * 사라지는 것을 막기 위한 것입니다.
 *
 * 저장 위치는 사용자가 고르는 대로입니다(문서 고르기 화면). 앱이 서버로 보내는
 * 것은 없고, 권한도 필요하지 않습니다.
 */
object Backup {

    /** 형식이 바뀌면 올립니다. 읽을 때는 모르는 값이 있어도 넘어갑니다. */
    private const val VERSION = 1

    /** 내보낼 때 제안하는 파일 이름 */
    fun suggestedName(): String =
        "todolock-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"

    /**
     * 지금 상태를 글로 만듭니다.
     *
     * 할 일뿐 아니라 고른 설정까지 담습니다 — 기기를 옮기고 나서 팝업 빈도나
     * 색을 처음부터 다시 고르게 하고 싶지 않습니다.
     */
    fun export(ctx: Context): String {
        val settings = JSONObject()
            .put("enabled", TodoStore.isEnabled(ctx))
            .put("popupMode", TodoStore.getMode(ctx))
            .put("remindStyle", TodoStore.getRemindStyle(ctx))
            .put("palette", ThemeConfig.palette(ctx))
            .put("widgetOpacity", WidgetConfig.opacity(ctx))
            .put("widgetTitleSp", WidgetConfig.titleSp(ctx).toDouble())

        return JSONObject()
            .put("version", VERSION)
            .put("exportedAt", TodoStore.prettyDateTime(System.currentTimeMillis()))
            .put("todos", TodoStore.encode(TodoStore.load(ctx)))
            .put("settings", settings)
            .toString(2)
    }

    /** 파일을 읽어 본 결과. 되돌리기 전에 몇 개인지 먼저 보여주기 위한 것입니다. */
    class Parsed(val todos: List<Todo>, val settings: JSONObject?)

    /** 읽을 수 없는 파일이면 null. 남의 json 을 골랐을 때 조용히 덮어쓰지 않기 위해서입니다. */
    fun parse(text: String): Parsed? {
        return try {
            val root = JSONObject(text)
            val todos = root.optJSONArray("todos") ?: return null
            Parsed(TodoStore.decode(todos.toString()), root.optJSONObject("settings"))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 되돌립니다.
     *
     * @param replace 참이면 지금 것을 지우고 파일 것만 남깁니다. 거짓이면 합칩니다 —
     *   같은 항목(id)은 파일 쪽으로 갈아 끼우고, 없던 것만 더합니다.
     *   기기를 옮기는 경우에는 둘의 결과가 같고, 쓰던 기기에서 잘못 눌렀을 때
     *   합치기 쪽이 적어 둔 것을 지우지 않습니다.
     * @return 되돌린 할 일 수
     */
    fun restore(ctx: Context, parsed: Parsed, replace: Boolean): Int {
        val merged = if (replace) {
            parsed.todos.toMutableList()
        } else {
            val byId = LinkedHashMap<Long, Todo>()
            for (t in TodoStore.load(ctx)) byId[t.id] = t
            for (t in parsed.todos) byId[t.id] = t
            byId.values.toMutableList()
        }
        // save 안에서 위젯까지 다시 그려집니다.
        TodoStore.save(ctx, merged)

        parsed.settings?.let { s ->
            if (s.has("enabled")) TodoStore.setEnabled(ctx, s.optBoolean("enabled", true))
            if (s.has("popupMode")) TodoStore.setMode(ctx, s.optInt("popupMode"))
            if (s.has("remindStyle")) TodoStore.setRemindStyle(ctx, s.optInt("remindStyle"))
            if (s.has("palette")) ThemeConfig.setPalette(ctx, s.optInt("palette"))
            if (s.has("widgetOpacity")) WidgetConfig.setOpacity(ctx, s.optInt("widgetOpacity"))
            if (s.has("widgetTitleSp")) {
                WidgetConfig.setTitleSp(ctx, s.optDouble("widgetTitleSp").toFloat())
            }
        }

        // 미리 알림은 알람으로 사는 것이라, 되돌린 것만으로는 울지 않습니다.
        Reminders.rescheduleAll(ctx)
        TodoWidget.refresh(ctx)
        return parsed.todos.size
    }
}
