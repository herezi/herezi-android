package com.herezi.feature.spotdetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.herezi.core.domain.Spot
import com.herezi.core.ui.formatPeriod
import com.herezi.core.ui.label

@Composable
fun SpotDetailRoute(onBack: () -> Unit, viewModel: SpotDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SpotDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onDirections = { openNaverDirections(context, it) },
    )
}

@Composable
fun SpotDetailScreen(state: SpotDetailUiState, onBack: () -> Unit, onRetry: () -> Unit, onDirections: (Spot) -> Unit) {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SpotDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SpotDetailUiState.NotFound ->
                    Message("끝났거나 없는 볼거리예요", "뒤로", onBack, Modifier.align(Alignment.Center))
                is SpotDetailUiState.Failed ->
                    Message(state.message, "다시 시도", onRetry, Modifier.align(Alignment.Center))
                is SpotDetailUiState.Loaded -> SpotDetail(state.spot, onBack, onDirections)
            }
        }
    }
}

@Composable
private fun SpotDetail(spot: Spot, onBack: () -> Unit, onDirections: (Spot) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            TextButton(onClick = onBack) { Text("← 뒤로") }
            Text(
                spot.category.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(spot.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            val period = formatPeriod(spot.startsAt, spot.endsAt) + if (spot.isFree) " · 무료" else ""
            Text(period, style = MaterialTheme.typography.bodyMedium)
            listOfNotNull(spot.placeName, spot.address).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            spot.summary?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "출처: ${spot.source}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { onDirections(spot) }, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("네이버지도 길찾기")
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text)
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** 네이버지도 앱으로 길찾기를 열고, 앱이 없으면 웹으로 연다. */
private fun openNaverDirections(context: Context, spot: Spot) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse(NaverMapDirections.appUri(spot, context.packageName)))
    try {
        context.startActivity(app)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NaverMapDirections.webUri(spot))))
    }
}
