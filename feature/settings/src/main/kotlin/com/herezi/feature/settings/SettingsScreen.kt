package com.herezi.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.account.AuthProvider
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
    onDeleteAccount: () -> Unit = {},
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
        account = AccountActions(
            onSignIn = viewModel::signIn,
            onSignOut = viewModel::signOut,
            onDeleteAccount = onDeleteAccount,
        ),
        onReport = {
            if (!sendReport(context, appVersion)) {
                scope.launch { toast.showSnackbar("메일 앱이 없어요. ${HereziLinks.SUPPORT_EMAIL} 으로 보내 주세요") }
            }
        },
    )
}

@Suppress("LongParameterList", "LongMethod")
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
    account: AccountActions = AccountActions(),
    onReport: () -> Unit,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        topBar = { TitleBar("설정", onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            GroupTitle("계정")
            val provider = state.provider
            if (provider == null) {
                SettingRow("로그인", hint = "로그인 창이 열려요", onClick = account.onSignIn)
            } else {
                AccountRow(provider)
                SettingRow("로그아웃", onClick = { confirmSignOut = true })
                SettingRow("회원 탈퇴", secondary = true, onClick = account.onDeleteAccount)
            }
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
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("로그아웃할까요?") },
            text = { Text("다시 로그인하면 저장한 볼거리를 그대로 볼 수 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    account.onSignOut()
                }) { Text("로그아웃") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("취소") } },
        )
    }
}

/** S6 계정 그룹 동작. */
data class AccountActions(
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDeleteAccount: () -> Unit = {},
)

/** 제공자 표시 28 + "카카오로 로그인됨". 정적 텍스트(탭 없음), 표시는 장식. */
@Composable
private fun AccountRow(provider: AuthProvider) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = HereziSpace.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProviderMark(provider)
        Spacer(Modifier.width(HereziSpace.s3))
        Text(provider.signedInLabel, style = HereziTheme.typography.body, color = HereziTheme.colors.textPrimary)
    }
}

@Composable
private fun ProviderMark(provider: AuthProvider) {
    val (bg, icon, size) = when (provider) {
        AuthProvider.KAKAO -> Triple(Color(0xFFFEE500), R.drawable.ic_provider_kakao, 18.dp)
        AuthProvider.APPLE -> Triple(Color.Black, R.drawable.ic_provider_apple, 16.dp)
    }
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(bg).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) { Image(painterResource(icon), null, Modifier.size(size)) }
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
    secondary: Boolean = false,
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
            color = if (secondary) HereziTheme.colors.textSecondary else HereziTheme.colors.textPrimary,
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
