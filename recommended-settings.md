# BGMI Engine Pro — Recommended Settings

## Device: iQOO I2304 (Snapdragon 8 Gen 2, Android 16)

### Thermal Thresholds
| Setting | Value | Why |
|---------|-------|-----|
| Warning (BALANCED) | **43°C** | Skin temp peaks at 42.9°C during TDM |
| Emergency (SAFE) | **48°C** | 5°C headroom before aggressive throttle |

### Game Optimizations
| Setting | Value | Why |
|---------|-------|-----|
| DNS Optimizer | **ON → Cloudflare** | Lowest latency DNS, ping was 6-53ms |
| Touch Boost | **ON** | Pointer speed 7, better aim response |
| GPU Performance | **ON** | Disables adaptive battery throttling |
| Pre-Game Clean | **ON** | Trims RAM before BGMI launches |

### General
| Setting | Value | Why |
|---------|-------|-----|
| Auto DND | **ON** | No notifications during gameplay |
| Voice Alerts | **OFF** | Distracting during competitive play |
| Drop Brightness | **ON** | Auto-dims on emergency to cool faster |
| Battery Estimator | **ON** | Shows remaining game time |
| Kill All BG | **ON** | Aggressive cleanup on emergency |

### Main Screen
| Setting | Value | Why |
|---------|-------|-----|
| Manual Override | **OFF** | Auto mode is stable now — 0 mode switches in TDM data |

### Temperature Sensor
- Using `tz_game_shell` (Vivo game-specific skin sensor)
- Normal idle: 35-37°C
- During gameplay: 37-43°C
- This is SKIN temp, not chip temp

### Verified Working Commands
- Refresh Rate: `settings put global vivo_screen_refresh_rate_mode 120`
- Battery Saver: `settings put global low_power 0`
- DNS: `settings put global private_dns_specifier one.one.one.one`
- DND: `settings put global zen_mode 2`
- Touch: `settings put system pointer_speed 7`
- Adaptive Battery: `settings put global adaptive_battery_management_enabled 0`
