# BGMI Engine Pro

Smart Gaming Assistant for BGMI (Battlegrounds Mobile India). Optimizes device performance in real-time using Shizuku shell access — no root required.

## Requirements

- Android 8.0+ (API 26)
- [Shizuku](https://shizuku.rikka.app/) installed and running (via Wireless Debugging or ADB)
- Usage Access permission
- Overlay permission (for floating dashboard)

## Features

### Engine Core
- **Auto Game Detection** — detects BGMI via UsageStatsManager, starts optimizations automatically
- **3-Mode Thermal Engine** — EXTREME (120Hz) → BALANCED (90Hz) → SAFE (60Hz), switches based on skin temperature
- **Manual Override** — lock 120Hz + max performance regardless of temperature
- **Session Tracking** — records FPS, temperature, CPU, RAM, battery, thermal hits per session
- **Performance Scoring** — 0-100 score based on thermal state and CPU load

### Game Optimizations (applied on game start, restored on exit)
- **DNS Optimizer** — switches to Cloudflare / Google / AdGuard DNS for lower ping
- **Touch Boost** — increases pointer speed, tries device-specific touch rate paths (Samsung, Qualcomm, Xiaomi)
- **GPU Performance Mode** — disables battery saver + adaptive battery
- **Pre-Game Clean** — trims memory, drops filesystem caches, compacts RAM
- **Auto DND** — enables Do Not Disturb during gameplay
- **Drop Brightness on Emergency** — reduces to 40% during thermal emergency
- **Kill All BG on Emergency** — aggressive background app kill during thermal events

### Process Manager
- List all running processes with RAM usage
- Filter: ALL / USER / SYSTEM
- Search by package name
- Select and kill multiple processes
- Whitelist management — protect apps from being killed

### Kill Terminal
- Visual terminal showing kill progress
- Verifies each kill with `ps` (not `pidof` — handles subprocesses)
- `am force-stop` + `kill -9` + alarm/job/notification cancellation
- Shows: killed / persistent / not running counts

### System Apps Manager
- List all installed packages (USER / SYSTEM / DISABLED)
- **Bloatware Detection** — 60+ known bloatware packages flagged automatically
  - Vivo/iQOO, Samsung, Xiaomi, OnePlus, Google, Facebook, Microsoft preinstalls
  - Friendly names shown (e.g., "iQOO Store" instead of "com.iqoo.store")
- Disable / Enable / Uninstall (for current user) via Shizuku
- Auto-fallback: if disable fails, tries uninstall-for-user
- Clear "Protected by System" message for truly immovable apps

### Device Info
- Hardware: model, brand, board, hardware
- Android: version, API level, security patch, build number
- Processor: CPU name, cores, max frequency, GPU info
- Memory: total/available RAM, total/free storage
- Display: resolution, density, refresh rate
- Battery: level, health, temperature, voltage, technology
- Thermal: skin/shell temp, CPU/GPU temp, battery temp

### Floating Overlay (during gameplay)
- Collapsed bar: mode, FPS, temperature, battery, ping, brightness
- Expanded panel: CPU%, RAM%, session time, status
- Quick toggles: Voice ON/OFF, Auto/Override mode, Kill BG apps
- Draggable — move anywhere on screen
- Tap to expand/collapse

### Home Screen Widgets
- **Start Widget** — one-tap engine start (rocket icon)
- **Stop Widget** — one-tap engine stop (stop icon)
- **Kill Widget** — one-tap background app kill (skull icon)
- Notification feedback on each action (heads-up popup)

### Analytics Dashboard
- Total sessions, play time, avg FPS/temp/score
- Peak temperature, thermal hits, best session
- FPS bar chart (last 10 sessions)
- Performance score trend line chart

### Session History
- List all past sessions with date, duration, score, mode
- Session detail view with stats + 3 charts (temp, FPS, CPU/RAM over time)
- Session logs

### Settings
- **Performance Presets**
  - Competitive: 44°C/48°C thresholds, max everything, DND on
  - Balanced: 42°C/46°C thresholds, moderate settings
  - Battery Saver: 40°C/44°C thresholds, minimal boosts
- **Thermal Thresholds** — adjustable warning/emergency temperatures via sliders
- **Theme** — Dark / Light / AMOLED
- **Backup / Restore** — exports all settings, whitelist, theme, sessions to `Documents/bgmi_engine_backup/` (survives app uninstall)
- **Auto-restore prompt** on fresh install if backup file exists
- **Export** — CSV export of all sessions, share last session report
- **Command Diagnostics** — test Shizuku commands, run custom shell

### OTA Updates
- Auto-checks GitHub Releases on app open
- Shows changelog + APK size
- Download with progress → install
- Skip version option
- No server needed — uses GitHub Releases API

### Engine Logs
- Filter: ALL / THERMAL / MODE / KILL / ERROR / INFO
- Color-coded log types
- Last 500 entries

### Technical
- **Coroutines** — all background work uses `lifecycleScope.launch(Dispatchers.IO)`
- **Thread Safety** — `@Volatile` on shared state, `synchronized` on batteryHistory
- **Device Portability** — 12 thermal sensor types, multi-device touch/refresh rate/GPU paths
- **Theme-aware** — all colors use `?attr/` theme attributes (no hardcoded colors in main layouts)
- **ProGuard/R8** — release builds with minification, widget/receiver classes kept
- **Room Database** — sessions, stat snapshots, engine logs
- **Ping throttled** — network latency check every 60s (not every 2s)

## Build

```bash
./gradlew assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

## Device Support

Tested on iQOO (Vivo sub-brand, Snapdragon 8 Gen 2). Thermal sensors and device-specific paths cover:
- Qualcomm Snapdragon (Vivo, iQOO, OnePlus, Xiaomi)
- Samsung Exynos
- MediaTek
- Google Pixel

## License

Private project. Not for distribution.
