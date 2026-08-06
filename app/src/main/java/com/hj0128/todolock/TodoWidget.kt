package com.hj0128.todolock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.widget.RemoteViews

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
    }

    override fun onReceive(context: Context, intent: Intent) {
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
        const val EXTRA_ID = "todo_id"
        const val EXTRA_MODE = "mode"
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

            val pending = TodoStore.pendingSorted(ctx)
            rv.setTextViewText(
                R.id.wCount,
                // 비어 있을 때는 개수를 붙이지 않습니다. 아래 빈 목록 자리에
                // '할 일이 없습니다' 가 이미 뜨므로 헤더까지 거들 필요가 없습니다.
                if (pending.isEmpty()) "할 일" else "할 일 " + pending.size + "개"
            )

            // 겉모습 설정 적용. background 는 알파를 못 바꿔서 배경을 ImageView 로 깔았습니다.
            // 뒤쪽 목록 판은 옅게, 헤더는 진하게 해서 두 층이 구분됩니다.
            rv.setInt(R.id.wBg, "setImageAlpha", WidgetConfig.listAlpha(ctx))
            rv.setInt(R.id.wHeadBg, "setImageAlpha", WidgetConfig.panelAlpha(ctx))
            rv.setTextViewTextSize(R.id.wCount, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.headerSp(ctx))
            rv.setTextViewTextSize(R.id.wEmpty, TypedValue.COMPLEX_UNIT_SP, WidgetConfig.subSp(ctx))

            // 위젯이 여러 개 있어도 서로 다른 Intent 로 인식되도록 data 에 위젯 id 를 담습니다.
            val svc = Intent(ctx, TodoWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.wList, svc)
            rv.setEmptyView(R.id.wList, R.id.wEmpty)

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
            // 헤더(개수)를 누르면 앱 전체를 엽니다.
            rv.setOnClickPendingIntent(
                R.id.wCount,
                PendingIntent.getActivity(
                    ctx, 0,
                    Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return rv
        }

        private fun mutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    }
}
