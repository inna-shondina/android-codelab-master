package com.sap.codelab.e2e

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import androidx.test.espresso.PerformException
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.sap.codelab.R
import com.sap.codelab.model.Memo
import com.sap.codelab.model.ReminderStatus
import com.sap.codelab.repository.App
import com.sap.codelab.view.home.Home
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream

private const val ROUTE_ASSET = "routes/memo_arrival.gpx"
private const val GPS_PROVIDER = "gps"
private const val SHELL_UID = 2000
private const val POLL_INTERVAL_MILLIS = 100L
private const val ROUTE_STEP_DELAY_MILLIS = 750L
private const val E2E_TIMEOUT_MILLIS = 20_000L

@RunWith(AndroidJUnit4::class)
@LargeTest
@SdkSuppress(minSdkVersion = 31)
class LocationReminderE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val app by lazy { targetContext.applicationContext as App }
    private val notificationManager by lazy {
        targetContext.getSystemService(NotificationManager::class.java)
    }
    private lateinit var route: List<RoutePoint>
    private var mockGpsInstalled = false
    private var createdMemoId: Long? = null
    private var originalShellMockLocationMode = "deny"

    @Before
    fun setUp() {
        route = loadRoute()
        require(route.size >= 2) { "The E2E route must contain at least two points" }

        grantReminderPermissions()
        notificationManager.cancelAll()
        shell("cmd location set-location-enabled true")
        originalShellMockLocationMode = readShellMockLocationMode()
        shell("appops set $SHELL_UID android:mock_location allow")
        shell("cmd location providers remove-test-provider $GPS_PROVIDER", allowFailure = true)
        shell(
            "cmd location providers add-test-provider $GPS_PROVIDER " +
                "--requiresSatellite --supportsAltitude --supportsSpeed --supportsBearing " +
                "--powerRequirement 3"
        )
        mockGpsInstalled = true
        shell("cmd location providers set-test-provider-enabled $GPS_PROVIDER true")
    }

    @After
    fun tearDown() {
        createdMemoId?.let { memoId ->
            runBlocking { app.container.reminderManager.markDone(memoId) }
        }
        notificationManager.cancelAll()
        if (mockGpsInstalled) {
            shell("cmd location providers remove-test-provider $GPS_PROVIDER", allowFailure = true)
        }
        shell(
            "appops set $SHELL_UID android:mock_location $originalShellMockLocationMode",
            allowFailure = true
        )
    }

    @Test
    fun arrivingAtMemoLocation_deliversOneShotNotification() {
        val title = "E2E route ${System.currentTimeMillis()}"
        val description = "Created through UI and delivered after GPX playback"

        ActivityScenario.launch(Home::class.java).use { scenario ->
            onView(withId(R.id.fab)).perform(click())
            onView(withId(R.id.memo_title)).perform(replaceText(title), closeSoftKeyboard())
            onView(withId(R.id.memo_description)).perform(
                replaceText(description),
                closeSoftKeyboard()
            )
            onView(withId(R.id.map_host)).perform(selectMapCenter())
            val playbackRoute = route.endingAt(readSelectedLocation())
            sendLocation(playbackRoute.first())
            onView(withId(R.id.action_save)).perform(click())

            val activeMemo = awaitMemo(title, ReminderStatus.ACTIVE)
            createdMemoId = activeMemo.id
            assertRouteEndsNearMemo(playbackRoute, activeMemo)
            onView(withText(title)).check(matches(isDisplayed()))
            onView(withText(R.string.reminder_status_active)).check(matches(isDisplayed()))

            scenario.moveToState(Lifecycle.State.CREATED)
            playbackRoute.drop(1).forEach { point ->
                sendLocation(point)
                SystemClock.sleep(ROUTE_STEP_DELAY_MILLIS)
            }

            awaitMemo(title, ReminderStatus.TRIGGERED)
            assertNotNull(awaitNotification(title))

            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(isRoot()).perform(waitForText(targetContext.getString(R.string.reminder_status_triggered)))
            onView(withText(R.string.reminder_status_triggered)).check(matches(isDisplayed()))
        }
    }

    private fun grantReminderPermissions() {
        val packageName = targetContext.packageName
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ).forEach { permission ->
            shell("pm grant $packageName $permission")
        }
    }

    private fun loadRoute(): List<RoutePoint> {
        val parser = android.util.Xml.newPullParser()
        instrumentation.context.assets.open(ROUTE_ASSET).use { input ->
            parser.setInput(input, Charsets.UTF_8.name())
            return buildList {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "trkpt") {
                        add(
                            RoutePoint(
                                latitude = parser.getAttributeValue(null, "lat").toDouble(),
                                longitude = parser.getAttributeValue(null, "lon").toDouble()
                            )
                        )
                    }
                    event = parser.next()
                }
            }
        }
    }

    private fun sendLocation(point: RoutePoint) {
        shell(
            "cmd location providers set-test-provider-location $GPS_PROVIDER " +
                "--location ${point.latitude},${point.longitude} --accuracy 5"
        )
    }

    private fun readSelectedLocation(): RoutePoint {
        var selectedLocation = ""
        onView(withId(R.id.selected_location)).check { view, error ->
            if (error != null) throw error
            selectedLocation = (view as TextView).text.toString()
        }
        val coordinates = Regex("[-+]?\\d+(?:[.,]\\d+)?")
            .findAll(selectedLocation)
            .map { match -> match.value.replace(',', '.').toDouble() }
            .toList()
        require(coordinates.size == 2) {
            "Could not parse the selected map location: $selectedLocation"
        }
        return RoutePoint(coordinates[0], coordinates[1])
    }

    private fun awaitMemo(title: String, status: ReminderStatus): Memo = runBlocking {
        withTimeout(E2E_TIMEOUT_MILLIS) {
            app.container.memoRepository.observeAll()
                .map { memos -> memos.firstOrNull { it.title == title && it.reminderStatus == status } }
                .first { it != null }
                ?: error("Memo was not found")
        }
    }

    private fun awaitNotification(title: String): Notification? = runBlocking {
        withTimeout(E2E_TIMEOUT_MILLIS) {
            while (true) {
                val notification = notificationManager.activeNotifications
                    .map { it.notification }
                    .firstOrNull {
                        it.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == title
                    }
                if (notification != null) return@withTimeout notification
                delay(POLL_INTERVAL_MILLIS)
            }
            null
        }
    }

    private fun assertRouteEndsNearMemo(route: List<RoutePoint>, memo: Memo) {
        val result = FloatArray(1)
        val destination = route.last()
        android.location.Location.distanceBetween(
            destination.latitude,
            destination.longitude,
            memo.reminderLatitude,
            memo.reminderLongitude,
            result
        )
        assertTrue(
            "The GPX route must end inside the reminder radius; distance=${result[0]}m",
            result[0] < 200f
        )
    }

    private fun selectMapCenter(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "wait for the map and select its centre"

        override fun perform(uiController: UiController, view: View) {
            val clickCenter = GeneralClickAction(
                Tap.SINGLE,
                GeneralLocation.CENTER,
                Press.FINGER,
                InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.BUTTON_PRIMARY
            )
            val deadline = SystemClock.uptimeMillis() + E2E_TIMEOUT_MILLIS
            while (SystemClock.uptimeMillis() < deadline) {
                clickCenter.perform(uiController, view)
                uiController.loopMainThreadForAtLeast(250L)
                val selectedLocation = view.rootView
                    .findViewById<TextView>(R.id.selected_location)
                    .text
                    .toString()
                if (selectedLocation != targetContext.getString(R.string.no_location_selected)) return
            }
            throw PerformException.Builder()
                .withActionDescription(description)
                .withViewDescription(HumanReadables.describe(view))
                .build()
        }
    }

    private fun waitForText(expected: String): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()

        override fun getDescription(): String = "wait for text: $expected"

        override fun perform(uiController: UiController, view: View) {
            val deadline = SystemClock.uptimeMillis() + E2E_TIMEOUT_MILLIS
            while (SystemClock.uptimeMillis() < deadline) {
                val matchingViews = arrayListOf<View>()
                view.findViewsWithText(
                    matchingViews,
                    expected,
                    View.FIND_VIEWS_WITH_TEXT
                )
                if (matchingViews.any(View::isShown)) return
                uiController.loopMainThreadForAtLeast(POLL_INTERVAL_MILLIS)
            }
            throw PerformException.Builder()
                .withActionDescription(description)
                .withViewDescription(HumanReadables.describe(view))
                .build()
        }
    }

    private fun shell(command: String, allowFailure: Boolean = false): String {
        val output = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
        if (!allowFailure) {
            assertFalse("Shell command failed: $command\n$output", output.contains("Exception"))
            assertFalse("Shell command failed: $command\n$output", output.contains("Error"))
        }
        return output
    }

    private fun readShellMockLocationMode(): String {
        val output = shell("appops get $SHELL_UID android:mock_location", allowFailure = true)
        return Regex("(?:MOCK_LOCATION|android:mock_location): (\\w+)")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?: Regex("Default mode: (\\w+)").find(output)?.groupValues?.get(1)
            ?: "deny"
    }
}

private data class RoutePoint(
    val latitude: Double,
    val longitude: Double
)

private fun List<RoutePoint>.endingAt(destination: RoutePoint): List<RoutePoint> {
    val sourceDestination = last()
    val latitudeOffset = destination.latitude - sourceDestination.latitude
    val longitudeOffset = destination.longitude - sourceDestination.longitude
    return map { point ->
        RoutePoint(
            latitude = point.latitude + latitudeOffset,
            longitude = point.longitude + longitudeOffset
        )
    }
}
