package com.example.todolock

data class Todo(
    val id: Long,
    var text: String,
    /** 기한 날짜 "yyyy-MM-dd" (시각은 두지 않습니다) */
    var date: String,
    var done: Boolean = false,
    var important: Boolean = false,
    /**
     * 미리 알림이 울릴 시각(epoch 밀리초). NO_REMIND 면 알림 없음.
     * 기한 기준 오프셋이 아니라 절대 시각이라, 기한과 무관하게 자유롭게 잡을 수 있습니다.
     */
    var remindAt: Long = NO_REMIND
) {
    val hasReminder: Boolean get() = remindAt > NO_REMIND

    companion object {
        const val NO_REMIND = 0L
    }
}
