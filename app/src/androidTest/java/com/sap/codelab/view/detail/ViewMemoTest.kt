package com.sap.codelab.view.detail

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.util.HumanReadables
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sap.codelab.R
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.repository.App
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewMemoTest {

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val app by lazy { targetContext.applicationContext as App }
    private val createdMemoIds = mutableListOf<Long>()

    @After
    fun tearDown() = runBlocking {
        createdMemoIds.forEach { app.container.reminderManager.markDone(it) }
    }

    @Test
    fun closeAction_finishesDetails() {
        val intent = Intent(targetContext, ViewMemo::class.java)
            .putExtra(BUNDLE_MEMO_ID, -1L)

        ActivityScenario.launch<ViewMemo>(intent).use { scenario ->
            lateinit var activity: ViewMemo
            scenario.onActivity { activity = it }

            onView(withContentDescription(R.string.close_memo_details)).perform(click())

            assertTrue(activity.isFinishing)
        }
    }

    @Test
    fun newIntent_displaysMemoRequestedByLatestNotification(): Unit = runBlocking {
        val firstTitle = "First notification memo"
        val secondTitle = "Second notification memo"
        val firstId = insertMemo(firstTitle, ReminderStatus.INACTIVE)
        val secondId = insertMemo(secondTitle, ReminderStatus.ACTIVE)
        val firstIntent = Intent(targetContext, ViewMemo::class.java)
            .putExtra(BUNDLE_MEMO_ID, firstId)

        ActivityScenario.launch<ViewMemo>(firstIntent).use { scenario ->
            onView(isRoot()).perform(waitForText(firstTitle))

            scenario.onActivity { activity ->
                activity.startActivity(
                    Intent(activity, ViewMemo::class.java).apply {
                        putExtra(BUNDLE_MEMO_ID, secondId)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            }

            onView(isRoot()).perform(waitForText(secondTitle))
            scenario.onActivity { activity ->
                assertEquals(secondId, activity.intent.getLongExtra(BUNDLE_MEMO_ID, -1L))
            }
        }
    }

    private suspend fun insertMemo(title: String, status: ReminderStatus): Long {
        val id = app.container.memoRepository.insert(
            Memo(
                title = title,
                description = "$title description",
                reminderLatitude = 42.6977,
                reminderLongitude = 23.3219,
                reminderStatus = status
            )
        )
        createdMemoIds += id
        return id
    }

    private fun waitForText(expected: String): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()

        override fun getDescription(): String = "wait for text: $expected"

        override fun perform(uiController: UiController, view: View) {
            val deadline = SystemClock.uptimeMillis() + 10_000L
            while (SystemClock.uptimeMillis() < deadline) {
                val matchingViews = arrayListOf<View>()
                view.findViewsWithText(matchingViews, expected, View.FIND_VIEWS_WITH_TEXT)
                if (matchingViews.any(View::isShown)) return
                uiController.loopMainThreadForAtLeast(100L)
            }
            throw PerformException.Builder()
                .withActionDescription(description)
                .withViewDescription(HumanReadables.describe(view))
                .build()
        }
    }
}
