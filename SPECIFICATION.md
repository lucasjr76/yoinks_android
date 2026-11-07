# Yoinks Android
## Functional and technical specification · v0.4.0

**English** · [Português](ESPECIFICACAO.md)

### Goal

Download media from a link shared to or pasted into Android. The user picks
MP4 video or MP3 audio. The file shows up in the file manager under
**Downloads/Yoinks**. All processing happens on the device.

### Functional requirements

| ID | Requirement | Expected behavior |
| --- | --- | --- |
| FR-01 | Manual and clipboard input | The field accepts typing and standard paste. **Colar link** (Paste link) fills the field with the first HTTP or HTTPS URL found in the text. |
| FR-02 | Android sharing | `ACTION_SEND` with MIME `text/plain` opens the app or forwards the link to the screen already open. The user picks the format to start. |
| FR-03 | Video | **Baixar vídeo MP4** (Download MP4 video) automatically picks H.264 MP4 video plus M4A audio when available, falling back to a combined MP4 stream. |
| FR-04 | Audio | **Baixar áudio MP3** (Download MP3 audio) downloads the best available audio track and converts it to MP3 with FFmpeg. |
| FR-05 | Download in progress | Shows preparation, progress when the engine reports it, and saving. Both download buttons stay disabled until the operation ends. |
| FR-06 | Final file | Publishes the media to Downloads/Yoinks and shows the name Android registered. |
| FR-07 | Errors | Flags an invalid link on the field. For engine or write errors, shows the diagnostic and allows a retry. |
| FR-08 | TikTok photo posts | For `tiktok.com/@…/photo/…` links, the video button becomes **Baixar fotos (JPG)** (Download photos) and saves every image of the post to Downloads/Yoinks. **Baixar áudio MP3** saves the post's music. |
| FR-09 | ⋮ menu | **Verificar atualizações** (Check for updates) looks up the latest GitHub release and only notifies when a newer version exists, linking to the release page. **Sobre** (About) shows the version, authorship ([lucasjr76](https://github.com/lucasjr76/)), the repository link and credit to the original Yoinks. |

### Interface

The screen has a dark background, mint-green accents and three areas for the
link, the format and the progress. The form scrolls to fit small screens, the
keyboard and large fonts. Download state is shown as text, and the progress
bar appears during the operation. The adaptive vector icon uses a download
arrow and includes a monochrome layer for launchers that support it. The UI
text is in Portuguese.

### Implementation

| Component | Implementation |
| --- | --- |
| App | Java 17, Activity and native XML layouts. Package `br.com.develox.yoinks`. |
| Android | Minimum API 29, target 34, compiled with SDK 35. The ARM64 APK is the phone test package. The Gradle project also includes x86_64. |
| Engine | `youtubedl-android 0.18.1` with yt-dlp 2026.10.26, bundled Python 3.12 runtime and FFmpeg. |
| Execution | One single-thread executor per screen. Downloads go to a temporary directory in the app cache. |
| Publishing | `MediaStore.Downloads`, with a pending entry during the copy. If publishing fails, the created entry is removed. |
| Permissions | `INTERNET` for downloading. Publishing through MediaStore needs no broad storage access. |

### Limits of this version

The app handles one download at a time, with the app open. Quality is picked
automatically. Site coverage depends on the bundled yt-dlp extractors and may
vary by platform. TikTok photos are read directly from the post page, not
through yt-dlp, and depend on that page's current structure. Short
`vm.tiktok.com` links to photo posts are not supported yet. In-app playback is
the next planned step.

### On-device acceptance criteria

1. See the new icon in the launcher and open the screen with readable controls.
2. Copy a valid link and tap **Colar link**. Check that the field is filled and
   waits for a format choice.
3. Share the link from the browser to **Yoinks Android**, then repeat with the
   app already open. Check that the field is filled in both cases.
4. Download MP4 and MP3 and open both files from **Downloads/Yoinks**.
5. Try to start with an empty field and with an unavailable URL. Check the
   error shown, then complete a valid download.
6. Use the screen in landscape and with a large font. Check that the buttons,
   the field and the download state remain reachable by scrolling.

### Build and licenses

See the [README](README.md) to build and install. This prototype's code is
GPL-3.0-only. This app is a derivative initiative of
[Yoinks by Pablo Stanley](https://github.com/pablostanley/yoinks), whose MIT
notice is in [`LICENSE-YOINKS`](LICENSE-YOINKS).
