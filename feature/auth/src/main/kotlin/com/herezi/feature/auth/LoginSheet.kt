package com.herezi.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.account.AuthProvider
import com.herezi.core.domain.account.LoginReason
import com.herezi.core.domain.account.LoginRequest
import com.herezi.core.ui.HereziLinks
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme

/** 카카오 브랜드 색 (DESIGN §2.5 예외 — S7 버튼·S6 제공자 표시에만). */
val KakaoYellow = Color(0xFFFEE500)
private val KakaoLabel = Color.Black.copy(alpha = 0.85f)
private const val DISABLED_ALPHA = 0.4f

fun LoginRequest?.sheetTitle(): String = when (this?.reason) {
    LoginReason.SAVE -> "로그인하면 저장할 수 있어요"
    else -> "로그인하고 저장한 볼거리를 모아 보세요"
}

/**
 * S7 로그인 시트. :app 이 [SavedLibrary.loginRequest] 가 있을 때 띄운다.
 * [onOpenUrl] 은 약관 링크(Custom Tabs, 시트는 그대로).
 */
@Composable
fun LoginSheetRoute(
    request: LoginRequest,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(viewModel) { viewModel.done.collect { dismiss() } }
    LoginSheet(
        title = request.sheetTitle(),
        state = state,
        onKakao = { viewModel.signInWithKakao(request) },
        onDismiss = onDismiss,
        onOpenUrl = onOpenUrl,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    title: String,
    state: LoginUiState,
    onKakao: () -> Unit,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val busy by rememberUpdatedState(state.inProgress != null)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = HereziRadius.xl, topEnd = HereziRadius.xl),
        containerColor = HereziTheme.colors.bgPrimary,
        scrimColor = HereziTheme.colors.scrim,
    ) {
        LoginSheetContent(title, state, onKakao, onClose = { if (!busy) onDismiss() }, onOpenUrl)
    }
}

@Composable
fun LoginSheetContent(
    title: String,
    state: LoginUiState,
    onKakao: () -> Unit,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val busy = state.inProgress != null
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = HereziSpace.s3)) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onClose,
                enabled = !busy,
                modifier = Modifier.align(
                    Alignment.TopEnd,
                ).padding(end = HereziSpace.s2).alpha(if (busy) DISABLED_ALPHA else 1f),
            ) { Icon(Icons.Rounded.Close, "닫기", tint = HereziTheme.colors.iconPrimary) }
        }
        Column(
            Modifier.weight(
                1f,
                fill = false,
            ).verticalScroll(rememberScrollState()).padding(horizontal = HereziSpace.s5),
        ) {
            Text(
                title,
                style = HereziTheme.typography.title2,
                color = HereziTheme.colors.textPrimary,
                maxLines = 2,
                modifier = Modifier.focusRequester(focus).focusable().semantics { heading() },
            )
            Spacer(Modifier.height(HereziSpace.s2))
            Text(
                "저장한 볼거리를 어느 기기에서든 다시 볼 수 있어요.",
                style = HereziTheme.typography.body,
                color = HereziTheme.colors.textSecondary,
            )
            state.error?.let { ErrorLine(it) }
        }
        Spacer(Modifier.height(HereziSpace.s6))
        Column(
            Modifier.padding(horizontal = HereziSpace.s5),
            verticalArrangement = Arrangement.spacedBy(HereziSpace.s3),
        ) {
            SocialButton(
                label = "카카오로 계속하기",
                container = KakaoYellow,
                content = KakaoLabel,
                icon = R.drawable.ic_kakao_symbol,
                progress = state.inProgress == AuthProvider.KAKAO,
                enabled = !busy || state.inProgress == AuthProvider.KAKAO,
                onClick = onKakao,
            )
        }
        Spacer(Modifier.height(HereziSpace.s5))
        TermsLine(onOpenUrl, Modifier.padding(horizontal = HereziSpace.s5))
    }
}

@Composable
private fun ErrorLine(text: String) {
    Row(
        Modifier.padding(top = HereziSpace.s3).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Error, null, tint = HereziTheme.colors.statusError, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(HereziSpace.s1))
        Text(text, style = HereziTheme.typography.caption, color = HereziTheme.colors.textPrimary)
    }
}

@Suppress("LongParameterList")
@Composable
private fun SocialButton(
    label: String,
    container: Color,
    content: Color,
    icon: Int,
    progress: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = { if (!progress) onClick() },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HereziSize.buttonLg)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { if (progress) contentDescription = "로그인 중" },
        shape = RoundedCornerShape(HereziRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
    ) {
        if (progress) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Image(painterResource(icon), null, modifier = Modifier.size(24.dp), colorFilter = null)
            Spacer(Modifier.width(HereziSpace.s2))
            Text(label, style = HereziTheme.typography.title3.copy(fontWeight = FontWeight.SemiBold), color = content)
        }
    }
}

/** "계속하면 만 14세 이상이며 [이용약관]과 [개인정보 처리방침]에 동의하는 것으로 봐요." */
@Composable
private fun TermsLine(onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(
        SpanStyle(
            color = HereziTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
    )
    val text = buildAnnotatedString {
        append("계속하면 만 14세 이상이며 ")
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpenUrl(HereziLinks.TERMS) }) { append("이용약관") }
        append("과 ")
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpenUrl(HereziLinks.PRIVACY) }) {
            append("개인정보 처리방침")
        }
        append("에 동의하는 것으로 봐요.")
    }
    Text(
        text,
        style = HereziTheme.typography.caption,
        color = HereziTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().heightIn(min = HereziSize.touch),
    )
}
