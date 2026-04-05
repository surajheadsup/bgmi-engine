Build a production-ready Android application in Kotlin named "BGMI Engine Service".

GOAL:
A fully automatic background service (no UI required) that:
- Detects when BGMI (package: com.pubg.imobile) is running
- Applies performance optimizations using Shizuku API
- Runs continuously in background
- Restores system when game closes

IMPORTANT CONSTRAINTS:
- NO root
- NO Termux
- NO external scripts
- ONLY Android SDK + Shizuku API
- Must work fully on-device (no PC after install)

----------------------------------------
DEPENDENCIES
----------------------------------------

Use:
implementation "dev.rikka.shizuku:api:13.1.5"
implementation "dev.rikka.shizuku:provider:13.1.5"

----------------------------------------
PERMISSIONS
----------------------------------------

Add and handle:

- FOREGROUND_SERVICE
- RECEIVE_BOOT_COMPLETED
- PACKAGE_USAGE_STATS

Also:
- Guide user to enable Usage Access manually

----------------------------------------
ARCHITECTURE
----------------------------------------

Components:

1. EngineService (Foreground Service)
2. BootReceiver (auto start after reboot)
3. ShizukuManager (handles permission + execution)
4. GameDetector (detect BGMI running)

----------------------------------------
BGMI DETECTION
----------------------------------------

Use UsageStatsManager:

- Poll every 5 seconds
- Get current foreground app
- Detect package: com.pubg.imobile

----------------------------------------
SHIZUKU SETUP
----------------------------------------

- Check if Shizuku is running
- Request permission using Shizuku API
- Handle permission callback
- Only execute commands if permission granted

----------------------------------------
COMMAND EXECUTION
----------------------------------------

Create method:

runCommand(cmd: String)

Use:
Shizuku.newProcess(arrayOf("sh","-c",cmd), null, null)

Commands to run:

ON GAME START:
- cmd power set-fixed-performance-mode-enabled true
- settings put system peak_refresh_rate 120
- settings put system min_refresh_rate 120
- am kill-all

DURING GAME (every 2 min):
- am kill-all

THERMAL CHECK:
- read temperature from /sys/class/thermal/thermal_zone0/temp
- if >= 40°C:
    cmd power set-fixed-performance-mode-enabled false
- else:
    restore performance

ON GAME EXIT:
- cmd power set-fixed-performance-mode-enabled false
- settings delete system peak_refresh_rate
- settings delete system min_refresh_rate

----------------------------------------
ENGINE LOGIC
----------------------------------------

- Run loop every 5 seconds (detection)
- Run heavy tasks every 120 seconds
- Maintain:
    sessionTime
    thermalHits
    stabilizerRuns

----------------------------------------
FOREGROUND SERVICE
----------------------------------------

- Must run as foreground service
- Show minimal notification: "BGMI Engine Running"
- START_STICKY

----------------------------------------
BOOT RECEIVER
----------------------------------------

- Start service automatically after reboot

----------------------------------------
ERROR HANDLING
----------------------------------------

- Handle Shizuku not running
- Handle permission denied
- Prevent crashes

----------------------------------------
OUTPUT
----------------------------------------

Generate FULL Android Studio project:

- AndroidManifest.xml
- EngineService.kt
- BootReceiver.kt
- ShizukuManager.kt
- GameDetector.kt
- All required imports

Code must:
- Compile without errors
- Be clean and modular
- No pseudo code
- Production ready

Resume this session with:
claude --resume 90009e44-e466-4bb4-8a9a-632adf881c1b
claude --resume 90009e44-e466-4bb4-8a9a-632adf881c1b

claude --resume "bgmi-engine-ui-redesign"