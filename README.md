# Location Memo

An Android coding-challenge app that stores a memo with a point selected on a map and posts a one-shot notification when the device enters a 200-metre radius around that point.

## Original requirements

The challenge asks for an Android implementation that:

- lets the user select a location on a map while creating a memo;
- persists the memo;
- posts a status-bar notification with an icon, the memo title, and the first 140 characters of its text when the device comes within 200 metres of the selected point;
- continues to work while the app is in the background or its process is not running;
- keeps third-party libraries behind replaceable application boundaries.

The brief does not define recurrence, permission-denial behaviour, initial creation inside the radius, or production map-provider requirements. The choices made for those cases are documented below.

## Build and run

- Open the project in Android Studio with JDK 21 and Android SDK 36 installed.
- Run the `app` configuration on an API 27+ device or emulator.
- No API key, billing account, or proprietary location service is required.

The map uses MapLibre Native with OpenStreetMap raster tiles. OpenStreetMap attribution is supplied through the map style, MapLibre identifies requests with the application package and version, tile prefetch is disabled, and the app does not provide offline/bulk downloads. The public endpoint is used only for normal interactive viewing in this non-commercial challenge; non-commercial use is not an exemption from the [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/). Before distributing the app, re-check that policy and configure a contactable identifier, use a provider with an SLA, or host the tiles.

## User flow

1. Create a memo and tap the map to select a location.
2. Save the memo. Title, description, and location are mandatory. The memo is committed to Room before Android opens any external permission UI.
3. Grant precise location, background location, and notification permissions. Android 11+ opens the app settings for background access, because the system no longer offers “Allow all the time” in the normal runtime dialog.
4. Android registers a platform proximity alert with a 200-metre radius. If the memo is created inside that radius, it waits for the user to leave before becoming active.
5. On the first enter event after the reminder is active, the app posts a notification containing the memo title and the first 140 Unicode code points of its description, then completes and removes the reminder.
6. Selecting a memo opens its details. The standard Toolbar navigation action closes the details and returns to the list.

If a permission is declined, the memo remains saved with the `PERMISSION_REQUIRED` status and no proximity alert is scheduled. The saved memo ID, selected point, and creation stage are kept in `SavedStateHandle`, so process recreation while the user is in Settings resumes activation of the existing row. Interrupted transient stages are normalized instead of leaving Save disabled. `PENDING`, `PERMISSION_REQUIRED`, and `ERROR` reminders are reevaluated when the process starts or the home screen resumes. `WAITING_FOR_EXIT` and `ACTIVE` alerts are restored without changing their state. `TRIGGERED` and migrated `INACTIVE` memos are terminal and cannot be rearmed by a late permission callback.

## Architecture

- XML layouts, ViewBinding, Activities, ViewModels, StateFlow, and Room match the style of the starter project.
- The starter project's one-Activity-per-screen structure is retained to keep this challenge change focused. For a larger application, a single-Activity navigation architecture would also be reasonable.
- `SystemBarInsets` centralizes edge-to-edge handling: AppBars consume the top inset, while screen content and floating controls avoid side cutouts and the navigation bar.
- `AppContainer` is the composition root. It uses constructor injection and a small ViewModel factory instead of adding a DI framework for this object graph.
- `ReminderManager` owns the reminder state machine and coordinates persistence, scheduling, and notification delivery.
- Boot, app-update, and process-start restoration is delegated to unique WorkManager work, keeping long database/location operations outside `BroadcastReceiver` deadlines.
- `MemoRepository`, `ProximityReminderScheduler`, `MemoNotificationPublisher`, `ReminderPermissionChecker`, `CurrentLocationProvider`, and `LocationPicker` are boundaries around data and platform/third-party code.
- `MapLibreLocationPicker` is the only class coupled to MapLibre, so the map SDK and tile source can be replaced without changing Activities or ViewModels.
- `AndroidProximityReminderScheduler` wraps `LocationManager.addProximityAlert`; the feature therefore does not depend on Google Play services.
- Room stores coordinates as `Double`, exports its current schema, and includes tested migrations from the original v1 starter database and the intermediate v2 implementation schema.

