# WateR: Smart Hydration Reminder

WateR is a modern, engaging Android application designed to help users track their daily water intake and stay hydrated. Instead of standard notifications, WateR utilizes Android's `KeyguardManager` to deliver lock-screen notifications or a custom Lottie overlay (featuring Pacman!) when the phone is unlocked.

<div align="center">
  <!-- Add your screenshot links here later -->
  <img src="images/home_page.jpg" width="250"/>
  <img src="images/streak_page.jpg" width="250"/>
</div>

## Features
*   **Smart Reminders:** Context-aware alerts based on phone lock state.
*   **Daily Tracking:** Log water intake with customizable preset amounts (e.g., +250ml) or custom inputs via a clean Room database.
*   **Modern UI:** Fully customized Material Design 3 interface with edge-to-edge layout support.
*   **Background Reliability:** Leverages `AlarmManager` for precise interval timing.

## Tech Stack
*   **Language:** Kotlin
*   **Database:** Room (SQLite) with destructive migration support
*   **Animations:** Lottie by Airbnb
*   **UI/UX:** Material Design 3, XML Layouts

## Running the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/XDecoder12/Water-Reminder.git
   ```
2. Open the project in **Android Studio**.
3. Sync the project with Gradle files.
4. Run on an emulator or physical device (API 24+).

> **Note for MIUI/HyperOS Users:** If testing on Xiaomi devices, ensure you manually grant "Display pop-up windows while running in the background" in the app's permission settings to allow the overlay service to run.

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
