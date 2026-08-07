package com.hj0128.todolock

import android.content.Context
import android.content.Intent
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
        TodoWidgetFactory(applicationContext)
}

private class TodoWidgetFactory(
    private val ctx: Context
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
        rv.setTextViewTextSize(R.id.wMemo, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx))

        // 행은 뒤쪽 목록 판보다 진하게 (헤더와 같은 알파)
        rv.setInt(R.id.wItemBg, "setImageAlpha", WidgetConfig.panelAlpha(ctx))

        // 왼쪽에는 기한만 둡니다. 미리 알림은 오른쪽 끝에 따로 붙어,
        // 기한 시각과 알림 시각이 한 줄에 나와도 섞여 읽히지 않습니다.
        // 오늘 기한은 파란 굵은 글씨, 지난 기한은 빨간 글씨입니다(목록 화면과 같은 규칙).
        // '지남' 이라는 말은 붙이지 않습니다 — 날짜와 색이 이미 같은 이야기를 합니다.
        val overdue = TodoStore.isOverdue(todo)
        val dueToday = TodoStore.isDueToday(todo)
        val due = TodoStore.prettyDue(todo)

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
            ContextCompat.getColor(
                ctx,
                when {
                    overdue -> R.color.overdue
                    dueToday -> R.color.sky_heading
                    else -> R.color.widget_text_dim
                }
            )
        )

        // 알림 쪽은 기한이 지나도 색을 바꾸지 않습니다. '지남' 은 기한의 사정입니다.
        rv.setTextViewText(
            R.id.wRemind,
            if (todo.hasReminder) "🔔 " + TodoStore.prettyDateTime(todo.remindAt) else ""
        )
        rv.setViewVisibility(R.id.wRemind, if (todo.hasReminder) View.VISIBLE else View.GONE)

        // 메모 첫 줄. 있는 항목만 한 줄 더 차지합니다.
        rv.setTextViewText(R.id.wMemo, TodoStore.memoLine(todo))
        rv.setViewVisibility(R.id.wMemo, if (todo.hasMemo) View.VISIBLE else View.GONE)

        rv.setImageViewResource(
            R.id.wStar,
            if (todo.important) R.drawable.ic_star else R.drawable.ic_star_border
        )

        // 컬렉션 위젯은 행마다 PendingIntent 를 만들 수 없어 템플릿 하나를 공유합니다.
        // 그래서 어느 영역을 눌렀는지는 fillInIntent 의 extra 로 구분합니다.
        // 목록 화면과 같은 규칙: 본문 = 수정, 동그라미 = 완료, 별 = 중요.
        rv.setOnClickFillInIntent(R.id.wRoot, fill(todo.id, TodoWidget.MODE_EDIT))
        rv.setOnClickFillInIntent(R.id.wCheck, fill(todo.id, TodoWidget.MODE_TOGGLE))
        rv.setOnClickFillInIntent(R.id.wStar, fill(todo.id, TodoWidget.MODE_STAR))
        return rv
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
}
