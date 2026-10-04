package com.ailocal.app.tools.impl

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.AlarmClock
import com.ailocal.app.tools.ToolResult
import com.ailocal.app.tools.time.AlarmReceiver
import com.ailocal.app.tools.time.TimerReceiver
import com.ailocal.app.tools.time.TimerStore

class TimeActions(private val context: Context) {

    private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    private val timerStore by lazy { TimerStore(context) }

    fun createTimer(seconds: Long?, label: String?): ToolResult {
        if (seconds == null || seconds <= 0) return ToolResult.Failure("Missing or invalid duration in seconds")

        val id = System.currentTimeMillis()
        val triggerAt = SystemClock.elapsedRealtime() + (seconds * 1000)

        val intent = Intent(context, TimerReceiver::class.java).apply {
            putExtra("timer_id", id)
            putExtra("label", label ?: "")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
            )
            timerStore.save(id, label ?: "Timer", System.currentTimeMillis() + seconds * 1000)
            ToolResult.Success("Timer set for ${seconds}s", mapOf("id" to id.toString()))
        } catch (e: SecurityException) {
            ToolResult.Failure("Exact alarm permission not granted")
        }
    }

    fun cancelTimer(idStr: String?): ToolResult {
        val id = idStr?.toLongOrNull() ?: return ToolResult.Failure("Missing timer id")
        val intent = Intent(context, TimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        timerStore.remove(id)
        return ToolResult.Success("Timer cancelled")
    }

    fun listTimers(): ToolResult {
        val timers = timerStore.getAll()
        if (timers.isEmpty()) return ToolResult.Success("No active timers", mapOf("count" to "0"))
        val summary = timers.joinToString("; ") { "${it.label} (#${it.id})" }
        return ToolResult.Success("Active timers: $summary", mapOf("count" to timers.size.toString()))
    }

    /** Uses the system Clock app's AlarmClock intent - the standard, user-visible way to set alarms. */
    fun createAlarm(hour: Int?, minute: Int?, label: String?): ToolResult {
        if (hour == null || minute == null) return ToolResult.Failure("Missing hour/minute")
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Opened alarm clock to set $hour:$minute")
        } catch (e: Exception) {
            ToolResult.Failure("No clock app available to set alarms")
        }
    }

    fun createReminder(text: String?, seconds: Long?): ToolResult {
        if (text.isNullOrBlank()) return ToolResult.Failure("Missing reminder text")
        val delaySeconds = seconds ?: 0L
        val id = System.currentTimeMillis()
        val triggerAt = SystemClock.elapsedRealtime() + (delaySeconds * 1000)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("reminder_text", text)
            putExtra("reminder_id", id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
            )
            ToolResult.Success("Reminder set: $text")
        } catch (e: SecurityException) {
            ToolResult.Failure("Exact alarm permission not granted")
        }
    }
}
