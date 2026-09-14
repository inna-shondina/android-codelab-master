package com.sap.codelab.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat

internal interface ReminderPermissionChecker {
    fun hasForegroundLocationPermission(): Boolean
    fun hasBackgroundLocationPermission(): Boolean
    fun hasNotificationPermission(): Boolean

    fun hasAllReminderPermissions(): Boolean =
        hasForegroundLocationPermission() &&
            hasBackgroundLocationPermission() &&
            hasNotificationPermission()
}

internal class AndroidReminderPermissionChecker(
    context: Context
) : ReminderPermissionChecker {

    private val context = context.applicationContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override fun hasForegroundLocationPermission(): Boolean =
        context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    override fun hasNotificationPermission(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(REMINDER_CHANNEL_ID)?.importance !=
            NotificationManager.IMPORTANCE_NONE

    private fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
