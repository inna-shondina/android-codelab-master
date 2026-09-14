package com.sap.codelab.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        ReminderRestoreWork.enqueue(context)
    }
}
