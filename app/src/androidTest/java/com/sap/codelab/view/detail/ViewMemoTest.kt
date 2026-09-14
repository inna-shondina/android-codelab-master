package com.sap.codelab.view.detail

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sap.codelab.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewMemoTest {

    @Test
    fun closeAction_finishesDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ViewMemo::class.java)
            .putExtra(BUNDLE_MEMO_ID, -1L)

        ActivityScenario.launch<ViewMemo>(intent).use { scenario ->
            lateinit var activity: ViewMemo
            scenario.onActivity { activity = it }

            onView(withContentDescription(R.string.close_memo_details)).perform(click())

            assertTrue(activity.isFinishing)
        }
    }
}
