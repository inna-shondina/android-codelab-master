package com.sap.codelab.reminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.core.net.toUri
import com.sap.codelab.model.Memo

internal const val PROXIMITY_RADIUS_METERS = 200f
internal const val EXTRA_MEMO_ID = "memoId"

internal interface ProximityReminderScheduler {
    fun schedule(memo: Memo): ScheduleResult
    fun cancel(memoId: Long)
}

internal sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data object PermissionRequired : ScheduleResult
    data class Failed(val cause: Throwable) : ScheduleResult
}

internal class AndroidProximityReminderScheduler(
    context: Context,
    private val permissionChecker: ReminderPermissionChecker
) : ProximityReminderScheduler {

    private val context = context.applicationContext
    private val locationManager = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override fun schedule(memo: Memo): ScheduleResult {
        if (!permissionChecker.hasForegroundLocationPermission() ||
            !permissionChecker.hasBackgroundLocationPermission()
        ) {
            return ScheduleResult.PermissionRequired
        }
        return runCatching {
            locationManager.addProximityAlert(
                memo.reminderLatitude,
                memo.reminderLongitude,
                PROXIMITY_RADIUS_METERS,
                -1L,
                proximityPendingIntent(memo.id)
            )
        }.fold(
            onSuccess = { ScheduleResult.Scheduled },
            onFailure = ScheduleResult::Failed
        )
    }

    override fun cancel(memoId: Long) {
        runCatching {
            locationManager.removeProximityAlert(proximityPendingIntent(memoId))
        }
    }

    private fun proximityPendingIntent(memoId: Long): PendingIntent {
        val intent = Intent(context, ProximityAlertReceiver::class.java).apply {
            action = ACTION_PROXIMITY_ALERT
            data = "codelab://proximity/$memoId".toUri()
            putExtra(EXTRA_MEMO_ID, memoId)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingIntentFlag()
        )
    }

    private fun mutablePendingIntentFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    private companion object {
        const val ACTION_PROXIMITY_ALERT = "com.sap.codelab.action.PROXIMITY_ALERT"
    }
}
