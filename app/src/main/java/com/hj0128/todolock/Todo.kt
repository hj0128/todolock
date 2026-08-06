package com.hj0128.todolock

data class Todo(
    val id: Long,
    var text: String,
    /** 기한 날짜 "yyyy-MM-dd" */
    var date: String,
    /**
     * 기한 시각(자정부터의 분). NO_TIME 이면 '날짜만' 이며 이쪽이 기본입니다.
     *
     * 날짜 키를 건드리지 않으려고 시각만 따로 둡니다. 오늘 목록·팝업·위젯이 모두
     * date 문자열 비교로 돌아가는데, 이를 절대 시각으로 바꾸면 그 전부가 흔들립니다.
     * 시각은 표시와 정렬·'지남' 판정에만 쓰이고 알람을 걸지 않습니다 — 우는 것은
     * remindAt 하나뿐이라, 알림이 어디서 오는지 헷갈릴 일이 없습니다.
     */
    var dueMinutes: Int = NO_TIME,
    var done: Boolean = false,
    var important: Boolean = false,
    /**
     * 미리 알림이 울릴 시각(epoch 밀리초). NO_REMIND 면 알림 없음.
     * 기한 기준 오프셋이 아니라 절대 시각이라, 기한과 무관하게 자유롭게 잡을 수 있습니다.
     */
    var remindAt: Long = NO_REMIND,
    /**
     * 이 알림을 이미 띄웠는지. 놓친 알림을 따라잡을 때 중복 알림을 막습니다.
     * remindAt 을 새로 정하면 false 로 되돌려야 합니다.
     */
    var notified: Boolean = false
) {
    val hasReminder: Boolean get() = remindAt > NO_REMIND

    val hasDueTime: Boolean get() = dueMinutes >= 0

    companion object {
        const val NO_REMIND = 0L

        /** 기한에 시각을 정하지 않은 상태. 옛 데이터도 이 값으로 읽힙니다. */
        const val NO_TIME = -1
    }
}
