# Settings Page Redesign & Organization Implementation Plan

Restructure the Settings screen into a modular, categorized, and easy-to-navigate interface using Material 3 best practices, sub-routes, and clean `ListItem` components.

## Proposed Changes

### [Navigation & App Screens]
#### [MODIFY] [AppScreens.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/navigation/AppScreens.kt)
- Add `SecuritySettingsRoute` and `DataManagementRoute` for sub-settings pages.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/MainActivity.kt)
- Register navigation entries for `SecuritySettingsRoute` and `DataManagementRoute`.

### [UI / Screens]
#### [NEW] [SecuritySettingsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/SecuritySettingsScreen.kt)
- Houses Biometric Lock toggle, 4-digit PIN setup, Payment Handles (UPI, PayPal, Venmo, Revolut), and Payment Notification Detector.

#### [NEW] [DataManagementScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/DataManagementScreen.kt)
- Houses PDF/CSV Reports, JSON Backup/Restore, QA Testing Checklist, and Danger Zone (Clear Data, Delete Account).

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/SettingsScreen.kt)
- Streamline the main Settings screen into categorized cards using M3 `ListItem` components linking to profile edit, currency, theme, security settings, data management, subscriptions, about, and sign out.

## Verification Plan
- Build project and run unit tests (`app:testDebugUnitTest`).
- Deploy / test navigation flows across settings sub-screens.
