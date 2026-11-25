# v13 — visual card, icon, and interface refresh

This version is a **visual-only update based on the existing v12 project**. Core Java game rules, card costs/effects, server routes, and embedded game JavaScript interaction logic were not intentionally changed.

## Visual changes
- Replaced the artwork area inside the existing card frames with new, consistent cyberpunk illustrations for Spy, General, Disaster, Economic Crisis, and Sabotage. Card title, cost, frame, and rule text remain.
- Replaced resource icons for Energy, Industry, Army, and Defense with a unified neon-metallic badge set.
- Replaced the card back with a custom branded tactical circuitry design.
- Refreshed CSS colors, glow, panel materials, card selection states, buttons, and landscape layout styling.

## Validation performed
- Java compile: passed
- Core SelfTest: passed (1,046,916 assertions/checks, 0 failures)
- Embedded JS syntax: passed
- Map static checks: passed (12 sectors, IDs, polygons, interactions, assets)
- HTTP integration: passed (920 requests, 0 errors; 6 multiplayer/bot scenarios and reconnect/seat checks)
- Android APK: **not built** (Gradle executable/Android SDK are unavailable in this environment)
- Browser visual QA: **not performed** (Playwright dependency is unavailable)
