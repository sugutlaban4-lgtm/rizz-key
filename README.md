# Rizz key

A small Android keyboard that puts three ready replies above the letters.
Two chips pick the stage and the tone; tap a line and it is typed into
whatever chat is open.

Everything is bundled in the APK: 84 written lines, no network permission,
no libraries. It works with the data off.

## Install

1. Download `rizz-key-0.1.apk` from the latest release.
2. Open the file. Android will warn that it came from outside the Play Store;
   allow it for your browser once.
3. Settings, System, Languages and input, On-screen keyboard, Manage keyboards,
   switch on **Rizz key**.
4. In any chat, hold the globe key and choose Rizz key. To go back to your old
   keyboard, hold the globe key again.

Debug-signed build, version 0.1. Built by GitHub Actions on every push.

## What is in it

- `RizzKeyboardService.java`: the whole keyboard. Suggestion row, letters,
  numbers, shift, backspace, space, enter.
- `assets/rizz.json`: the lines, grouped by stage and tone.
- No analytics, no uploads, nothing leaves the phone.

## Known limits

Never run on a physical phone by the author, so v0.1 is a start: expect the
occasional rough edge. Switching keyboards back takes two taps, so trying it
costs nothing.
