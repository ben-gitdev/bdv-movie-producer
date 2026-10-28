# BigDataViewer Movie Producer (Fiji plugin)

Keyframe-based movie making for BigDataViewer, packaged as a normal Fiji
plugin. Extracted from the fork
[PreibischLab/bigdataviewer-core-movie](https://github.com/PreibischLab/bigdataviewer-core-movie)
(original author Marwan Zouinkhi; easing curves from Stephan Saalfeld's
`hotknife.VNCMovie`). It no longer replaces Fiji's BigDataViewer — it adds
one small jar alongside it.

## What you get

* **F7** or **Tools › Produce Movie (keyframes)** in every BDV window
  (including windows opened by BigStitcher, Multiview Reconstruction, scripts).
  This is wired up automatically when a BDV window is created (the shortcut
  is also listed under BDV's Preferences › Keymap).
* Fallback command: **Plugins › BigDataViewer › Movie Producer (keyframes)**,
  which attaches to the most recently active BDV window.
* Add keyframes with **+**, set per-transition frame count and easing
  (symmetric / ease-in / ease-out and soft variants, see [Easing](#easing)),
  preview in the viewer, export / import keyframes as JSON (old JSON files
  from the fork still load), and render a PNG sequence at any resolution with
  optional scale bar and box overlay.

![Produce Movie (keyframes) in the BDV Tools menu](docs/images/tools-menu.png)

*The movie producer in BDV's Tools menu (shortcut F7).*

![The Produce Movie dialog with four keyframes](docs/images/produce-movie-dialog.png)

*The Produce Movie dialog: preview controls and what new keyframes capture
(timepoint, channels & display settings) on top, the keyframe strip with
per-transition frame count and easing, and export / import / record on the
right. "(t=0, ch)" in a keyframe's title means it stores a timepoint and
channel/display settings; right-click a keyframe to go to or update it.*

### Changes vs. the original fork

* Builds against current `bigdataviewer-core` (pom-scijava 44) instead of a
  2022 fork of it.
* Preview Stop no longer uses `Thread.stop/suspend/resume` (they throw on the
  Java 21 that current Fiji ships).
* Recording and import run on background threads, so the UI does not freeze;
  recording shows progress and can be cancelled.
* Recording renders from a snapshot of the viewer state, so the live window is
  not moved/resized during export.
* Keyframes remember the canvas size they were captured at, so resizing the
  BDV window or recording at a different resolution keeps the same framing.
* Optional: keyframes capture the current **timepoint**, and the timepoint is
  interpolated between keyframes (for time-lapse data). Untick
  "timepoint" under "New keyframes capture" for a purely spatial fly-through.
* Optional: keyframes capture **channels & display settings**: which
  sources/channels and groups are visible, the display mode, and each
  source's display range (brightness/contrast) and color.
  * Visibility switches when a keyframe is reached: during the transition
    into keyframe *B* you see keyframe *A*'s channels. To switch channels
    without moving, add two keyframes at the same view; the "Frames" value of
    the second is how long the first channel is held.
  * Display ranges and colors are interpolated between keyframes with the
    same easing as the camera (e.g. fade a channel in by ramping its maximum).
  * Right-click a keyframe to go to it, replace it with the current view, or
    update / clear only its channel & display settings. Hover a keyframe to
    see what it stores; "ch" in its title means display settings are stored.
  * Settings are matched to sources by index, so they apply to the same
    dataset only (a JSON with a different number of sources is ignored, with
    a warning). Keyframes without display settings leave the display as is.
  * While recording, BDV's live window shows the changing display ranges and
    colors (they are shared with the viewer); they are restored afterwards.
* Easing: "slow start" / "slow end" (and their soft variants) are replaced by
  ease-in / ease-out, and all curves use the shared easing length (see
  [Easing](#easing)). Fork JSON files still load, but transitions that used
  slow start / slow end play with ease-in / ease-out, and symmetric
  transitions longer than 2 × 60 frames get a constant-speed middle.

## Easing

Each keyframe's dropdown sets how the movie moves from the previous keyframe
into it; its "Frames" value (*F*) is the length of that transition. One
**Easing length** (*N*, in frames, at the top of the dialog) applies to all
keyframes: it is how long the movie takes to speed up or slow down.

| Mode | Motion during the transition | If the transition is short |
|---|---|---|
| symmetric | speeds up over the first *N* frames, constant speed, slows to a stop over the last *N* frames | *F* ≤ 2*N*: eases over the whole transition (cosine S-curve) |
| ease-in | speeds up over the first *N* frames, then constant speed into the keyframe | *F* ≤ *N*: speeds up throughout |
| ease-out | constant speed, then slows to a stop over the last *N* frames | *F* ≤ *N*: slows down throughout |
| soft symmetric / soft ease-in / soft ease-out | as above, with a gentler start / stop and a higher top speed | as above |

* *N* = 0 gives constant speed in every mode. The default, 60, is half the
  default transition length (120 frames).
* Speed changes smoothly within a transition. Ease-in reaches its keyframe
  at full speed, so follow it with ease-out (or soft ease-out) for one
  smooth move through the keyframe; following it with a mode that starts
  from rest makes the camera stop abruptly there.
* The camera, display ranges and colors follow the easing; the timepoint is
  always interpolated linearly.
* Exported JSON stores *N* along with the keyframes
  (`{"easeFrames": 60, "keyframes": [...]}`), and importing restores it.
  Plain keyframe lists (earlier versions, the original fork) still load and
  keep the current *N*. The original fork cannot read the new files.

## Build

Requirements: a JDK (17 or 21; e.g. [Eclipse Temurin](https://adoptium.net))
and [Maven](https://maven.apache.org/download.cgi). Works the same on Windows,
macOS and Linux.

```
mvn package
```

The plugin is `target/bigdataviewer-movie-producer-0.1.0-SNAPSHOT.jar`.

Or simply download the compiled jar from the
[Releases](https://github.com/ben-gitdev/bdv-movie-producer/releases).
Pushing a tag like `v0.2.0` builds the jar and attaches it to the release of
that name (`.github/workflows/release.yml`).

No local toolchain? Fork this repository on GitHub: the included
workflow (`.github/workflows/build.yml`) builds it on every push and keeps the
jar as a zipped artifact under the **Actions** tab. Chrome may block that zip
as "dangerous" (it contains an executable `.jar`); choose **Keep** /
**Download dangerous file** in Chrome's downloads list (`chrome://downloads`),
or use the jar from a release instead.

## Install

Copy the jar into `Fiji.app/plugins/` (or `Fiji.app/jars/`) and restart Fiji.

## Making a movie from the PNGs

In Fiji: *File › Import › Image Sequence…* on the output folder, then
*File › Save As › AVI…*. Or with ffmpeg:

```
ffmpeg -framerate 30 -i img-%05d.png -c:v libx264 -pix_fmt yuv420p -crf 18 movie.mp4
```

## License

GPL-2.0-or-later (the easing code derives from GPL-2 code; the rest of the
original fork is BSD-2 from bigdataviewer-core).
