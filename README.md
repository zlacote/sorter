# Sorter

Files a screenshot into the folder named after the app it came from.

```
DCIM/Screenshots/Screenshot_20261010_064921_Soul.png
        └──▶ Pictures/AppScreenshots/Soul/
```

Take a screenshot, take a screen recording, and a few seconds later it is in a
folder of its own. No tapping, no filing by hand.

## Where the name comes from

Samsung's shell writes the foreground application into the capture file name at
the moment the shot is taken:

```
Screenshot_20261010_064921_Soul.png
Screenshot_20261001_184114_Brawl Stars.png
Screen_Recording_20260928_220747_Brawl Stars.mp4
```

So the app that took the picture is already written down, by the one party that
knows it for certain. Sorter reads it out and creates the folder. Nothing is
guessed, no other app is watched, and no screen is ever read.

The split is anchored on the timestamp, which is always eight digits, an
underscore and six more. Whatever follows is the application name, so names
with spaces or underscores survive intact.

A capture with no application name — the lock screen, where the shell refuses to
name the app so it cannot leak — is left where it is. Guessing would be worse
than doing nothing.

## Why it is idle between screenshots

There is no broadcast for "a screenshot was taken". Android removed the old
ones, and the only supported detection API fires while *our own* window is on
screen, which is no use to a background app.

What Android does provide is a job that the media store itself wakes:

```kotlin
JobInfo.Builder(JOB_ID, SortJobService::class.java)
    .addTriggerContentUri(TriggerContentUri(imagesUri, FLAG_NOTIFY_FOR_DESCENDANTS))
    .addTriggerContentUri(TriggerContentUri(videoUri, FLAG_NOTIFY_FOR_DESCENDANTS))
    .setTriggerContentUpdateDelay(5_000)   // wait for the dust to settle
    .setTriggerContentMaxDelay(30_000)     // but never wait forever
```

No service, no notification, no polling loop. Between screenshots the process
does not exist. The five second delay is not a guess: the system restarts it on
every change, so five shots in a row cause one pass rather than five.

A 15 minute `WorkManager` pass sits underneath as a safety net, because there
are reports of the trigger going quiet on some devices after long sleep. It
finds nothing on a healthy phone and costs a few milliseconds. The screen shows
when the sorter last ran, so a trigger that stopped firing is visible instead of
silent.

## Not chasing its own tail

Moving a file announces a change, which wakes the sorter, which looks at the
file it just moved. Two independent barriers close that loop:

- a file already under the target root is never a candidate again;
- a file id already in the journal is refused even if it reappears in the source
  folder.

Either one alone would do. Keeping both means a bug in one cannot turn into an
infinite move.

## Layers

```
core/        pure Kotlin. Name parsing, folder naming, move decisions.
             No Android imports; this is where the unit tests live.
      ▼
data/        MediaStore reads and moves, SQLite journal, settings.
domain/      one pass: rescan, decide, move, record.
watch/       content trigger, safety net, re-arm after reboot.
ui/          Compose. A switch, permission buttons, history with undo.
```

Each pass works from a rescan of what is actually on disk, not from the list of
files a change notification mentioned. A notification lost to a killed process
costs nothing: the next pass sees the file anyway. Notifications decide *when* a
pass happens, never *what* it acts on.

## Permission

Screenshots belong to the shell that took them, so moving one needs "All files
access". It is granted once, by hand, in the system settings — which is why the
app shows a button that goes straight there rather than asking for anything at
runtime. After that, moves happen without a prompt.

## Verified, not assumed

`ci/emulator_run.sh` runs on an emulator and checks the real thing: a file
named like a real screenshot is indexed, sorted, and then found on disk in the
right folder with exactly one file in it and nothing left behind, through
repeated passes. A permission that did not take, a refused move, a loop or a
crash all fail the build.

The automation hook it drives exists only in the debug source set. The release
APK has no such entry point.

## Build

Needs JDK 21, Android SDK 35. No native code, so no NDK. There is no Gradle
wrapper checked in; CI installs Gradle 8.9.

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

## Limits

- **One UI by default.** The source folders default to `DCIM/Screenshots/` and
  `DCIM/Screen recordings/`, which is where a Samsung puts them. A stock device
  uses `Pictures/Screenshots/`. They are settings, not constants, but there is
  no screen for editing them yet.
- **Screenshots the shell wrote while another app was in front** carry that
  other app's name, which is the shell's call and not something Sorter can
  second-guess.
- **Samsung Gallery keeps its own index.** MediaStore is updated correctly by a
  move, but the gallery's own view may lag. Worth checking on the device.
- **No rename.** The file keeps its name. The app is already in the folder name,
  so stripping it would lose information for no gain.
