package com.herezi.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Category
import com.herezi.core.ui.component.HereziLogo
import com.herezi.core.ui.component.HereziTextButton
import com.herezi.core.ui.component.PrimaryButton
import com.herezi.core.ui.theme.CATEGORY_TINT_ALPHA
import com.herezi.core.ui.theme.HereziSpace
import com.herezi.core.ui.theme.HereziTheme
import com.herezi.core.ui.theme.color
import com.herezi.core.ui.theme.icon

private val LocationPermissions =
    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

/** 글꼴 크기가 이 배율 이상이면 3×3 아이콘 그림을 숨긴다. */
private const val LARGE_FONT_SCALE = 1.5f

@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 결과와 관계없이 홈으로 (S0).
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.finish()
    }
    LaunchedEffect(state) { if (state == OnboardingState.Done) onDone() }
    when (state) {
        OnboardingState.Show ->
            OnboardingScreen(onAllow = { launcher.launch(LocationPermissions) }, onLater = viewModel::finish)
        else -> Box(Modifier.fillMaxSize().background(HereziTheme.colors.bgPrimary))
    }
}

@Composable
fun OnboardingScreen(onAllow: () -> Unit, onLater: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Scaffold(
        containerColor = HereziTheme.colors.bgPrimary,
        bottomBar = {
            Column(
                Modifier.padding(horizontal = HereziSpace.s5).padding(bottom = HereziSpace.s4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PrimaryButton("위치 허용하기", onClick = onAllow)
                Spacer(Modifier.height(HereziSpace.s2))
                HereziTextButton("나중에 할게요", onClick = onLater)
                Spacer(Modifier.height(HereziSpace.s2))
                Text(
                    "위치는 주변 볼거리를 찾는 데만 써요.",
                    style = HereziTheme.typography.caption,
                    color = HereziTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HereziSpace.s5),
        ) {
            Spacer(Modifier.height(HereziSpace.s10))
            HereziLogo(56.dp)
            Spacer(Modifier.height(HereziSpace.s6))
            Text(
                "지금 내 주변에 뭐가 있는지\n바로 보여드릴게요",
                style = HereziTheme.typography.display,
                color = HereziTheme.colors.textPrimary,
                modifier = Modifier.semantics {
                    heading()
                    contentDescription = "지금 내 주변에 뭐가 있는지 바로 보여드릴게요"
                },
            )
            Spacer(Modifier.height(HereziSpace.s3))
            Text(
                "축제, 공연, 전시, 팝업, 벚꽃 명소까지 — 위치만 허용하면 가입 없이 바로 볼 수 있어요.",
                style = HereziTheme.typography.body,
                color = HereziTheme.colors.textSecondary,
            )
            if (!largeFont) {
                Spacer(Modifier.height(HereziSpace.s10))
                CategoryGrid(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

/** 장식용 3×3 카테고리 원. */
@Composable
private fun CategoryGrid(modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(HereziSpace.s4),
    ) {
        Category.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(HereziSpace.s4)) {
                row.forEach { category ->
                    Box(
                        Modifier.size(56.dp).background(category.color.copy(alpha = CATEGORY_TINT_ALPHA), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(category.icon, null, tint = category.color, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
