# OBD Automation and Coolant Alerts Design

Date: 2026-06-03

## Goal

Add two safety-focused automation features to AndrOBD:

1. Safely auto-clear known, user-approved diagnostic trouble codes.
2. Warn and alarm when engine coolant temperature exceeds user-editable thresholds.

The app must avoid hiding new or unknown vehicle faults. Auto-clear is allowed only when every currently detected DTC is explicitly approved by the user.

## Current Project Context

AndrOBD is a Java Android app with these relevant areas:

- `androbd/src/main/java/com/fr3ts0n/ecu/gui/androbd/MainActivity.java`
  - Displays OBD data and fault codes.
  - Contains manual fault-code clearing in `clearObdFaultCodes()`.
- `library/src/main/java/com/fr3ts0n/ecu/prot/obd/ObdProt.java`
  - Parses OBD responses, including DTC responses.
  - Sends OBD services including read codes and clear codes.
- `androbd/src/main/java/com/fr3ts0n/ecu/gui/androbd/ObdBackgroundService.java`
  - Existing foreground service scaffold for background OBD monitoring.
- `androbd/src/main/res/xml/settings.xml`
  - Existing app settings UI based on Android preferences.
- `library/src/main/java/com/fr3ts0n/ecu/prot/obd/res/pids.csv`
  - Defines standard coolant temperature PID `engine_coolant_temperature`.

## Feature 1: Safe DTC Auto-Clear

### User Controls

Add an "Automation / Alerts" settings area with:

- Enable safe DTC auto-clear.
- Editable approved DTC list.
- Example input format: `P0420, P0441`.

Also add quick management from the fault-code screen:

- When a DTC is visible, the user can add it to the approved auto-clear list.
- If a visible DTC is already approved, the user can remove it from the approved list.

### Behavior

When DTCs are read:

- If safe auto-clear is disabled, keep current AndrOBD behavior.
- If no DTCs are present, do nothing.
- If all currently detected DTCs are in the approved list, automatically clear fault codes.
- If one or more detected DTCs are not approved, do not clear any code.
- For unknown DTCs, show a clear user-facing warning or notification so the user knows a real new fault may need inspection.

### Safety Rule

OBD clear-code service clears emission-related diagnostic information broadly. It does not safely clear one selected DTC while preserving others. Because of that, auto-clear must use an all-or-nothing decision:

```text
auto-clear allowed = detected_codes is not empty
                     and every detected code is in approved_codes
```

Any unknown code blocks auto-clear.

### Duplicate / Repeat Protection

Auto-clear should not fire repeatedly in a tight loop for the same code set. Add a cooldown after an auto-clear attempt. A practical initial cooldown is 60 seconds.

## Feature 2: Coolant Temperature Warning and Critical Alarm

### User Controls

Add settings for:

- Enable coolant temperature monitoring.
- Warning threshold in Celsius.
- Critical alarm threshold in Celsius.

Default thresholds:

- Warning: `100 C`
- Critical: `110 C`

The settings must be editable inside the app interface.

### Monitored Data

Monitor standard OBD coolant temperature:

- PID key: `engine_coolant_temperature`
- Standard OBD PID: service `0x01`, PID `0x05`

### Behavior

When coolant monitoring is enabled:

- If coolant temperature is below warning threshold, no alert is active.
- If coolant temperature is at or above warning threshold but below critical threshold, show a warning state.
- If coolant temperature is at or above critical threshold, trigger a loud critical alarm.

The critical alarm must be obvious and persistent until acknowledged.

### Permissions and Android Limits

The app should request the Android permissions and settings access needed for reliable alerts where supported:

- Notification permission on Android 13+.
- A high-importance notification channel for critical coolant alarms.
- Do Not Disturb policy access when the user wants the alarm to break through silent / DND modes.

Android may still restrict alarm behavior depending on OS version, user settings, and device manufacturer policy. The app should guide the user to the required Android settings when permission is missing.

### Alarm Acknowledgement

Critical alarm notifications should include an acknowledgement action or should stop when the user opens the app and acknowledges the alarm. Acknowledging one critical alarm should not permanently disable monitoring.

### Repeat Protection

Avoid repeatedly starting the alarm every time a new coolant reading arrives. Track alert state transitions:

- Normal to warning: show warning once.
- Warning to critical: start critical alarm once.
- Critical remains critical: keep alarm active.
- Critical to lower state: clear critical state when temperature drops below critical threshold.

## Implementation Approach

Use app-layer helpers rather than changing low-level OBD protocol parsing unless necessary.

Add focused components:

- `DtcAutomationSettings`
  - Reads and writes safe auto-clear preferences.
  - Parses approved DTC list.
  - Normalizes DTC codes to uppercase.
- `DtcAutoClearController`
  - Receives current detected DTC codes.
  - Decides whether safe auto-clear is allowed.
  - Enforces cooldown.
  - Requests clear-code service only when safe.
- `CoolantAlertSettings`
  - Reads and writes coolant monitoring preferences.
  - Validates thresholds.
- `CoolantAlertController`
  - Receives coolant temperature readings.
  - Tracks normal, warning, and critical states.
  - Requests warning or critical notifications.
- `VehicleAlertNotifier`
  - Creates notification channels.
  - Shows unknown-DTC, coolant-warning, and coolant-critical notifications.
  - Handles critical alarm notification behavior and acknowledgement.

Keep UI changes in the existing preference-based settings flow where practical.

## Data Flow

### DTC Auto-Clear

```text
OBD response -> ObdProt parses DTCs -> MainActivity/DTC adapter sees current codes
             -> DtcAutoClearController evaluates approved list
             -> if all detected codes are approved: set OBD_SVC_CLEAR_CODES
             -> else: notify unknown DTCs
```

### Coolant Alert

```text
OBD data update -> coolant PID value available in current data list
                -> CoolantAlertController evaluates thresholds
                -> VehicleAlertNotifier shows warning or critical alarm
```

## Validation

Automated tests should cover the pure decision logic:

- Approved-code parsing trims whitespace and uppercases codes.
- Auto-clear allows only non-empty detected code sets where all codes are approved.
- Auto-clear blocks mixed approved and unknown code sets.
- Coolant alert state changes at warning and critical thresholds.
- Coolant alert does not retrigger repeatedly while staying in the same state.

Manual verification should cover:

- App builds with `.\gradlew.bat :androbd:assembleDebug`.
- App installs and launches on emulator.
- Settings screen displays the new controls.
- Approved DTC list can be edited in settings.
- DTC quick add/remove updates the settings value.
- Coolant thresholds can be edited in-app.
- Notification permission and DND access prompts/routes are reachable.

## Out of Scope

- Clearing individual DTCs while preserving unknown DTCs.
- Engine oil temperature monitoring.
- Exhaust gas, DPF, or catalyst temperature alarms.
- Replacing the legacy preference UI with a new settings architecture.
- Building a full background-only monitoring system before the main app flow is proven.