Memos from the original v1 schema had placeholder `0/0` coordinates, not user-selected locations. The migration therefore preserves them as `INACTIVE` instead of creating false reminders in the Gulf of Guinea. The v2-to-v3 migration normalizes the Room table and preserves a reminder status when that column is present.

Proximity alerts are restored after process creation, device reboot, and app replacement. A restoration batch requests at most one current location fix and does so outside the reminder-operation mutex. Marking a memo done cancels its alert. Explicit `PendingIntent`s and unique URI identities prevent reminders from overwriting one another, while `ViewMemo.onNewIntent()` refreshes an already-open details screen for the newly selected notification.

## Assumptions to confirm with product

The original brief leaves several behaviours undefined. This implementation makes conservative challenge-sized choices:

- Reminders are one-shot, not recurring.
- A memo created inside its radius must observe an exit before a later enter can notify. If Android cannot provide a current fix within five seconds, the reminder is armed immediately so the first real arrival is not missed.
- A memo remains saved when permissions are denied, but its reminder stays inactive.
- Date/time scheduling, editing, deleting, snoozing, and a maximum reminder count are out of scope.
- “App not running” means the process may be killed normally. Android does not deliver alarms or boot receivers to a force-stopped app until the user launches it again.
- The public OpenStreetMap tile endpoint has no commercial SLA. Before a production release, product requirements must define expected traffic, offline behaviour, privacy, and the supported tile provider.

Those points should be explicit acceptance criteria in a commercial ticket. Another viable implementation would use Google Play services geofencing, but it introduces a Google service dependency and is not the best fit for the requested zero-cost/no-key solution.

## Platform limitations

`LocationManager` proximity alerts are intentionally approximate. Delivery can be delayed by Doze, device location settings, OEM background restrictions, or sparse location fixes. Reboot recovery occurs after credential-protected Room storage becomes available. Users must choose precise and “Allow all the time” location access for background delivery.

Relevant platform/provider documentation:

- [Android proximity alerts](https://developer.android.com/reference/android/location/LocationManager#addProximityAlert(double,double,float,long,android.app.PendingIntent))
- [Android background location](https://developer.android.com/develop/sensors-and-location/location/permissions/background)
- [Android notification permission](https://developer.android.com/about/versions/13/behavior-changes-all#notification-permission)
- [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/)

## Verification

Run the local checks with:

```shell
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

This builds both application and instrumentation-test APKs without requiring an emulator. The reminder coordinator, one-shot transition, batched restoration, permission fallback, process-safe creation state, Unicode-safe notification preview, and coordinate validation have local unit coverage.

### Details navigation test

With an emulator or device connected, verify that the Toolbar navigation action closes the memo details screen and that notification B replaces already-open memo A:

```shell
./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sap.codelab.view.detail.ViewMemoTest
```

### Database migration tests

The migration suite creates databases matching the original v1 and intermediate v2 tables, opens them through the production migration chain, and verifies that memo data survives in v3:

```shell
./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.sap.codelab.repository.DatabaseMigrationTest
```

### End-to-end route test

Start a dedicated Android Emulator running API 31 or newer, then run:

```shell
./scripts/run-e2e.sh
```

The suite creates memos through `Home -> CreateMemo`, selects a point in the real MapLibre view, and saves through the real Room and `LocationManager` implementations. It translates [`memo_arrival.gpx`](app/src/androidTest/assets/routes/memo_arrival.gpx) so its final point matches the UI-selected coordinates, backgrounds the activity, and replays the route through a temporary system GPS test provider. The two scenarios cover normal entry and creation inside the radius followed by exit and return. See the [reminder lifecycle documentation](docs/reminder-lifecycle.md) for the complete state machine.

The script intentionally runs only `LocationReminderE2ETest`; the details navigation test above is separate. If several emulators are running, select one explicitly, for example `ANDROID_SERIAL=emulator-5554 ./scripts/run-e2e.sh`.

The route test grants location permissions plus the notification runtime permission on API 33+, temporarily replaces the emulator GPS provider, and clears this app's notifications. Its `tearDown` restores the GPS provider and mock-location app-op. Run it on an emulator rather than a personal device. The route can also be loaded manually from **Emulator > Extended controls > Location > Routes > Load GPX/KML**.
