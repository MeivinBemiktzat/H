package com.ailocal.app.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.ailocal.app.tools.ToolResult

class AppActions(private val context: Context) {

    fun openApp(packageName: String?): ToolResult {
        if (packageName.isNullOrBlank()) return ToolResult.Failure("Missing package name")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.Failure("App not installed: $packageName")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launchIntent)
            ToolResult.Success("Opened $packageName")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open app: ${e.message}")
        }
    }

    fun openAppByName(name: String?): ToolResult {
        if (name.isNullOrBlank()) return ToolResult.Failure("Missing app name")
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true)
        } ?: return ToolResult.Failure("No installed app matches \"$name\"")
        return openApp(match.packageName)
    }

    fun listInstalledApps(): ToolResult {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { pm.getApplicationLabel(it).toString() to it.packageName }
            .sortedBy { it.first }

        val summary = apps.take(50).joinToString(", ") { it.first }
        return ToolResult.Success(
            "Found ${apps.size} launchable apps",
            mapOf("apps" to summary, "count" to apps.size.toString())
        )
    }

    fun openAppInfo(packageName: String?): ToolResult {
        if (packageName.isNullOrBlank()) return ToolResult.Failure("Missing package name")
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened app info for $packageName")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open app info: ${e.message}")
        }
    }
}
