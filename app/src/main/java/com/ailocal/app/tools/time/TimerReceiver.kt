package com.ailocal.app.tools.time

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ailocal.app.AiLocalApplication
import com.ailocal.app.MainActivity
import com.ailocal.app.R

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("timer_id", 0L)
        val label = intent.getStringExtra("label").orEmpty().ifBlank { context.getString(R.string.actions_time_category) }

        TimerStore(context).remove(id)

        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context, id.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AiLocalApplication.CHANNEL_TIMERS)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.tool_result_done))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id.toInt(), notification)
    }
}
