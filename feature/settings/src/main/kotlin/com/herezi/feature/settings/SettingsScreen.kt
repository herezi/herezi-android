package com.herezi.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.ui.BundledLicenses
import com.herezi.core.ui.HereziLinks
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import kotlinx.coroutines.launch

/** [appVersion] 은 :app 이 BuildConfig 로 채운다. 예: "1.0.0 (12)". */
@Composable
fun SettingsRoute(
    appVersion: String,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val toast = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    SettingsScreen(
        state = state,
        appVersion = appVersion,
        toast = toast,
        onBack = onBack,
        onLocation = { openAppSettings(context) },
        onOpenSources = onOpenSources,
        onOpenLicenses = onOpenLicenses,
        onOpenUrl = { openUrl(context, it) },
        onReport = {
            if (!sendReport(context, appVersion)) {
                scope.launch { toast.showSnackbar("메일 앱이 없어요. ${HereziLinks.SUPPORT_EMAIL} 으로 보내 주세요") }
            }
        },
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    appVersion: String,
    toast: SnackbarHostState,
    onBack: () -> Unit,
    onLocation: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenLicenses: () -> Unit = {},
    onReport: () -> Unit,
) {
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        topBar = { TitleBar("설정", onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            GroupTitle("위치")
            SettingRow("위치 권한", value = state.accessLabel, hint = "설정 앱이 열려요", onClick = onLocation)
            GroupTitle("정보")
            SettingRow("정보 출처", onClick = onOpenSources)
            SettingRow("이용약관", hint = "브라우저가 열려요", onClick = { onOpenUrl(HereziLinks.TERMS) })
            SettingRow("개인정보 처리방침", hint = "브라우저가 열려요", onClick = { onOpenUrl(HereziLinks.PRIVACY) })
            SettingRow("오픈소스 라이선스", onClick = onOpenLicenses)
            GroupTitle("도움")
            SettingRow("정보 오류 제보", hint = "메일 앱이 열려요", onClick = onReport)
            SelectionContainer {
                Text(
                    HereziLinks.SUPPORT_EMAIL,
                    style = HereziTheme.typography.caption,
                    color = HereziTheme.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = HereziSpace.s5),
                )
            }
            Spacer(Modifier.height(HereziSpace.s6))
            SettingRow("앱 버전", value = appVersion, showChevron = false, onClick = null)
            Spacer(Modifier.height(HereziSpace.s8))
            Text(
                "지금 내 주변, 히어지",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(HereziSpace.s8))
        }
    }
}

/** 정보 출처 (S6 하위 화면). */
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(containerColor = HereziTheme.colors.bgPrimary, topBar = { TitleBar("정보 출처", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(HereziSpace.s4))
            DataSources.forEach { source ->
                SettingRow(
                    source.name,
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    hint = "브라우저가 열려요",
                    onClick = { openUrl(context, source.url) },
                )
            }
            Text(
                "요약은 AI(OpenAI GPT)가 원문을 정리한 것이에요. 정확한 내용은 원문을 확인해 주세요.",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s6),
            )
        }
    }
}

/** 오픈소스 라이선스 (S6 하위 화면). 번들한 에셋마다 이름·버전·라이선스와 원문 전체. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(containerColor = HereziTheme.colors.bgPrimary, topBar = { TitleBar("오픈소스 라이선스", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            BundledLicenses.forEach { item ->
                val text = remember(item.textRes) {
                    context.resources.openRawResource(item.textRes).bufferedReader().use { it.readText() }
                }
                GroupTitle("${item.name} ${item.version}")
                SettingRow(
                    item.license,
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    hint = "브라우저가 열려요",
                    onClick = { openUrl(context, item.url) },
                )
                SelectionContainer {
                    Text(
                        text,
                        style = HereziTheme.typography.caption,
                        color = HereziTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3),
                    )
                }
            }
            Spacer(Modifier.height(HereziSpace.s8))
        }
    }
}

@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s1, vertical = HereziSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
        Text(
            title,
            style = HereziTheme.typography.title1,
            color = HereziTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = HereziTheme.typography.caption,
        color = HereziTheme.colors.textSecondary,
        modifier = Modifier
            .padding(horizontal = HereziSpace.s5)
            .padding(top = HereziSpace.s6, bottom = HereziSpace.s1)
            .semantics { heading() },
    )
}

@Composable
private fun SettingRow(
    title: String,
    value: String? = null,
    hint: String? = null,
    showChevron: Boolean = true,
    icon: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onClick)
                        .semantics {
                            if (hint != null) {
                                onClick(label = hint) {
                                    onClick()
                                    true
                                }
                            }
                        }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = HereziSpace.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = HereziTheme.typography.body,
            color = HereziTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, style = HereziTheme.typography.body, color = HereziTheme.colors.textSecondary)
        }
        if (onClick != null && showChevron) {
            Spacer(Modifier.width(HereziSpace.s1))
            Icon(icon, null, tint = HereziTheme.colors.iconSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    context.startActivity(intent)
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // 브라우저가 없는 기기: 조용히 무시.
    }
}

/** 메일 앱이 없으면 false. */
private fun sendReport(context: Context, appVersion: String): Boolean {
    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(HereziLinks.SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, REPORT_SUBJECT)
        putExtra(Intent.EXTRA_TEXT, reportBody(appVersion, Build.VERSION.RELEASE ?: "?"))
    }
    return try {
        context.startActivity(mail)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
