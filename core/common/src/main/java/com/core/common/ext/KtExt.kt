package com.core.common.ext

import java.util.Locale

fun Int.toKString(): String {
    return if (this < 1000) {
        this.toString()
    } else {
        val kValue = this / 1000.0
        if (this % 1000 == 0) {
            // 整千直接取整（如 1000 -> "1k", 12000 -> "12k"）
            "${this / 1000}k"
        } else {
            // 非整千保留一位小数（如 12800 -> "12.8k"）
            String.format(Locale.CHINA, "%.1fk", kValue)
        }
    }
}