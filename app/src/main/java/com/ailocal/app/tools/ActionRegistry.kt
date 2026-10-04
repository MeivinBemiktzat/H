package com.ailocal.app.tools

enum class ActionCategory { APPS, SYSTEM, MEDIA, TIME, DEVICE, FILES }

data class ActionDefinition(
    val id: String,
    val category: ActionCategory,
    val risk: RiskLevel,
    val displayNameHe: String,
    val displayNameEn: String
)

/**
 * Central registry of every supported action. The LLM's system prompt is
 * generated from this list (so prompt and implementation can't drift apart),
 * and the UI "Actions" tab lists these grouped by category.
 */
object ActionRegistry {

    val all: List<ActionDefinition> = listOf(
        // Apps
        ActionDefinition(ActionIds.OPEN_APP, ActionCategory.APPS, RiskLevel.SAFE, "פתיחת אפליקציה", "Open app"),
        ActionDefinition(ActionIds.OPEN_APP_BY_NAME, ActionCategory.APPS, RiskLevel.SAFE, "פתיחת אפליקציה לפי שם", "Open app by name"),
        ActionDefinition(ActionIds.LIST_APPS, ActionCategory.APPS, RiskLevel.SAFE, "רשימת אפליקציות מותקנות", "List installed apps"),
        ActionDefinition(ActionIds.OPEN_APP_INFO, ActionCategory.APPS, RiskLevel.SAFE, "פתיחת מידע על אפליקציה", "Open app info"),
        ActionDefinition(ActionIds.OPEN_APP_SETTINGS, ActionCategory.APPS, RiskLevel.SAFE, "פתיחת הגדרות אפליקציה", "Open app settings"),

        // System
        ActionDefinition(ActionIds.OPEN_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות", "Open settings"),
        ActionDefinition(ActionIds.OPEN_WIFI_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות Wi-Fi", "Open Wi-Fi settings"),
        ActionDefinition(ActionIds.OPEN_BLUETOOTH_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות Bluetooth", "Open Bluetooth settings"),
        ActionDefinition(ActionIds.OPEN_DISPLAY_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות תצוגה", "Open display settings"),
        ActionDefinition(ActionIds.OPEN_SOUND_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות קול", "Open sound settings"),
        ActionDefinition(ActionIds.OPEN_BATTERY_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות סוללה", "Open battery settings"),
        ActionDefinition(ActionIds.OPEN_STORAGE_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות אחסון", "Open storage settings"),
        ActionDefinition(ActionIds.OPEN_ACCESSIBILITY_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת הגדרות נגישות", "Open accessibility settings"),
        ActionDefinition(ActionIds.OPEN_PERMISSION_SETTINGS, ActionCategory.SYSTEM, RiskLevel.SAFE, "פתיחת מסך הרשאות", "Open permission screen"),

        // Media
        ActionDefinition(ActionIds.MEDIA_PLAY, ActionCategory.MEDIA, RiskLevel.SAFE, "הפעלת מדיה", "Play media"),
        ActionDefinition(ActionIds.MEDIA_PAUSE, ActionCategory.MEDIA, RiskLevel.SAFE, "השהיית מדיה", "Pause media"),
        ActionDefinition(ActionIds.MEDIA_STOP, ActionCategory.MEDIA, RiskLevel.SAFE, "עצירת מדיה", "Stop media"),
        ActionDefinition(ActionIds.MEDIA_NEXT, ActionCategory.MEDIA, RiskLevel.SAFE, "הבא", "Next track"),
        ActionDefinition(ActionIds.MEDIA_PREVIOUS, ActionCategory.MEDIA, RiskLevel.SAFE, "הקודם", "Previous track"),

        // Time
        ActionDefinition(ActionIds.CREATE_TIMER, ActionCategory.TIME, RiskLevel.SAFE, "יצירת טיימר", "Create timer"),
        ActionDefinition(ActionIds.CANCEL_TIMER, ActionCategory.TIME, RiskLevel.CONFIRM, "ביטול טיימר", "Cancel timer"),
        ActionDefinition(ActionIds.LIST_TIMERS, ActionCategory.TIME, RiskLevel.SAFE, "הצגת טיימרים", "List timers"),
        ActionDefinition(ActionIds.CREATE_ALARM, ActionCategory.TIME, RiskLevel.CONFIRM, "יצירת Alarm", "Create alarm"),
        ActionDefinition(ActionIds.CREATE_REMINDER, ActionCategory.TIME, RiskLevel.SAFE, "יצירת תזכורת", "Create reminder"),

        // Device info
        ActionDefinition(ActionIds.GET_TIME, ActionCategory.DEVICE, RiskLevel.SAFE, "שעה נוכחית", "Current time"),
        ActionDefinition(ActionIds.GET_DATE, ActionCategory.DEVICE, RiskLevel.SAFE, "תאריך נוכחי", "Current date"),
        ActionDefinition(ActionIds.GET_BATTERY, ActionCategory.DEVICE, RiskLevel.SAFE, "מצב סוללה", "Battery status"),
        ActionDefinition(ActionIds.GET_STORAGE, ActionCategory.DEVICE, RiskLevel.SAFE, "נפח אחסון", "Storage info"),
        ActionDefinition(ActionIds.GET_DEVICE_INFO, ActionCategory.DEVICE, RiskLevel.SAFE, "מידע על המכשיר", "Device info"),
        ActionDefinition(ActionIds.GET_ANDROID_VERSION, ActionCategory.DEVICE, RiskLevel.SAFE, "גרסת אנדרואיד", "Android version"),
        ActionDefinition(ActionIds.GET_APP_INFO, ActionCategory.DEVICE, RiskLevel.SAFE, "מידע על האפליקציה", "App info"),

        // Files
        ActionDefinition(ActionIds.OPEN_FILE_MANAGER, ActionCategory.FILES, RiskLevel.SAFE, "פתיחת מנהל קבצים", "Open file manager"),
        ActionDefinition(ActionIds.PICK_FILE, ActionCategory.FILES, RiskLevel.SAFE, "בחירת קובץ", "Pick a file"),
        ActionDefinition(ActionIds.PICK_FOLDER, ActionCategory.FILES, RiskLevel.SAFE, "בחירת תיקייה", "Pick a folder"),
        ActionDefinition(ActionIds.LIST_PICKED_FILES, ActionCategory.FILES, RiskLevel.SAFE, "הצגת קבצים שנבחרו", "List picked files"),
        ActionDefinition(ActionIds.OPEN_FILE, ActionCategory.FILES, RiskLevel.SAFE, "פתיחת קובץ", "Open a file"),
        ActionDefinition(ActionIds.CREATE_TEXT_FILE, ActionCategory.FILES, RiskLevel.SAFE, "יצירת קובץ טקסט", "Create text file"),
        ActionDefinition(ActionIds.DELETE_FILE, ActionCategory.FILES, RiskLevel.CONFIRM, "מחיקת קובץ", "Delete file"),
        ActionDefinition(ActionIds.SEARCH_FILES, ActionCategory.FILES, RiskLevel.SAFE, "חיפוש קבצים", "Search files"),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): ActionDefinition? = byId[id]

    fun riskOf(id: String): RiskLevel = byId[id]?.risk ?: RiskLevel.CONFIRM

    fun byCategory(category: ActionCategory): List<ActionDefinition> = all.filter { it.category == category }

    /**
     * Builds the tool list section of the system prompt in a compact form
     * small models can realistically follow.
     */
    fun buildToolPromptSection(): String {
        val sb = StringBuilder()
        sb.append("Available actions (use the exact action id):\n")
        all.forEach { sb.append("- ${it.id}\n") }
        return sb.toString()
    }
}
