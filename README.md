# Location Memo

An Android coding-challenge app that stores a memo with a point selected on a map and posts a one-shot notification when the device enters a 200-metre radius around that point.

## Build and run

- Open the project in Android Studio with JDK 21 and Android SDK 36 installed.
- Run the `app` configuration on an API 27+ device or emulator.
- No API key, billing account, or proprietary location service is required.

The map uses MapLibre Native with OpenStreetMap raster tiles. OpenStreetMap attribution is supplied through the map style, tile prefetch is disabled, and the app does not provide offline/bulk downloads. The public tile service is suitable for this non-commercial challenge, but a production app should use a provider with an SLA or host its own tiles.

## User flow

1. Create a memo and tap the map to select a location.
2. Save the memo. Title, description, and location are mandatory.
3. Grant precise location, background location, and notification permissions. Android 11+ opens the app settings for background access, because the system no longer offers “Allow all the time” in the normal runtime dialog.
4. Android registers a platform proximity alert with a 200-metre radius. If the memo is created inside that radius, it waits for the user to leave before becoming active.
5. On the first enter event after the reminder is active, the app posts a notification containing the memo title and the first 140 Unicode code points of its description, then completes and removes the reminder.

If a permission is declined, the memo is still saved and clearly marked as needing permissions. Inactive reminders are retried when the app starts or the home screen resumes.

## Architecture

- XML layouts, ViewBinding, Activities, ViewModels, StateFlow, and Room match the style of the starter project.
- `AppContainer` is the composition root. It uses constructor injection and a small ViewModel factory instead of adding a DI framework for this object graph.
- `ReminderManager` owns the reminder state machine and coordinates persistence, scheduling, and notification delivery.
- `MemoRepository`, `ProximityReminderScheduler`, `MemoNotificationPublisher`, `ReminderPermissionChecker`, `CurrentLocationProvider`, and `LocationPicker` are boundaries around data and platform/third-party code.
- `MapLibreLocationPicker` is the only class coupled to MapLibre, so the map SDK and tile source can be replaced without changing Activities or ViewModels.
- `AndroidProximityReminderScheduler` wraps `LocationManager.addProximityAlert`; the feature therefore does not depend on Google Play services.
- Room stores coordinates as `Double` and includes migrations from both known starter schemas.

Memos from the starter schema had placeholder `0/0` coordinates, not user-selected locations. The migration therefore preserves them as `INACTIVE` instead of creating false reminders in the Gulf of Guinea.

Proximity alerts are restored after process creation, device reboot, and app replacement. Marking a memo done cancels its alert. Explicit `PendingIntent`s and unique URI identities prevent reminders from overwriting one another.

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
- [Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [OpenStreetMap tile usage policy](https://operations.osmfoundation.org/policies/tiles/)

## Verification

Run the local checks with:

```shell
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The reminder coordinator, one-shot transition, permission fallback, Unicode-safe notification preview, and coordinate validation have unit coverage.

### End-to-end route test

Start a dedicated Android Emulator running API 31 or newer, then run:

```shell
./scripts/run-e2e.sh
```

The suite creates memos through `Home -> CreateMemo`, selects a point in the real MapLibre view, and saves through the real Room and `LocationManager` implementations. It translates [`memo_arrival.gpx`](app/src/androidTest/assets/routes/memo_arrival.gpx) so its final point matches the UI-selected coordinates, backgrounds the activity, and replays the route through a temporary system GPS test provider. The two scenarios cover normal entry and creation inside the radius followed by exit and return. See the [reminder lifecycle documentation](docs/reminder-lifecycle.md) for the complete state machine.

The test grants location and notification permissions, temporarily replaces the emulator GPS provider, and clears this app's notifications. Its `tearDown` restores the GPS provider and mock-location app-op. Run it on an emulator rather than a personal device. The route can also be loaded manually from **Emulator > Extended controls > Location > Routes > Load GPX/KML**.
