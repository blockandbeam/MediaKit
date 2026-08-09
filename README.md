# MediaKit

## FFmpeg

`FFmpeg.resolve()` locates an ffmpeg binary, auto-downloading the latest build if none is found:

1. `-Dmediakit.ffmpeg=/path/to/ffmpeg` - explicit path
2. `ffmpeg` on PATH (skip system detection with `-Dmediakit.ffmpeg.detect=false`)
3. Latest download, stored in `config/mediakit/ffmpeg/` and checked for updates at most once per hour

## SoundCloud

`soundcloud.com` track URLs resolve to a temporary signed stream, then stream like any remote audio. The client id is scraped from SoundCloud's site and cached; override it with `-Dmediakit.soundcloud.client-id=...`.

## Licensing

Copyright (C) 2026 Block & Beam

This file is licensed under the GNU Lesser General Public License
version 3 or any later version.

The full license text is provided in `LICENSE.txt`
