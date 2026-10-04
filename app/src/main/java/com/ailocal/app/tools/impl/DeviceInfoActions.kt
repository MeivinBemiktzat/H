package com.ailocal.app.tools.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.ailocal.app.tools.ToolResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceInfoActions(private val context: Context) {

    fun getCurrentTime(): ToolResult {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return ToolResult.Success(fmt.format(Date()))
    }

    fun getCurrentDate(): ToolResult {
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        return ToolResult.Success(fmt.format(Date()))
    }

    fun getBatteryStatus(): ToolResult {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        if (level == -1 || scale == -1) return ToolResult.Failure("Could not read battery status")

        val percent = (level * 100 / scale.toFloat()).toInt()
        val chargingText = if (isCharging) "charging" else "not charging"
        return ToolResult.Success(
            "Battery: $percent% ($chargingText)",
            mapOf("percent" to percent.toString(), "charging" to isCharging.toString())
        )
    }

    fun getStorageInfo(): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val availableBytes = stat.availableBlocksLong * blockSize

        fun humanReadable(bytes: Long): String {
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            return String.format(Locale.getDefault(), "%.1f GB", gb)
        }

        return ToolResult.Success(
            "Storage: ${humanReadable(availableBytes)} free of ${humanReadable(totalBytes)}",
            mapOf("total" to totalBytes.toString(), "available" to availableBytes.toString())
        )
    }

    fun getDeviceInfo(): ToolResult {
        val info = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
        return ToolResult.Success(info, mapOf("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL))
    }

    fun getAndroidVersion(): ToolResult {
        val version = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        return ToolResult.Success(version, mapOf("sdk" to Build.VERSION.SDK_INT.toString()))
    }

    fun getAppInfo(): ToolResult {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = pInfo.versionName ?: "unknown"
            ToolResult.Success("AI Local v$versionName", mapOf("version" to versionName))
        } catch (e: Exception) {
            ToolResult.Failure("Could not read app info")
        }
    }
}
