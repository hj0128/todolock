package com.hj0128.todolock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * 홈 화면 위젯.
 *
 * 목록은 TodoWidgetService 가 공급하고, 항목을 탭하면 앱을 열지 않고 바로 완료 처리됩니다.
 * 데이터가 바뀔 때마다 TodoStore.save 가 refresh 를 불러 주므로 별도 갱신 호출이 필요 없습니다.
 */
class TodoWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, build(context, id))
        }
        // 틀만 다시 그리면 목록 행은 예전 글자를 그대로 답니다. 행에는 '내일'
        // 같은 오늘 기준 표기가 들어 있어, 날이 바뀌어도 어제 만든 글이 남습니다.
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.wList)

        // 위젯을 막 놓은 참이면 아직 알람이 없습니다.
        DayRollover.arm(context.applicationContext)
    }

    /** 마지막 위젯이 홈에서 사라지면 깨울 이유도 없습니다. */
    override fun onDisabled(context: Context) {
        DayRollover.cancel(context.applicationContext)
        super.onDisabled(context)
    }

    /** 위젯이 홈에서 지워지면 그 위젯의 모드 기억도 함께 지웁니다. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetConfig.forget(context, id)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MODE) {
            val ctx = context.applicationContext
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetConfig.setCalendar(ctx, id, !WidgetConfig.isCalendar(ctx, id))
                // 달력을 열 때는 늘 이번 달부터. 지난번에 넘겨 둔 자리에서
                // 다시 열리면 오늘이 어디인지부터 찾아야 합니다.
                WidgetConfig.setMonthOffset(ctx, id, 0)
                AppWidgetManager.getInstance(ctx).updateAppWidget(id, build(ctx, id))
            }
            return
        }

        if (intent.action == ACTION_MONTH) {
            val ctx = context.applicationContext
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val step = intent.getIntExtra(EXTRA_STEP, 0)
                WidgetConfig.setMonthOffset(ctx, id, WidgetConfig.monthOffset(ctx, id) + step)
                AppWidgetManager.getInstance(ctx).updateAppWidget(id, build(ctx, id))
            }
            return
        }

        if (intent.action == ACTION_ITEM) {
            val ctx = context.applicationContext
            val id = intent.getLongExtra(EXTRA_ID, 0L)
            if (id != 0L) {
                when (intent.getIntExtra(EXTRA_MODE, MODE_EDIT)) {
                    MODE_TOGGLE -> flip(ctx, id) { it.done = !it.done }
                    MODE_STAR -> flip(ctx, id) { it.important = !it.important }
                    else ->
                        // 목록 화면과 같은 규칙: 본문 탭은 수정.
                        // 위젯 탭으로 온 PendingIntent 라 액티비티를 띄울 수 있습니다.
                        ctx.startActivity(
                            Intent(ctx, QuickAddActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(QuickAddActivity.EXTRA_EDIT_ID, id)
                        )
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    /**
     * 항목 하나를 바꿔 저장합니다.
     * update → save → refresh(위젯 갱신) 로 이어지므로 별도 갱신 호출이 없습니다.
     */
    private fun flip(ctx: Context, id: Long, change: (Todo) -> Unit) {
        val todo = TodoStore.load(ctx).firstOrNull { it.id == id } ?: return
        change(todo)
        TodoStore.update(ctx, todo)
        // 완료로 바뀌면 예약을 지우고, 해제하면 다시 겁니다.
        Reminders.schedule(ctx, todo)
    }

    companion object {
        const val ACTION_ITEM = "com.hj0128.todolock.WIDGET_ITEM"

        /** 헤더의 달력 아이콘 — 위젯 안을 목록 ↔ 달력으로 바꿉니다 */
        const val ACTION_MODE = "com.hj0128.todolock.WIDGET_MODE"

        /** 헤더의 ◀ ▶ — 달력에서 달을 넘깁니다 */
        const val ACTION_MONTH = "com.hj0128.todolock.WIDGET_MONTH"

        const val EXTRA_ID = "todo_id"
        const val EXTRA_MODE = "mode"
        const val EXTRA_STEP = "step"
        const val MODE_EDIT = 0
        const val MODE_TOGGLE = 1
        const val MODE_STAR = 2

        /** 목록/개수 모두 다시 그립니다. 위젯이 없으면 아무 일도 하지 않습니다. */
        fun refresh(ctx: Context) {
            val mgr = try {
                AppWidgetManager.getInstance(ctx)
            } catch (e: Exception) {
                null
            } ?: return

            val ids = try {
                mgr.getAppWidgetIds(ComponentName(ctx, TodoWidget::class.java))
            } catch (e: Exception) {
                return
            }
            if (ids.isEmpty()) return

            // 시간이 흘러 글자가 달라지는 순간(자정·오늘 기한 시각)에 다시 그리도록.
            // 데이터가 바뀔 때마다 여기를 지나므로 늘 최신 상태로 다시 잡힙니다.
            DayRollover.arm(ctx)

            // 헤더의 개수는 updateAppWidget 으로, 목록 내용은 notify 로 갱신됩니다.
            for (id in ids) {
                try {
                    mgr.updateAppWidget(id, build(ctx, id))
                } catch (e: Exception) {
                    // 위젯 갱신 실패가 앱 동작을 막지 않도록 무시합니다
                }
            }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.wList)
        }

        private fun build(ctx: Context, widgetId: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_todo)
            val calendar = WidgetConfig.isCalendar(ctx, widgetId)
            val shown = shownMonth(ctx, widgetId, calendar)

            rv.setViewVisibility(R.id.wListBox, if (calendar) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.wCalBox, if (calendar) View.VISIBLE else View.GONE)

            // 달 이름과 화살표는 달력일 때만, 목록 제목은 목록일 때만.
            rv.setViewVisibility(R.id.wCount, if (calendar) View.GONE else View.VISIBLE)
            rv.setViewVisibility(R.id.wMonth, if (calendar) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.wPrev, if (calendar) View.VISIBLE else View.GONE)
            rv.setViewVisibility(R.id.wNext, if (calendar) View.VISIBLE else View.GONE)

            // 아이콘은 '지금 무엇인지' 가 아니라 '누르면 어디로 가는지' 를 말합니다.
            // 달력을 보고 있는데 달력 아이콘이 있으면 돌아갈 길이 안 보입니다.
            rv.setImageViewResource(
                R.id.wCalendar,
                if (calendar) R.drawable.ic_list else R.drawable.ic_calendar
            )
            rv.setContentDescription(
                R.id.wCalendar,
                if (calendar) "목록으로 보기" else "달력으로 보기"
            )

            val pending = TodoStore.pendingSorted(ctx)
            rv.setTextViewText(
                R.id.wCount,
                // 비어 있을 때는 개수를 붙이지 않습니다. 아래 빈 목록 자리에
                // '할 일이 없습니다' 가 이미 뜨므로 헤더까지 거들 필요가 없습니다.
                if (pending.isEmpty()) "할 일" else "할 일 " + pending.size + "개"
            )
            // 화살표까지 들어가 좁아지므로, 작은 위젯에서는 연도를 뺍니다.
            rv.setTextViewText(
                R.id.wMonth,
                if (widthDp(ctx, widgetId) >= WIDE_DP) TodoStore.prettyMonth(shown)
                else TodoStore.shortMonth(shown)
            )

            // 겉모습 설정 적용. background 는 알파를 못 바꿔서 배경을 ImageView 로 깔았습니다.
            // 뒤쪽 판은 옅게, 그 위에 얹는 판(행 · 달력)과 헤더는 진하게 해서
            // 두 층이 구분됩니다.
            rv.setInt(R.id.wBg, "setImageAlpha", WidgetConfig.listAlpha(ctx))
            rv.setInt(R.id.wHeadBg, "setImageAlpha", WidgetConfig.panelAlpha(ctx))
            rv.setInt(R.id.wCalBg, "setImageAlpha", WidgetConfig.panelAlpha(ctx))

            // 강조색 팔레트. 위젯 XML 은 런처가 그려서 ?attr 이 풀리지 않으므로
            // 색을 직접 넣습니다. 판 색은 알파와 함께 걸려도 서로 간섭하지 않습니다.
            val accent = ThemeConfig.headingColor(ctx)
            val panel = ThemeConfig.panelColor(ctx)
            rv.setInt(R.id.wHeadBg, "setColorFilter", panel)
            rv.setInt(R.id.wBg, "setColorFilter", panel)
            rv.setTextColor(R.id.wCount, accent)
            rv.setTextColor(R.id.wMonth, accent)
            rv.setInt(R.id.wAdd, "setColorFilter", accent)
            rv.setInt(R.id.wCalendar, "setColorFilter", accent)
            rv.setInt(R.id.wSettings, "setColorFilter", accent)
            rv.setInt(R.id.wPrev, "setColorFilter", accent)
            rv.setInt(R.id.wNext, "setColorFilter", accent)
            rv.setTextViewTextSize(R.id.wCount, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.headerSp(ctx))
            rv.setTextViewTextSize(R.id.wMonth, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.headerSp(ctx))
            rv.setTextViewTextSize(R.id.wEmpty, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx))

            // 위젯이 여러 개 있어도 서로 다른 Intent 로 인식되도록 data 에 위젯 id 를 담습니다.
            val svc = Intent(ctx, TodoWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.wList, svc)
            rv.setEmptyView(R.id.wList, R.id.wEmpty)

            if (calendar) {
                fillCalendar(ctx, rv, accent, shown)
                rv.setOnClickPendingIntent(R.id.wPrev, monthStep(ctx, widgetId, -1))
                rv.setOnClickPendingIntent(R.id.wNext, monthStep(ctx, widgetId, 1))
            }

            // 행마다 다른 동작(수정/완료)을 fillInIntent 로 구분하므로 MUTABLE 이어야 합니다.
            rv.setPendingIntentTemplate(
                R.id.wList,
                PendingIntent.getBroadcast(
                    ctx, 0,
                    Intent(ctx, TodoWidget::class.java).setAction(ACTION_ITEM),
                    PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
                )
            )

            // ＋ 는 앱을 열지 않고 홈 화면 위에 입력 시트만 띄웁니다.
            rv.setOnClickPendingIntent(
                R.id.wAdd,
                PendingIntent.getActivity(
                    ctx, 1,
                    Intent(ctx, QuickAddActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            // 톱니바퀴 = 위젯 겉모습 설정
            rv.setOnClickPendingIntent(
                R.id.wSettings,
                PendingIntent.getActivity(
                    ctx, 2,
                    Intent(ctx, WidgetSettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            // 달력 아이콘 = 위젯 안을 목록 ↔ 달력으로. 위젯마다 따로 기억하므로
            // 눌린 위젯이 어느 것인지 data 로 구분해 둡니다(같은 요청 코드라도
            // data 가 다르면 다른 PendingIntent 입니다).
            rv.setOnClickPendingIntent(
                R.id.wCalendar,
                PendingIntent.getBroadcast(
                    ctx, 4,
                    Intent(ctx, TodoWidget::class.java)
                        .setAction(ACTION_MODE)
                        .setData(Uri.parse("todolock://widget/" + widgetId))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            // 헤더의 제목을 누르면 지금 보고 있는 것의 큰 화면으로 갑니다 —
            // 목록이면 앱 전체, 달력이면 달력 화면.
            rv.setOnClickPendingIntent(
                if (calendar) R.id.wMonth else R.id.wCount,
                PendingIntent.getActivity(
                    ctx, if (calendar) 5 else 0,
                    Intent(ctx, if (calendar) CalendarActivity::class.java else MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return rv
        }

        private fun mutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        /**
         * 이번 달 날짜 칸을 채웁니다.
         *
         * 목록과 달리 컬렉션(어댑터)을 쓰지 않습니다. 컬렉션은 줄 높이를 내용에
         * 맞춰 버려 바닥에 빈자리가 크게 남는데, 늘려 줄 방법이 없습니다.
         * 여섯 줄을 레이아웃에 박아 두고 무게로 나누면 정확히 들어찹니다.
         */
        /** 지금 위젯이 보여 줄 달. 목록일 때는 쓰이지 않지만 계산은 같습니다. */
        private fun shownMonth(ctx: Context, widgetId: Int, calendar: Boolean): Calendar {
            val cal = Calendar.getInstance()
            if (calendar) cal.add(Calendar.MONTH, WidgetConfig.monthOffset(ctx, widgetId))
            return cal
        }

        /** ◀ ▶. 위젯마다 다른 곳을 넘겨야 하므로 data 로 구분합니다. */
        private fun monthStep(ctx: Context, widgetId: Int, step: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 7,
                Intent(ctx, TodoWidget::class.java)
                    .setAction(ACTION_MONTH)
                    .setData(Uri.parse("todolock://month/" + widgetId + "/" + step))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(EXTRA_STEP, step),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /**
         * 위젯의 가로 폭(dp). 런처가 알려준 값이 없으면 좁은 쪽으로 봅니다 —
         * 넉넉하다고 봤다가 글자가 잘리는 쪽이 나쁩니다.
         */
        private fun widthDp(ctx: Context, widgetId: Int): Int {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return 0
            return try {
                AppWidgetManager.getInstance(ctx)
                    .getAppWidgetOptions(widgetId)
                    ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            } catch (e: Exception) {
                0
            }
        }

        private fun fillCalendar(ctx: Context, rv: RemoteViews, accent: Int, month: Calendar) {
            val days = MonthGrid.build(ctx, month, maxEntries = 0)
            val today = TodoStore.today()
            val rows = days.size / MonthGrid.COLUMNS

            // 다섯 줄이면 되는 달은 마지막 줄을 빼고 나머지가 그만큼 늘어납니다.
            for (r in ROW_IDS.indices) {
                rv.setViewVisibility(ROW_IDS[r], if (r < rows) View.VISIBLE else View.GONE)
            }

            for (i in days.indices) {
                val day = days[i]
                rv.setTextViewText(CELL_IDS[i], cellText(ctx, day, today, accent))
                // 날짜를 누르면 그 날짜로 달력 화면이 열립니다. 위젯마다·날짜마다
                // 다른 곳으로 가야 하므로 data 로 구분합니다.
                rv.setOnClickPendingIntent(
                    CELL_IDS[i],
                    PendingIntent.getActivity(
                        ctx, 6,
                        Intent(ctx, CalendarActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .setData(Uri.parse("todolock://day/" + day.key))
                            .putExtra(CalendarActivity.EXTRA_DATE, day.key),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }

        /**
         * 칸 하나에 들어갈 글. 윗줄은 날짜, 아랫줄은 남은 개수를 나타내는 점입니다.
         *
         * 한 TextView 에 두 줄로 넣고 색만 나눠 칠합니다 — 칸마다 뷰를 둘씩 두면
         * 한 달에 여든넷이 되고, 그만큼을 통째로 런처에 실어 보내야 합니다.
         */
        private fun cellText(ctx: Context, day: Day, today: String, accent: Int): CharSequence {
            val dots = when {
                day.pending > 0 -> minOf(day.pending, MAX_DOTS)
                day.done > 0 -> 1
                else -> 0
            }
            val number = day.dayOfMonth.toString()
            val text = SpannableString(number + "\n" + "●".repeat(dots))

            val dim = ContextCompat.getColor(ctx, R.color.widget_text_dim)
            text.setSpan(
                ForegroundColorSpan(
                    when {
                        day.key == today -> accent
                        !day.inMonth -> dim
                        else -> ContextCompat.getColor(ctx, R.color.widget_text)
                    }
                ),
                0, number.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (day.key == today) {
                text.setSpan(
                    StyleSpan(Typeface.BOLD), 0, number.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (dots > 0) {
                val from = number.length + 1
                text.setSpan(
                    ForegroundColorSpan(
                        when {
                            day.overdue -> ContextCompat.getColor(ctx, R.color.overdue)
                            day.pending == 0 -> dim
                            else -> accent
                        }
                    ),
                    from, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // 점은 날짜보다 작아야 숫자가 먼저 읽힙니다.
                text.setSpan(
                    RelativeSizeSpan(DOT_SCALE), from, text.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return text
        }

        /** 칸이 좁아 점은 셋까지만 찍습니다. 그 이상은 눌러서 봐야 합니다. */
        private const val MAX_DOTS = 3
        private const val DOT_SCALE = 0.55f

        /** 이 폭(dp)부터는 헤더에 연도까지 들어갑니다 */
        private const val WIDE_DP = 250

        private val ROW_IDS = intArrayOf(
            R.id.wRow0, R.id.wRow1, R.id.wRow2, R.id.wRow3, R.id.wRow4, R.id.wRow5
        )

        private val CELL_IDS = intArrayOf(
            R.id.wD0, R.id.wD1, R.id.wD2, R.id.wD3, R.id.wD4, R.id.wD5, R.id.wD6,
            R.id.wD7, R.id.wD8, R.id.wD9, R.id.wD10, R.id.wD11, R.id.wD12, R.id.wD13,
            R.id.wD14, R.id.wD15, R.id.wD16, R.id.wD17, R.id.wD18, R.id.wD19, R.id.wD20,
            R.id.wD21, R.id.wD22, R.id.wD23, R.id.wD24, R.id.wD25, R.id.wD26, R.id.wD27,
            R.id.wD28, R.id.wD29, R.id.wD30, R.id.wD31, R.id.wD32, R.id.wD33, R.id.wD34,
            R.id.wD35, R.id.wD36, R.id.wD37, R.id.wD38, R.id.wD39, R.id.wD40, R.id.wD41
        )
    }
}
