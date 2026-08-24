# VowTaker

VowTaker is a RuneLite plugin that turns progression into an oath-driven challenge system. Players unlock vow cards from milestone completions, review the draft pool, and then live under the restrictions of the selected vow until the run ends.

## Features

- Projected approval queue for draft vows before they become active.
- Permanent, god-aligned, and ritual vow tracking.
- Milestone-driven selection and review events.
- Persistent JSON state with RuneLite config integration.
- Overlay showing the active vow state, selected deity, and pending review queue.
- Conservative client-side enforcement for equipment, inventory, prayer, energy, and ritual checks.

## Build and test

From the project root:

```powershell
PowerShell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location 'D:\Unreal\Projects\RS Plugins\VowTaker'; C:\tools\gradle-8.10\bin\gradle.bat test"
```

Or for a full build:

```powershell
PowerShell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location 'D:\Unreal\Projects\RS Plugins\VowTaker'; C:\tools\gradle-8.10\bin\gradle.bat build"
```

The generated jar will appear under:

```text
D:\Unreal\Projects\RS Plugins\VowTaker\build\libs\
```

## RuneLite testing

VowTaker is packaged as a stock RuneLite external plugin. The jar contains the required `runelite-plugin.properties` entry point and is loaded via the **SideLoader** plugin (available on the RuneLite Plugin Hub) or via the RuneLite developer workflow.

### Option 1: SideLoader install (recommended)

1. Open RuneLite.
2. Open **Plugin Hub** and install the plugin named **SideLoader** (by Adam / official). Enable it.
3. Build this project:

   ```powershell
   PowerShell -NoProfile -ExecutionPolicy Bypass -Command "Set-Location 'D:\Unreal\Projects\RS Plugins\VowTaker'; C:\tools\gradle-8.10\bin\gradle.bat build -x checkstyleMain -x checkstyleTest"
   ```

4. Copy the produced jar into the SideLoader plugin folder:

   ```text
   %USERPROFILE%\.runelite\sideloaded-plugins\VowTaker-1.0.0.jar
   ```

   (On non-Windows systems the folder is `~/.runelite/sideloaded-plugins/`.)

5. Restart RuneLite. VowTaker will appear in the plugin list — enable it.
6. Verify the overlay is drawn in the top-left. Right-click it and choose **Take-vow** on any pending choice to activate a vow.

Persistent state is written to `%USERPROFILE%\.runelite\vowtaker\<accountName>_vow_state.json`. Deleting that file resets the account's approvals and completed vows.

### Option 2: RuneLite developer workflow

1. Clone the RuneLite source: `git clone https://github.com/runelite/runelite.git`.
2. Import it as a Maven project in your IDE.
3. Add this project as a **sibling Gradle module** (or install the jar into your local Maven cache).
4. Add a dependency from `runelite-client` (or your bootstrap project) onto this jar.
5. Run `net.runelite.client.RuneLite` from the IDE. VowTaker will auto-register through its `@PluginDescriptor` annotation once the jar is on the client classpath.

### In-game usage

- Right-click the VowTaker overlay to see the current pending vow choices. Left-click **Take-vow** on any listed vow to activate it immediately.
- Type `!vow help` in the chat (public chat, sent by your own character) for a command reference.
- Commands: `!vow status`, `!vow god <SARADOMIN|ZAMORAK|GUTHIX|ARMADYL|ZAROS>`, `!vow select <vowId>`, `!vow approve [vowId]`, `!vow decline [vowId]`.
- Chat commands only respond when sent by the logged-in local player.

## Quick validation checklist

- The overlay shows the selected God and active vow state.
- The draft review queue appears if draft vows are still pending.
- Only approved vows can be selected.
- Milestones trigger selection events without repeated duplicate spam.
- Enforced vows block disallowed equipment, resources, or ritual activity.

## Vow Philosophy

Vows are intentionally painful but not impossible. They reduce convenience or efficiency without permanently breaking the game, while still adding real decision pressure and roleplay flavor.
