package com.sap.codelab.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.sap.codelab.repository.App
import kotlinx.coroutines.launch

internal class ProximityAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val memoId = intent.getLongExtra(EXTRA_MEMO_ID, -1L)
        if (memoId < 0) return
        val isEntering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        val pendingResult = goAsync()
        val container = (context.applicationContext as App).container
        container.applicationScope.launch {
            try {
                runCatching {
                    container.reminderManager.onProximityEvent(memoId, isEntering)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
