package com.ailocal.app.tools

import android.content.Context
import com.ailocal.app.tools.impl.AppActions
import com.ailocal.app.tools.impl.DeviceInfoActions
import com.ailocal.app.tools.impl.FileActions
import com.ailocal.app.tools.impl.MediaActions
import com.ailocal.app.tools.impl.SystemSettingsActions
import com.ailocal.app.tools.impl.TimeActions

/**
 * Single entry point that executes a [ToolCall] using real Android APIs.
 * Never simulates an outcome: if an action cannot be verified as performed,
 * it returns [ToolResult.Failure] or opens the relevant settings screen and
 * says so explicitly, rather than claiming success.
 */
class ToolExecutor(private val context: Context) {

    private val appActions = AppActions(context)
    private val systemSettings = SystemSettingsActions(context)
    private val mediaActions = MediaActions(context)
    private val timeActions = TimeActions(context)
    private val deviceInfo = DeviceInfoActions(context)
    private val fileActions = FileActions(context)

    /**
     * Executes immediately for SAFE actions. For CONFIRM-risk actions, the
     * caller must have already obtained user confirmation before invoking
     * this (see ChatViewModel flow) - this function does not re-check risk.
     */
    suspend fun execute(call: ToolCall): ToolResult {
        return try {
            when (call.action) {
                ActionIds.OPEN_APP -> appActions.openApp(call.stringParam("package"))
                ActionIds.OPEN_APP_BY_NAME -> appActions.openAppByName(call.stringParam("name"))
                ActionIds.LIST_APPS -> appActions.listInstalledApps()
                ActionIds.OPEN_APP_INFO -> appActions.openAppInfo(call.stringParam("package"))
                ActionIds.OPEN_APP_SETTINGS -> appActions.openAppInfo(call.stringParam("package"))

                ActionIds.OPEN_SETTINGS -> systemSettings.openSettings()
                ActionIds.OPEN_WIFI_SETTINGS -> systemSettings.openWifiSettings()
                ActionIds.OPEN_BLUETOOTH_SETTINGS -> systemSettings.openBluetoothSettings()
                ActionIds.OPEN_DISPLAY_SETTINGS -> systemSettings.openDisplaySettings()
                ActionIds.OPEN_SOUND_SETTINGS -> systemSettings.openSoundSettings()
                ActionIds.OPEN_BATTERY_SETTINGS -> systemSettings.openBatterySettings()
                ActionIds.OPEN_STORAGE_SETTINGS -> systemSettings.openStorageSettings()
                ActionIds.OPEN_ACCESSIBILITY_SETTINGS -> systemSettings.openAccessibilitySettings()
                ActionIds.OPEN_PERMISSION_SETTINGS -> systemSettings.openAppPermissionSettings()

                ActionIds.MEDIA_PLAY -> mediaActions.play()
                ActionIds.MEDIA_PAUSE -> mediaActions.pause()
                ActionIds.MEDIA_STOP -> mediaActions.stop()
                ActionIds.MEDIA_NEXT -> mediaActions.next()
                ActionIds.MEDIA_PREVIOUS -> mediaActions.previous()

                ActionIds.CREATE_TIMER -> timeActions.createTimer(call.stringParam("seconds")?.toLongOrNull(), call.stringParam("label"))
                ActionIds.CANCEL_TIMER -> timeActions.cancelTimer(call.stringParam("id"))
                ActionIds.LIST_TIMERS -> timeActions.listTimers()
                ActionIds.CREATE_ALARM -> timeActions.createAlarm(call.stringParam("hour")?.toIntOrNull(), call.stringParam("minute")?.toIntOrNull(), call.stringParam("label"))
                ActionIds.CREATE_REMINDER -> timeActions.createReminder(call.stringParam("text"), call.stringParam("seconds")?.toLongOrNull())

                ActionIds.GET_TIME -> deviceInfo.getCurrentTime()
                ActionIds.GET_DATE -> deviceInfo.getCurrentDate()
                ActionIds.GET_BATTERY -> deviceInfo.getBatteryStatus()
                ActionIds.GET_STORAGE -> deviceInfo.getStorageInfo()
                ActionIds.GET_DEVICE_INFO -> deviceInfo.getDeviceInfo()
                ActionIds.GET_ANDROID_VERSION -> deviceInfo.getAndroidVersion()
                ActionIds.GET_APP_INFO -> deviceInfo.getAppInfo()

                ActionIds.OPEN_FILE_MANAGER -> fileActions.openFileManager()
                ActionIds.PICK_FILE -> ToolResult.Failure("Use the UI file picker action, not a direct tool call.")
                ActionIds.PICK_FOLDER -> ToolResult.Failure("Use the UI folder picker action, not a direct tool call.")
                ActionIds.LIST_PICKED_FILES -> fileActions.listPickedFiles()
                ActionIds.OPEN_FILE -> fileActions.openFile(call.stringParam("uri"))
                ActionIds.CREATE_TEXT_FILE -> fileActions.createTextFile(call.stringParam("name"), call.stringParam("content"))
                ActionIds.DELETE_FILE -> fileActions.deleteFile(call.stringParam("uri"))
                ActionIds.SEARCH_FILES -> fileActions.searchFiles(call.stringParam("query"))

                else -> ToolResult.NotSupported("Unknown action: ${call.action}")
            }
        } catch (e: SecurityException) {
            ToolResult.Failure("Missing permission: ${e.message}")
        } catch (e: Exception) {
            ToolResult.Failure(e.message ?: "Unknown error")
        }
    }

    fun requiresConfirmation(call: ToolCall): Boolean =
        ActionRegistry.riskOf(call.action) == RiskLevel.CONFIRM
}
