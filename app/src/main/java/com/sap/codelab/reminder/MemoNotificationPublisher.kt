package com.sap.codelab.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.sap.codelab.R
import com.sap.codelab.model.Memo
import com.sap.codelab.view.detail.BUNDLE_MEMO_ID
import com.sap.codelab.view.detail.ViewMemo

internal const val NOTIFICATION_PREVIEW_LENGTH = 140
internal const val REMINDER_CHANNEL_ID = "location_reminders"

internal interface MemoNotificationPublisher {
    fun publish(memo: Memo): Boolean
}

internal class AndroidMemoNotificationPublisher(
    context: Context
) : MemoNotificationPublisher {

    private val context = context.applicationContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        createNotificationChannel()
    }

    override fun publish(memo: Memo): Boolean {
        if (!canPostNotifications()) return false
        val preview = memo.description.takeCodePoints(NOTIFICATION_PREVIEW_LENGTH)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_notification)
            .setContentTitle(memo.title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setContentIntent(contentPendingIntent(memo.id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(memo.id.stableNotificationId(), notification)
        return true
    }

    private fun canPostNotifications(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(REMINDER_CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun contentPendingIntent(memoId: Long): PendingIntent {
        val intent = Intent(context, ViewMemo::class.java).apply {
            data = "codelab://memo/$memoId".toUri()
            putExtra(BUNDLE_MEMO_ID, memoId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun Long.stableNotificationId(): Int = (this xor (this ushr 32)).toInt()
}

internal fun String.takeCodePoints(maxCodePoints: Int): String {
    require(maxCodePoints >= 0) { "maxCodePoints must be non-negative" }
    val codePointCount = codePointCount(0, length)
    if (codePointCount <= maxCodePoints) return this
    val endIndex = offsetByCodePoints(0, maxCodePoints)
    return substring(0, endIndex)
}
