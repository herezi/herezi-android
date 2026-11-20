package com.herezi.core.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * 라이브러리 없는 작은 이미지 로더 (Maven Central 을 못 쓰는 환경 때문에 Coil 대신, #14).
 *
 * - 받기: HttpURLConnection, 연결·읽기 각 [TIMEOUT_MS](DESIGN §6.16 "10초 초과 = 실패"), 최대 [MAX_DOWNLOAD_BYTES].
 * - 디스크: `cacheDir/images/<sha256>` 에 원본 바이트. 합계 [DISK_MAX_BYTES] 넘으면 오래 안 쓴 것부터 지운다.
 * - 메모리: 요청 크기로 줄여 디코딩한 Bitmap 을 바이트 기준 LruCache 에.
 * - 취소: 호출 코루틴이 취소되면 연결을 끊는다(컴포저블이 사라질 때).
 * 실패(4xx·5xx·디코딩 실패·시간 초과)는 null — 화면은 대체 그림을 그린다.
 */
class HereziImageLoader(
    private val diskDir: File,
    memoryBytes: Int = defaultMemoryBytes(),
    private val diskMaxBytes: Long = DISK_MAX_BYTES,
) {
    private val memory = object : LruCache<String, Bitmap>(memoryBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val network: ExecutorService = Executors.newFixedThreadPool(NETWORK_THREADS) { r ->
        Thread(r, "herezi-image").apply { isDaemon = true }
    }
    private val diskLock = Any()

    /** 메모리에 있으면 바로 돌려준다(첫 프레임 깜빡임 방지). */
    fun cached(url: String, widthPx: Int, heightPx: Int): Bitmap? =
        memory.get(ImageKeys.memoryKey(url, widthPx, heightPx))

    /** [url] 을 [widthPx]×[heightPx] 이상으로 줄여 디코딩한다. 실패하면 null. 취소는 그대로 전파한다. */
    suspend fun load(url: String, widthPx: Int, heightPx: Int): Bitmap? {
        val key = ImageKeys.memoryKey(url, widthPx, heightPx)
        memory.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            val file = File(diskDir, ImageKeys.diskName(url))
            if (file.exists()) {
                file.setLastModified(System.currentTimeMillis())
            } else if (!download(url, file)) {
                return@withContext null
            }
            ensureActive()
            decode(file, ImageKeys.bucket(widthPx), ImageKeys.bucket(heightPx)) ?: run {
                file.delete() // 깨진 파일은 다음에 다시 받는다.
                null
            }
        }
        bitmap?.let { memory.put(key, it) }
        return bitmap
    }

    private suspend fun download(url: String, target: File): Boolean = suspendCancellableCoroutine { cont ->
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (@Suppress("SwallowedException") e: IOException) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            cont.resume(false) // http(s) 가 아닌 주소 등
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { connection.disconnect() }
        try {
            network.execute {
                if (!cont.isActive) return@execute
                val ok = runCatching { fetchTo(connection, target) }.getOrDefault(false)
                connection.disconnect()
                if (cont.isActive) cont.resume(ok)
            }
        } catch (e: RejectedExecutionException) {
            cont.resumeWithException(e)
        }
    }

    private fun fetchTo(connection: HttpURLConnection, target: File): Boolean {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        val acceptable = connection.responseCode in HTTP_OK_RANGE &&
            connection.contentLengthLong <= MAX_DOWNLOAD_BYTES
        if (!acceptable) return false
        diskDir.mkdirs()
        val tmp = File(diskDir, "${target.name}.${Thread.currentThread().id}.tmp")
        val complete = connection.inputStream.use { input -> tmp.outputStream().use { copyLimited(input, it) } }
        val moved = complete && (tmp.renameTo(target) || target.exists())
        tmp.delete()
        if (moved) trimDisk()
        return moved
    }

    /** [MAX_DOWNLOAD_BYTES] 를 넘으면 false (서버가 Content-Length 를 안 줄 때 대비). */
    private fun copyLimited(input: InputStream, output: OutputStream): Boolean {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        var read = input.read(buffer)
        while (read >= 0) {
            total += read
            if (total > MAX_DOWNLOAD_BYTES) return false
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }
        return true
    }

    private fun decode(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageKeys.sampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** 합계가 상한을 넘으면 오래 안 쓴(lastModified) 파일부터 상한의 80% 까지 지운다. */
    private fun trimDisk() = synchronized(diskLock) {
        val files = diskDir.listFiles { f -> f.isFile && !f.name.endsWith(".tmp") }?.toList().orEmpty()
        var total = files.sumOf { it.length() }
        if (total <= diskMaxBytes) return@synchronized
        val goal = diskMaxBytes / TRIM_DIVISOR * TRIM_KEEP
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= goal) break
            val size = f.length()
            if (f.delete()) total -= size
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val NETWORK_THREADS = 4
        private const val BUFFER_BYTES = 16 * 1024
        private const val MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024
        private const val DISK_MAX_BYTES = 30L * 1024 * 1024
        private const val TRIM_DIVISOR = 5
        private const val TRIM_KEEP = 4
        private const val MEMORY_FRACTION = 8
        private const val MEMORY_MAX_BYTES = 48 * 1024 * 1024
        private val HTTP_OK_RANGE = 200..299

        @Volatile
        private var instance: HereziImageLoader? = null

        /** 앱 전체에서 하나. 디스크 캐시는 `cacheDir/images`. */
        fun get(context: Context): HereziImageLoader = instance ?: synchronized(this) {
            instance ?: HereziImageLoader(File(context.applicationContext.cacheDir, "images")).also { instance = it }
        }

        private fun defaultMemoryBytes(): Int =
            minOf(Runtime.getRuntime().maxMemory() / MEMORY_FRACTION, MEMORY_MAX_BYTES.toLong()).toInt()
    }
}
