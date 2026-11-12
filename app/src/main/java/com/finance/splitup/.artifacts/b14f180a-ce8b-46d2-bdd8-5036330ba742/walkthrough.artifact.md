# Walkthrough - Optional Biometrics, Dynamic Color, Tip & Tax Calculator

All requested features and enhancements have been successfully implemented and verified.

## Summary of Completed Changes

### 1. Optional Biometric Security Lock in Settings
* **Files Modified**: [`FinanceViewModel.kt`](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/viewmodel/FinanceViewModel.kt), [`MainActivity.kt`](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/MainActivity.kt), [`SettingsScreen.kt`](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/SettingsScreen.kt)
* **Changes**:
  - Biometric lock is now **disabled by default** (`isBiometricLockEnabled = false`).
  - Added a toggle switch under Preferences in Settings (`"Biometric Security Lock"`).
  - `MainActivity` only displays the biometric lock screen if `isBiometricLockEnabled` is turned on by the user.

### 2. Material You Dynamic Color Support
* **File Modified**: [`Theme.kt`](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/theme/Theme.kt)
* **Changes**:
  - Enabled Material You dynamic color by default (`dynamicColor = true`) on Android 12+ devices (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`).

### 3. Interactive Tip & Tax Calculator
* **File Modified**: [`ReceiptAllocationScreen.kt`](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/ReceiptAllocationScreen.kt)
* **Changes**:
  - Added tip percentage filter chips (`0%`, `10%`, `15%`, `20%`) and an extra tax/fees input field in the cost allocation screen, automatically calculating the adjusted total.

---

## Verification Results

### Unit Tests
```
Task :app:testDebugUnitTest
12 passed, 0 skipped, 0 failed
```

### Application Build
```
BUILD SUCCESSFUL
```
