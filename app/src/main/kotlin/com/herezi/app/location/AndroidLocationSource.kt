package com.herezi.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.herezi.core.domain.GeoPoint
import com.herezi.core.domain.LocationAccess
import com.herezi.core.domain.LocationSource
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 기기 위치. Play 서비스 없이 플랫폼 LocationManager 만 쓴다(가볍게).
 * 마지막 위치가 충분히 최근이면 그것을, 아니면 한 번 측위(최대 [TIMEOUT_MS])한다. 실패하면 null → 서울시청.
 */
class AndroidLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val executor = Executors.newSingleThreadExecutor()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED

    override fun access(): LocationAccess = when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.PRECISE
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.APPROXIMATE
        else -> LocationAccess.DENIED
    }

    @SuppressLint("MissingPermission") // access() 로 확인한다.
    override suspend fun current(): GeoPoint? {
        if (access() == LocationAccess.DENIED || !LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        val recent = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_AGE_MS }
            .minByOrNull { it.accuracy }
        val location = recent ?: providers.firstOrNull()?.let { fresh(it) }
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(provider: String): Location? = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val signal = androidx.core.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor) { cont.resume(it) }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 10 * 60 * 1000L
        const val TIMEOUT_MS = 5_000L
    }
}
