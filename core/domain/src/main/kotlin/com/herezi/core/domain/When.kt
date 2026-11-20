package com.herezi.core.domain

/** 기간 필터. API `when` 쿼리 값과 같다. */
enum class When(val code: String) {
    NOW("now"),
    TODAY("today"),
    WEEKEND("weekend"),
    WEEK("week"),
}
