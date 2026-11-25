package com.herezi.core.ui.link

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent

/**
 * 외부 웹 링크(약관·처리방침·공지사항·출처 등)를 인앱 브라우저(Android Custom Tabs)로 연다(S6, #46).
 *
 * - 툴바는 흰색, 제목 표시, 공유 버튼은 기본값.
 * - Custom Tabs 를 지원하는 브라우저가 없으면 `ACTION_VIEW` 로 일반 브라우저를 연다.
 * - 그것도 없으면 조용히 무시한다.
 */
fun openInAppBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val supportsCustomTabs = CustomTabsClient.getPackageName(context, null) != null
    try {
        if (supportsCustomTabs) {
            customTabsIntent().apply { intent.addNewTaskIfNeeded(context) }.launchUrl(context, uri)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addNewTaskIfNeeded(context))
        }
    } catch (_: ActivityNotFoundException) {
        // 브라우저가 없는 기기.
    }
}

private fun customTabsIntent(): CustomTabsIntent {
    val white = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(Color.WHITE)
        .setNavigationBarColor(Color.WHITE)
        .build()
    return CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(white)
        // 앱은 라이트 테마만 쓴다. 다크 모드여도 흰 툴바를 유지한다.
        .setColorScheme(CustomTabsIntent.COLOR_SCHEME_LIGHT)
        .build()
}

/** Activity 가 아닌 Context 에서 열 때만 새 태스크로. */
private fun Intent.addNewTaskIfNeeded(context: Context): Intent =
    if (context is Activity) this else addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
