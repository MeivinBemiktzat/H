package com.ailocal.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ailocal.app.tools.ToolResult

/**
 * Android does not let third-party apps silently toggle Wi-Fi, Bluetooth,
 * etc. on modern versions. In every case here we open the matching system
 * settings screen rather than pretending to flip the setting ourselves -
 * exactly as the product spec requires.
 */
class SystemSettingsActions(private val context: Context) {

    private fun launch(action: String): ToolResult {
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ToolResult.Success("Opened settings screen", mapOf("note" to "opened_settings"))
        } catch (e: Exception) {
            ToolResult.Failure("Could not open settings: ${e.message}")
        }
    }

    fun openSettings() = launch(Settings.ACTION_SETTINGS)
    fun openWifiSettings() = launch(Settings.ACTION_WIFI_SETTINGS)
    fun openBluetoothSettings() = launch(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openDisplaySettings() = launch(Settings.ACTION_DISPLAY_SETTINGS)
    fun openSoundSettings() = launch(Settings.ACTION_SOUND_SETTINGS)
    fun openBatterySettings(): ToolResult {
        return try {
            val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened battery settings")
        } catch (e: Exception) {
            launch(Settings.ACTION_SETTINGS)
        }
    }
    fun openStorageSettings() = launch(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    fun openAccessibilitySettings() = launch(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openAppPermissionSettings(): ToolResult {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened permission settings")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open permission settings: ${e.message}")
        }
    }
}
