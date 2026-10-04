package com.ailocal.app.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The structured command the LLM emits when it decides an on-device action
 * is needed, e.g.:
 * {"type":"action","action":"open_app","parameters":{"package":"com.google.android.youtube"}}
 *
 * The model NEVER performs the action itself - it only requests one. The
 * [ToolExecutor] decides how (and whether) to actually carry it out.
 */
@Serializable
data class ToolCall(
    val type: String = "action",
    val action: String,
    val parameters: Map<String, JsonElement> = emptyMap()
) {
    fun stringParam(key: String): String? =
        (parameters[key] as? JsonPrimitive)?.contentOrNull()

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this.isString || !this.content.equals("null", ignoreCase = true)) content else null
}

/** Identifies every action the app knows how to run. Keep in sync with ActionRegistry. */
object ActionIds {
    // Apps
    const val OPEN_APP = "open_app"
    const val OPEN_APP_BY_NAME = "open_app_by_name"
    const val LIST_APPS = "list_installed_apps"
    const val OPEN_APP_INFO = "open_app_info"
    const val OPEN_APP_SETTINGS = "open_app_settings"

    // System settings
    const val OPEN_SETTINGS = "open_settings"
    const val OPEN_WIFI_SETTINGS = "open_wifi_settings"
    const val OPEN_BLUETOOTH_SETTINGS = "open_bluetooth_settings"
    const val OPEN_DISPLAY_SETTINGS = "open_display_settings"
    const val OPEN_SOUND_SETTINGS = "open_sound_settings"
    const val OPEN_BATTERY_SETTINGS = "open_battery_settings"
    const val OPEN_STORAGE_SETTINGS = "open_storage_settings"
    const val OPEN_ACCESSIBILITY_SETTINGS = "open_accessibility_settings"
    const val OPEN_PERMISSION_SETTINGS = "open_permission_settings"

    // Media
    const val MEDIA_PLAY = "media_play"
    const val MEDIA_PAUSE = "media_pause"
    const val MEDIA_STOP = "media_stop"
    const val MEDIA_NEXT = "media_next"
    const val MEDIA_PREVIOUS = "media_previous"

    // Time / notifications
    const val CREATE_TIMER = "create_timer"
    const val CANCEL_TIMER = "cancel_timer"
    const val LIST_TIMERS = "list_timers"
    const val CREATE_ALARM = "create_alarm"
    const val CREATE_REMINDER = "create_reminder"

    // Device info
    const val GET_TIME = "get_current_time"
    const val GET_DATE = "get_current_date"
    const val GET_BATTERY = "get_battery_status"
    const val GET_STORAGE = "get_storage_info"
    const val GET_DEVICE_INFO = "get_device_info"
    const val GET_ANDROID_VERSION = "get_android_version"
    const val GET_APP_INFO = "get_app_info"

    // Files
    const val OPEN_FILE_MANAGER = "open_file_manager"
    const val PICK_FILE = "pick_file"
    const val PICK_FOLDER = "pick_folder"
    const val LIST_PICKED_FILES = "list_picked_files"
    const val OPEN_FILE = "open_file"
    const val CREATE_TEXT_FILE = "create_text_file"
    const val DELETE_FILE = "delete_file"
    const val SEARCH_FILES = "search_files"
}

enum class RiskLevel { SAFE, CONFIRM }

/** Outcome returned to the chat after an action attempt. */
sealed class ToolResult {
    data class Success(val message: String, val data: Map<String, String> = emptyMap()) : ToolResult()
    data class Failure(val message: String) : ToolResult()
    data class NeedsConfirmation(val call: ToolCall, val promptMessage: String) : ToolResult()
    data class NotSupported(val reason: String) : ToolResult()
}
