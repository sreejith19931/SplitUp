# UI Revamp Implementation Plan

Revamping the SplitUp UI to elevate the Neon-Midnight & Mint aesthetic, introducing glassmorphism, polished stat cards, interactive spending indicators, and micro-interactions across the dashboard and group screens.

## User Review Required

> [!IMPORTANT]
> The UI revamp will enhance visual appeal using Jetpack Compose and Material 3 design tokens (frosted glass cards, gradient headers, refined typography hierarchy, and interactive spending breakdown bars) without altering existing data logic or navigation flows.

## Proposed Changes

### Theme & Design Tokens
#### [MODIFY] [Color.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/theme/Color.kt)
- Add richer neon surface colors, glowing accent gradients, and translucent overlay colors for glassmorphism.

#### [MODIFY] [Theme.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/theme/Theme.kt)
- Fine-tune Material 3 color schemes (Dark/Light) to make the Neon-Midnight theme pop with high contrast and smooth dark mode styling.

---

### Common UI Components
#### [MODIFY] [CommonComponents.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/CommonComponents.kt)
- Enhance `StatCard` and other reusable widgets with glassmorphism styling, subtle gradient borders, and responsive typography.

---

### Dashboard & Spending Visualization
#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/DashboardScreen.kt)
- Upgrade the dashboard header banner with a vibrant neon gradient.
- Redesign spending summary cards and category budget breakdown bars with animated progress indicators and clean percentage indicators.

---

### Groups & Detail Screens
#### [MODIFY] [GroupsScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/GroupsScreen.kt)
- Refine group balance cards with clear net-owed color coding (green for positive, coral/red for negative) and improved spacing.

#### [MODIFY] [GroupDetailScreen.kt](file:///C:/Users/Sreejith/AndroidStudioProjects/SplitUp/app/src/main/java/com/finance/splitup/ui/screens/GroupDetailScreen.kt)
- Polish settlement breakdown and member chips with modern M3 surface styling.

---

## Verification Plan

### Automated Tests
- Run `./gradlew app:assembleDebug` to ensure all UI components compile successfully without errors.

### Manual Verification
- Deploy and test on emulator/device to inspect visual changes, contrast, and layout responsiveness.
