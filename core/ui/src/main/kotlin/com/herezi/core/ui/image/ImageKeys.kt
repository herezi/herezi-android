package com.herezi.core.ui.image

import java.security.MessageDigest

/**
 * 이미지 로더의 순수 계산(안드로이드 없음 → JVM 단위 테스트).
 * 메모리 캐시는 "주소 + 요청 크기" 단위, 디스크 캐시는 "주소" 단위(원본 바이트)로 둔다.
 */
object ImageKeys {
    /** 요청 크기를 이 배수로 올려 비슷한 크기 요청이 같은 메모리 항목을 쓰게 한다. */
    const val SIZE_STEP_PX = 64

    /** 크기를 모를 때(0 이하) 쓰는 상한. 지나치게 큰 원본을 그대로 올리지 않게 한다. */
    const val MAX_SIDE_PX = 2048

    /** [px] 를 [SIZE_STEP_PX] 배수로 올린다. 0 이하·무한대는 [MAX_SIDE_PX]. */
    fun bucket(px: Int): Int = when {
        px <= 0 -> MAX_SIDE_PX
        else -> minOf(MAX_SIDE_PX, (px + SIZE_STEP_PX - 1) / SIZE_STEP_PX * SIZE_STEP_PX)
    }

    /** 메모리 캐시 키. 같은 주소라도 요청 크기 구간이 다르면 다른 항목이다. */
    fun memoryKey(url: String, widthPx: Int, heightPx: Int): String = "${bucket(widthPx)}x${bucket(heightPx)}|$url"

    /** 디스크 파일 이름: 주소의 SHA-256 16진수(64자). 파일 이름에 쓸 수 없는 문자를 피한다. */
    fun diskName(url: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /**
     * BitmapFactory `inSampleSize`: 디코딩 결과가 요청 크기보다 작아지지 않는 가장 큰 2의 거듭제곱.
     * 원본이 요청보다 작거나 크기를 모르면 1.
     */
    fun sampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (minOf(minOf(srcWidth, srcHeight), minOf(reqWidth, reqHeight)) <= 0) return 1
        var sample = 1
        while (srcWidth / (sample * 2) >= reqWidth && srcHeight / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        return sample
    }
}
