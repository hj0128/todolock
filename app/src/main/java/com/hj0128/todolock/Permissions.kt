package com.hj0128.todolock

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/**
 * 잠금해제 팝업이 동작하는 데 필요한 권한 상태.
 *
 * 목록 화면(경고 배너)과 설정 화면(버튼 라벨·경고)이 같은 판단을 써야 하므로
 * 한곳에 모았습니다.
 */
object Permissions {

    /** 백그라운드에서 화면을 띄울 수 있는지. 없으면 팝업 대신 알림으로 대체됩니다. */
    fun canShowPopup(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** 절전이 감지 서비스를 죽이지 않도록 예외 처리됐는지. */
    fun isBatteryExempt(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(PowerManager::class.java)
        pm != null && pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (e: Exception) {
        false
    }

    /**
     * 잠금해제 팝업이 제대로 동작할 상태인지.
     * 둘 중 하나라도 없으면 팝업이 안 뜨거나, 며칠 뒤 조용히 멈춥니다.
     */
    fun unlockPopupReady(ctx: Context): Boolean =
        canShowPopup(ctx) && isBatteryExempt(ctx)

    /**
     * 목록 화면 배너에 쓸 문구. 준비된 상태면 null.
     * 더 심각한 쪽(팝업 자체가 불가능)을 먼저 알립니다.
     */
    fun warning(ctx: Context): String? = when {
        !canShowPopup(ctx) ->
            "⚠ 잠금해제 팝업이 뜨지 않습니다 · 눌러서 권한 허용"
        !isBatteryExempt(ctx) ->
            "⚠ 절전 때문에 잠금해제 팝업이 멈출 수 있습니다 · 눌러서 설정"
        else -> null
    }
}
