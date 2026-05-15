# VibeCraftMod

VibeCraftMod is a Fabric client mod for Minecraft 1.21.4 that renders the VibeCraft chat UI and HUD overlays, receives UI schema events from server plugins, and sends player input/settings back to the server.

This project is client-only.

## Current Status

- Actively used for dynamic plugin-driven screens (including EnchantForge catalog UI).
- Safe for regular client use with matching server plugins.
- Includes one documented gap in the generic overlay framework (see Known Gaps).

## Requirements

- Minecraft: 1.21.4
- Java: 21
- Fabric Loader: 0.16.9 or newer
- Fabric API: 0.112.0+1.21.4 (or compatible)

Version source:
- gradle.properties
- src/main/resources/fabric.mod.json

## Install (Player)

1. Install Fabric Loader for Minecraft 1.21.4.
2. Install Fabric API for 1.21.4.
3. Place VibeCraftMod-1.0.0.jar into your Minecraft mods folder.
4. Launch the Fabric profile.
5. Join a server that has the VibeCraft/EnchantForge plugin side configured.

Windows mods path:
- %APPDATA%\\.minecraft\\mods

### Player Setup Commands (Windows PowerShell)

Run this in a normal PowerShell window on the player's PC.

```powershell
$ErrorActionPreference = "Stop"

# Versions
$MinecraftVersion = "1.21.4"
$LoaderVersion = "0.16.9"
$FabricApiVersion = "0.112.0+1.21.4"

# Paths
$McDir = Join-Path $env:APPDATA ".minecraft"
$ModsDir = Join-Path $McDir "mods"
New-Item -ItemType Directory -Path $ModsDir -Force | Out-Null

# Ensure Java exists (Fabric installer requires Java)
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Write-Host "Java not found. Installing Temurin 21..."
  winget install EclipseAdoptium.Temurin.21.JDK --accept-package-agreements --accept-source-agreements
}

# Download and run Fabric installer (client profile)
$FabricInstaller = Join-Path $env:TEMP "fabric-installer.jar"
Invoke-WebRequest "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar" -OutFile $FabricInstaller
java -jar $FabricInstaller client -dir $McDir -mcversion $MinecraftVersion -loader $LoaderVersion

# Install Fabric API
$FabricApiJar = "fabric-api-$FabricApiVersion.jar"
$FabricApiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FabricApiVersion/$FabricApiJar"
Invoke-WebRequest $FabricApiUrl -OutFile (Join-Path $ModsDir $FabricApiJar)

# Copy VibeCraftMod jar into mods folder
# Update the source path if your jar is in a different location.
$VibeCraftModSource = "C:\\Temp\\VibeCraftMod-1.0.0.jar"
Copy-Item $VibeCraftModSource (Join-Path $ModsDir "VibeCraftMod-1.0.0.jar") -Force

# Verify installed mod jars
Get-ChildItem $ModsDir | Where-Object { $_.Name -match "fabric-api|VibeCraftMod" } | Select-Object Name, Length, LastWriteTime
```

### Launch And Join Commands (Windows)

```powershell
# Launch Minecraft Launcher (default install path)
Start-Process "$env:LOCALAPPDATA\Programs\Minecraft Launcher\MinecraftLauncher.exe"

# After launching, choose the Fabric profile for 1.21.4 and join the LAN server:
# 192.168.1.12:25565
```

### Client Log Check Commands

```powershell
$ClientLog = Join-Path $env:APPDATA ".minecraft\logs\latest.log"
Select-String -Path $ClientLog -Pattern "VibeCraftMod|open_screen|ui_schema|handleEvent" | Select-Object -Last 80
```

## Build From Source

From the VibeCraftMod folder:

- gradlew.bat build

Build output:
- build/libs/VibeCraftMod-1.0.0.jar

## Local Deploy (Windows)

- Copy-Item .\\build\\libs\\VibeCraftMod-1.0.0.jar "$env:APPDATA\\.minecraft\\mods\\VibeCraftMod-1.0.0.jar" -Force

## LAN Sharing (Same House)

If another player (for example your brother) needs the same UI features:

1. Give them the same VibeCraftMod jar.
2. Ensure they also use Fabric + Fabric API on Minecraft 1.21.4.
3. Keep their mod version synchronized with your current server/plugin-compatible build.

LAN direct connect target:

- 192.168.1.12:25565

## Features

- Dynamic screen rendering from server-sent schema.
- Plugin-scoped screens and actions.
- Client HUD chat rendering and tool status display.
- Plugin message networking for:
  - message
  - request_history
  - set_setting
  - clear_history
- Runtime debug flags:
  - schema events
  - packet/event flow
  - UI action execution

## Project Structure

- src/main/java/com/example/vibecraftmod
  - network: packet payloads and event handling
  - screen: dynamic UI screen rendering and actions
  - hud: active HUD rendering callbacks
  - ui: schema model and overlay framework classes
  - settings/config: persisted client and plugin-scoped settings
- src/main/resources/fabric.mod.json: Fabric mod metadata and entrypoint

## Known Gaps

The generic overlay framework under src/main/java/com/example/vibecraftmod/ui is scaffolded but not fully wired for production use yet.

Current gap details:

1. Overlay data binding is a stub.
- OverlayDataBinding.resolve currently returns null for bindings.

2. Generic overlay widgets are placeholders.
- SlotOverlayWidget
- BarOverlayWidget
- IconOverlayWidget
- TextOverlayWidget
Each class has TODO render logic.

3. OverlayManager render pass is not currently registered to a Fabric HUD callback.
- Schema reload can populate overlay definitions.
- Existing active HUD rendering uses dedicated overlays:
  - ClaudeHudOverlay
  - ArmorHudOverlay

Impact:
- Core dynamic screens and main chat/catalog workflows are functional.
- The generic overlay subsystem should be treated as experimental until fully implemented.

## Troubleshooting

If UI does not appear:

1. Confirm Fabric profile is used.
2. Confirm mod jar exists in mods folder.
3. Confirm Fabric API is installed.
4. Confirm server plugin side is running and sending schema/events.
5. Check client latest.log for VibeCraftMod open_screen, ui_schema, and related event logs.

If UI loops or reopens repeatedly:

- Ensure you are on the latest build containing duplicate open_screen guard logic.

## Development Notes

- This is a standalone repository intended to evolve independently from server/plugin repos.
- Keep release notes tied to protocol/schema changes so client and server stay compatible.
