package com.hj0128.todolock

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat

/** 위젯 목록에 행을 공급합니다. */
class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodoWidgetFactory(
            applicationContext,
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        )
}

private class TodoWidgetFactory(
    private val ctx: Context,
    /** 폭을 알아야 기한과 알림이 한 줄에 들어가는지 판단할 수 있습니다. */
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    /** onDataSetChanged 에서만 갱신합니다. getViewAt 은 이 스냅샷만 읽습니다. */
    private var items: List<Todo> = emptyList()

    override fun onCreate() {
        items = TodoStore.pendingSorted(ctx)
    }

    override fun onDataSetChanged() {
        items = TodoStore.pendingSorted(ctx)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
        // 목록이 바뀌는 도중 호출될 수 있어 범위를 확인합니다.
        val todo = items.getOrNull(position) ?: return rv

        rv.setTextViewText(R.id.wTitle, todo.text)

        // 글꼴 크기 설정을 행에도 적용합니다.
        rv.setTextViewTextSize(R.id.wTitle, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.titleSp(ctx))
        rv.setTextViewTextSize(R.id.wSub, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx))
        rv.setTextViewTextSize(R.id.wRemind, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx))
        rv.setTextViewTextSize(
            R.id.wRemindBelow, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx)
        )
        rv.setTextViewTextSize(R.id.wMemo, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx))

        // 행은 뒤쪽 목록 판보다 진하게 (헤더와 같은 알파)
        rv.setInt(R.id.wItemBg, "setImageAlpha", WidgetConfig.panelAlpha(ctx))

        // 왼쪽에는 기한만 둡니다. 미리 알림은 오른쪽 끝에 따로 붙어,
        // 기한 시각과 알림 시각이 한 줄에 나와도 섞여 읽히지 않습니다.
        // 오늘 기한은 파란 굵은 글씨, 지난 기한은 빨간 글씨입니다(목록 화면과 같은 규칙).
        // '지남' 이라는 말은 붙이지 않습니다 — 날짜와 색이 이미 같은 이야기를 합니다.
        val overdue = TodoStore.isOverdue(todo)
        val dueToday = TodoStore.isDueToday(todo)
        val due = TodoStore.prettyDue(ctx, todo)

        rv.setTextViewText(
            R.id.wSub,
            if (dueToday) {
                SpannableString(due).apply {
                    setSpan(
                        StyleSpan(Typeface.BOLD), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                due
            }
        )
        rv.setTextColor(
            R.id.wSub,
            when {
                overdue -> ContextCompat.getColor(ctx, R.color.overdue)
                dueToday -> ThemeConfig.headingColor(ctx)
                else -> ContextCompat.getColor(ctx, R.color.widget_text_dim)
            }
        )

        // 알림은 자리가 되면 기한과 같은 줄 오른쪽 끝에, 모자라면 아랫줄에 놓습니다.
        // 색은 기한이 지나도 바꾸지 않습니다 — 지난 것은 기한의 사정입니다.
        val remind =
            if (todo.hasReminder) "🔔 " + TodoStore.prettyDateTime(ctx, todo.remindAt) else ""
        val inline = todo.hasReminder && fitsOnOneLine(due, remind, dueToday)

        rv.setTextViewText(R.id.wRemind, remind)
        rv.setViewVisibility(R.id.wRemind, if (inline) View.VISIBLE else View.GONE)
        rv.setTextViewText(R.id.wRemindBelow, remind)
        rv.setViewVisibility(
            R.id.wRemindBelow,
            if (todo.hasReminder && !inline) View.VISIBLE else View.GONE
        )

        // 메모 첫 줄. 있는 항목만 한 줄 더 차지합니다.
        rv.setTextViewText(R.id.wMemo, TodoStore.memoLine(todo))
        rv.setViewVisibility(R.id.wMemo, if (todo.hasMemo) View.VISIBLE else View.GONE)

        rv.setImageViewResource(
            R.id.wStar,
            if (todo.important) R.drawable.ic_star else R.drawable.ic_star_border
        )

        // 아이콘 색은 레이아웃의 tint 가 아니라 여기서 넣습니다.
        // 위젯 XML 은 런처가 그려서 ?attr 로 팔레트를 따라갈 수 없습니다.
        val accent = ThemeConfig.headingColor(ctx)
        rv.setInt(R.id.wCheck, "setColorFilter", accent)
        rv.setInt(R.id.wStar, "setColorFilter", accent)

        // 컬렉션 위젯은 행마다 PendingIntent 를 만들 수 없어 템플릿 하나를 공유합니다.
        // 그래서 어느 영역을 눌렀는지는 fillInIntent 의 extra 로 구분합니다.
        // 목록 화면과 같은 규칙: 본문 = 수정, 동그라미 = 완료, 별 = 중요.
        rv.setOnClickFillInIntent(R.id.wRoot, fill(todo.id, TodoWidget.MODE_EDIT))
        rv.setOnClickFillInIntent(R.id.wCheck, fill(todo.id, TodoWidget.MODE_TOGGLE))
        rv.setOnClickFillInIntent(R.id.wStar, fill(todo.id, TodoWidget.MODE_STAR))
        return rv
    }

    /**
     * 기한과 알림이 한 줄에 들어가는지 미리 계산합니다.
     *
     * RemoteViews 는 만들어 보내기만 할 뿐 잴 수 없으므로, 같은 글자 크기의
     * Paint 로 폭을 직접 재서 판단합니다. 재지 않고 weight 로 나눠 가지면
     * 글자가 커졌을 때 한쪽이 '8...' 처럼 잘립니다.
     */
    private fun fitsOnOneLine(due: CharSequence, remind: String, bold: Boolean): Boolean {
        val dm = ctx.resources.displayMetrics
        val paint = Paint().apply {
            textSize = WidgetConfig.subSp(ctx) * dm.scaledDensity
            // 오늘 기한은 굵게 그려지므로 잴 때도 굵게 재야 합니다.
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        val needed = (paint.measureText(due.toString()) + paint.measureText(remind)) * SAFETY
        return needed <= (widgetWidthDp() - CHROME_DP) * dm.density
    }

    /**
     * 위젯의 가로 폭(dp). 런처가 알려준 값이 없으면 화면 폭으로 어림합니다.
     * 최솟값(OPTION_APPWIDGET_MIN_WIDTH)이라 실제보다 작게 잡히는데, 그래야
     * 애매할 때 두 줄로 안전하게 떨어집니다.
     */
    private fun widgetWidthDp(): Float {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val w = try {
                AppWidgetManager.getInstance(ctx)
                    .getAppWidgetOptions(widgetId)
                    ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            } catch (e: Exception) {
                0
            }
            if (w > 0) return w.toFloat()
        }
        val dm = ctx.resources.displayMetrics
        return dm.widthPixels / dm.density - 24f
    }

    /** 어느 영역을 눌렀는지 위젯 쪽 템플릿에 전달할 extra */
    private fun fill(id: Long, mode: Int): Intent =
        Intent()
            .putExtra(TodoWidget.EXTRA_ID, id)
            .putExtra(TodoWidget.EXTRA_MODE, mode)

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        /**
         * 글자가 쓸 수 없는 가로 폭(dp).
         * 목록 판 안쪽 여백 16 + 행 좌우 여백 8 + 완료 동그라미 34 + 본문 들여쓰기 6
         * + 별 34 + 두 글자 사이 8 = 106. 런처가 위젯에 얹는 여백까지 더해 112 로 둡니다.
         */
        const val CHROME_DP = 112f

        /**
         * 잰 값에 붙이는 여유. 이모지 폭과 실제 렌더가 조금씩 어긋나는데,
         * 모자라서 잘리는 쪽이 남아서 한 줄 더 쓰는 쪽보다 나쁩니다.
         */
        const val SAFETY = 1.03f
    }
}
