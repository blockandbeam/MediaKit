# MediaKit

## FFmpeg

`FFmpeg.resolve()` locates an ffmpeg binary, auto-downloading the latest build if none is found. Set an explicit path with `-Dmediakit.ffmpeg=/path/to/ffmpeg`, or fall back to `ffmpeg` on PATH (skip system detection with `-Dmediakit.ffmpeg.detect=false`); downloads are stored in the instance's `mediakit/ffmpeg/` folder and checked for updates at most once per hour.

## SoundCloud

`soundcloud.com` track URLs resolve to a temporary signed stream, then stream like any remote audio. The client id is scraped from SoundCloud's site and cached; override it with `-Dmediakit.soundcloud.client-id=...`.

## YouTube

`youtube.com` and `youtu.be` video URLs resolve to a direct audio stream via [yt-dlp](https://github.com/yt-dlp/yt-dlp). The binary is located the same way as ffmpeg: set `-Dmediakit.ytdlp=/path/to/yt-dlp` for an explicit path, or use `yt-dlp` on PATH (skip system detection with `-Dmediakit.ytdlp.detect=false`); downloads are stored in the instance's `mediakit/ytdlp/` folder and checked for updates at most once per hour.

## Licensing

Copyright (C) 2026 Block & Beam

This file is licensed under the GNU Lesser General Public License
version 3 or any later version.

The full license text is provided in `LICENSE.txt`
