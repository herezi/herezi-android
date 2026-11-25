package com.herezi.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.ui.component.HereziToastHost
import com.herezi.core.ui.component.PrimaryButton
import com.herezi.core.ui.theme.HereziColors
import com.herezi.core.ui.theme.HereziRadius
import com.herezi.core.ui.theme.HereziSize
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme

/** S6 회원 탈퇴. [onFinished] = 완료 화면 **확인** → :app 이 탭을 처음 상태로 되돌리고 홈으로. */
@Composable
fun DeleteAccountRoute(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { toast.showSnackbar(it) } }
    // 진행 중에는 뒤로 막음, 완료 화면에서는 뒤로 = 확인.
    BackHandler(enabled = state.deleting || state.done) { if (state.done) onFinished() }
    if (state.done) {
        DeletedScreen(onFinished)
    } else {
        DeleteAccountScreen(state, toast, onBack, viewModel::toggleConfirmed, viewModel::delete)
    }
}

@Suppress("LongMethod")
@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    toast: SnackbarHostState,
    onBack: () -> Unit,
    onToggleConfirmed: () -> Unit,
    onDelete: () -> Unit,
) {
    var finalConfirm by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        snackbarHost = { HereziToastHost(toast) },
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = HereziSpace.s1, vertical = HereziSpace.s2)) {
                IconButton(onClick = onBack, enabled = !state.deleting) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로")
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = HereziTheme.colors.border)
                Box(Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3)) {
                    DeleteButton(state, onClick = { finalConfirm = true })
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(
                padding,
            ).verticalScroll(rememberScrollState()).padding(horizontal = HereziSpace.s5),
        ) {
            Spacer(Modifier.height(HereziSpace.s6))
            Text(
                "탈퇴하기 전에\n확인해 주세요",
                style = HereziTheme.typography.title1,
                color = HereziTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(HereziSpace.s8))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HereziTheme.colors.bgSecondary, RoundedCornerShape(HereziRadius.md))
                    .padding(horizontal = HereziSpace.s4, vertical = HereziSpace.s3),
            ) {
                LossRow(Icons.Rounded.Bookmark, state.savedLine)
                LossRow(Icons.Rounded.LinkOff, state.unlinkLine)
                LossRow(Icons.Rounded.ErrorOutline, "지운 정보는 되돌릴 수 없어요")
            }
            Spacer(Modifier.height(HereziSpace.s3))
            Text(
                "같은 계정으로 다시 가입할 수 있지만, 저장한 볼거리는 돌아오지 않아요.",
                style = HereziTheme.typography.caption,
                color = HereziTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(HereziSpace.s6))
            ConfirmCheck(state.confirmed, enabled = !state.deleting, onToggle = onToggleConfirmed)
            Spacer(Modifier.height(HereziSpace.s6))
        }
    }
    if (finalConfirm) {
        AlertDialog(
            onDismissRequest = { finalConfirm = false },
            title = { Text("정말 탈퇴할까요?") },
            text = { Text("저장한 볼거리와 계정 정보가 바로 지워져요.") },
            confirmButton = {
                TextButton(onClick = {
                    finalConfirm = false
                    onDelete()
                }) { Text("탈퇴", color = HereziTheme.colors.statusError) }
            },
            dismissButton = { TextButton(onClick = { finalConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun DeleteButton(state: DeleteAccountUiState, onClick: () -> Unit) {
    Button(
        onClick = { if (!state.deleting) onClick() },
        enabled = state.confirmed,
        modifier = Modifier.fillMaxWidth().heightIn(min = HereziSize.buttonLg),
        shape = RoundedCornerShape(HereziRadius.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = HereziTheme.colors.bgInverse,
            contentColor = HereziTheme.colors.textInverse,
            disabledContainerColor = HereziColors.Gray200,
            disabledContentColor = HereziColors.Gray500,
        ),
    ) {
        if (state.deleting) {
            CircularProgressIndicator(
                color = HereziTheme.colors.textInverse,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text("탈퇴하기", style = HereziTheme.typography.title3)
        }
    }
}

@Composable
private fun LossRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = HereziTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(HereziSpace.s3))
        Text(text, style = HereziTheme.typography.bodyStrong, color = HereziTheme.colors.textPrimary)
    }
}

@Composable
private fun ConfirmCheck(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HereziSize.touch)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(HereziRadius.xs)
        Box(
            Modifier
                .size(24.dp)
                .then(
                    if (checked) {
                        Modifier.background(HereziTheme.colors.bgInverse, shape)
                    } else {
                        Modifier.border(2.dp, HereziColors.Gray200, shape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = HereziTheme.colors.textInverse,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(HereziSpace.s3))
        Text("위 내용을 확인했어요", style = HereziTheme.typography.bodyStrong, color = HereziTheme.colors.textPrimary)
    }
}

/** 완료 화면. 뒤로 돌아갈 수 없다. */
@Composable
private fun DeletedScreen(onFinished: () -> Unit) {
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        bottomBar = {
            Box(Modifier.padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s3)) {
                PrimaryButton("확인", onClick = onFinished)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = HereziSpace.s5, vertical = HereziSpace.s10),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                null,
                tint = HereziTheme.colors.iconPrimary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(HereziSpace.s4))
            Text(
                "탈퇴했어요",
                style = HereziTheme.typography.title2,
                color = HereziTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(HereziSpace.s2))
            Text(
                "그동안 히어지를 써 주셔서 고마워요.",
                style = HereziTheme.typography.body,
                color = HereziTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
