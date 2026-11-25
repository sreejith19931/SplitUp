# Multi-Device Compatibility & Visual Clarity Implementation Plan

This implementation plan outlines the steps to upgrade **SplitUp** for robust multi-device compatibility (phones, tablets, foldables, and desktop/ChromeOS) and superior visual clarity, adhering to Material 3 adaptive design guidelines.

## User Review Required

> [!IMPORTANT]
> This plan introduces adaptive navigation (`NavigationSuiteScaffold`) across top-level screens and extends multi-pane list-detail layouts to dashboard and personal expenses screens.

## Open Questions

- None. Material 3 adaptive libraries (`androidx.compose.material3.adaptive:adaptive-navigationsuite`, `adaptive-layout`) are already present in project dependencies.

## Proposed Changes

### App Navigation & Architecture

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/MainActivity.kt)
- Replace the manual `Scaffold` + `NavigationBar` setup with `NavigationSuiteScaffold` from `androidx.compose.material3.adaptive.navigationsuite`.
- Automatically adapt navigation items between bottom bar (phones), navigation rail (tablets), and navigation drawer (desktops).

### Multi-Pane Layouts

#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/DashboardScreen.kt)
- Integrate `ListDetailPaneScaffold` to display overview metrics / summary list alongside transaction details or quick action panels on wide tablet/foldable viewports.

#### [MODIFY] [PersonalExpensesScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/PersonalExpensesScreen.kt)
- Add list-detail scene adaptation for wide screens.

### Responsive Grids & Visual Clarity

#### [MODIFY] [GroupsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/GroupsScreen.kt) & Dashboard Cards
- Upgrade single-column list views and dashboard metric cards to use `LazyVerticalGrid` with `GridCells.Adaptive(minSize = 320.dp)` on wide screens.
- Ensure consistent `.navigationBarsPadding()` and `.imePadding()` across all modals and floating action buttons.

## Verification Plan

### Automated Tests
- Run existing UI tests and instrumented tests via Gradle (`app:connectedDebugAndroidTest` or unit tests).
- Build the project (`gradle_build("app:assembleDebug")`) to verify clean compilation.

### Manual Verification
- Deploy to phone and tablet emulators to verify bottom bar vs. navigation rail switching.
- Verify multi-pane layout switching on wide viewports.
