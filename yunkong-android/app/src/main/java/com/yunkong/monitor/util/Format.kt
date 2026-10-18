package com.yunkong.monitor.util

import java.util.Locale

object Format {

    fun bytes(value: Double?, decimals: Int = 1): String {
        if (value == null || value.isNaN()) return "-"
        val v = kotlin.math.abs(value)
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var size = value
        var i = 0
        while (kotlin.math.abs(size) >= 1024.0 && i < units.size - 1) {
            size /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.${decimals}f %s", size, units[i])
    }

    fun speed(bytesPerSecond: Double?): String =
        if (bytesPerSecond == null) "-" else bytes(bytesPerSecond) + "/s"

    fun percent(value: Double?, decimals: Int = 0): String =
        if (value == null) "-" else String.format(Locale.US, "%.${decimals}f%%", value)

    fun duration(millis: Long): String {
        var s = millis / 1000
        val days = s / 86400
        s %= 86400
        val hours = s / 3600
        s %= 3600
        val minutes = s / 60
        return when {
            days > 0 -> "${days}天 ${hours}小时"
            hours > 0 -> "${hours}小时 ${minutes}分钟"
            else -> "${minutes}分钟"
        }
    }

    fun dateTime(millis: Long?): String {
        if (millis == null || millis <= 0) return "-"
        return java.text.SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(java.util.Date(millis))
    }

    fun timeHM(millis: Long): String {
        return java.text.SimpleDateFormat("HH:mm", Locale.CHINA).format(java.util.Date(millis))
    }

    /** 千分位流量：MB 输入展示为 GB 更直观 */
    fun mb(value: Double?): String {
        if (value == null || value < 0) return "-"
        return if (value >= 1024) String.format(Locale.US, "%.2f GB", value / 1024.0)
        else String.format(Locale.US, "%.0f MB", value)
    }
}
