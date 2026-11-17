# Dedicated Professional Notifications Screen

Replace the current cramped notification bottom sheet with a dedicated, professional full-screen Notifications page with polished M3 cards, severity indicators, clear-all and reset actions, and an empty state.

## User Review Required

> [!IMPORTANT]
> - **Dedicated Screen**: Moving notifications from a bottom sheet to a dedicated full-screen page (`NotificationsScreen`) accessible via the notification bell icon on the Dashboard.
> - **Professional Design**: Includes category/severity icons (warnings for exceeded budgets, info for active subscriptions), clear-all / dismiss actions, and reset cleared alerts.

## Proposed Changes

### Navigation & Routing
- #### [MODIFY] [AppScreens.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/navigation/AppScreens.kt)
  - Add `data object NotificationsRoute : NavKey`.

- #### [MODIFY] [MainActivity.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/MainActivity.kt)
  - Register `is NotificationsRoute` in navigation handler.
  - Wire navigation from Dashboard notification bell to `NotificationsRoute`.

### Notifications Screen Implementation
- #### [NEW] [NotificationsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/NotificationsScreen.kt)
  - Create a professional full-screen Material 3 `NotificationsScreen` with TopAppBar, active alerts list, severity indicators, quick dismiss, and "All caught up" empty state.

- #### [MODIFY] [DashboardScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/DashboardScreen.kt)
  - Update notification bell click listener to navigate to `NotificationsRoute` (and remove `NotificationTraySheet`).

## Verification Plan

### Automated Tests
- Gradle build verification: `app:assembleDebug`.

### Manual Verification
- Tap notification bell on Dashboard, verify smooth transition to dedicated Notifications screen.
- Verify active budget / subscription alerts display correctly with professional styling.
- Test dismissing individual alerts and clearing all.
