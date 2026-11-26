# ps3mio v0.8 (test version)

First public test build of ps3mio for the PlayStation 3: the Stremio experience on the Movian engine.

## Files
- `ps3mio.pkg` - the PS3 package.
- `ps3mio_geohot.pkg` - a second package variant produced by the same build.

## What is in it
- Stremio-style interface: Board, Discover, Library, Search, Addons, Settings.
- Login with your Stremio account and your own addons.
- Torrent streaming with a 10 second buffer before playback starts and after every stall.
- Arabic and English text with joined Arabic letters and right-to-left order (subtitles and interface).
- Stremio-style player layout.

## Notes
- This is a test version, not a finished release.
- The NTFS driver (reading NTFS-formatted USB drives) is left out of this build. Streaming over the network is not affected.
- Built from the same sources as the Android test build. The first PS3 release has had very little testing on a real console, so please report problems in the Issues tab.
- The Android APK is not part of the releases. It is built by the "Engine - Android APK" workflow and found under Actions > Artifacts.
