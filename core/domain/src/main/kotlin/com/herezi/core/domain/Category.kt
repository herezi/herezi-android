package com.herezi.core.domain

/**
 * 볼거리 카테고리 9종. API 계약(openapi.yaml `Category`)과 코드가 1:1로 같아야 한다.
 * 무료 여부는 카테고리가 아니라 필터(`SpotQuery.freeOnly`)다.
 */
enum class Category(val code: String) {
    NATURE("nature"),
    FESTIVAL("festival"),
    PERFORMANCE("performance"),
    EXHIBITION("exhibition"),
    POPUP("popup"),
    FOOD("food"),
    FAMILY("family"),
    PET("pet"),
    SPORTS("sports"),
    ;

    companion object {
        /** 모르는 코드는 null. 서버가 카테고리를 늘려도 앱이 깨지지 않게 한다. */
        fun fromCode(code: String): Category? = entries.firstOrNull { it.code == code }
    }
}
