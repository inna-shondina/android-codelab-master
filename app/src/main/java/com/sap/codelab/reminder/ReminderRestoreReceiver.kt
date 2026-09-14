package com.sap.codelab.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sap.codelab.repository.App
import kotlinx.coroutines.launch

internal class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pendingResult = goAsync()
        val container = (context.applicationContext as App).container
        container.applicationScope.launch {
            try {
                runCatching { container.reminderManager.restoreReminders() }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
