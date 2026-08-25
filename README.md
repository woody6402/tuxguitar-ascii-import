# TuxGuitar ASCII TAB Importer

Experimental ASCII tablature importer for TuxGuitar.

The plugin adds support for opening common ASCII guitar tablature files directly in TuxGuitar. It is intended as a **best-effort importer**, not as an implementation of a formal ASCII TAB standard.

## Background

ASCII guitar tablature has existed for decades, but there is no single universally adopted specification. Different TAB collections and websites use different conventions for string labels and tuning, measure separators, note spacing, rhythm, effects, repeats and metadata.

The approach of this plugin is pragmatic:

1. recognize the common de-facto ASCII TAB structure
2. extract as much musical information as possible
3. create a base TuxGuitar song
4. allow the user to correct or refine the result inside TuxGuitar

The goal is not perfect reconstruction of the original score. ASCII TAB usually does not contain enough information for that.

## Current features

### ASCII TAB detection

The importer recognizes typical six-string guitar tablature:

```text
E|------------------------|
B|----1---------3p1--0h1--|
G|------------------------|
D|---------2----2----2----|
A|----0----3----3----3----|
E|------------------------|
```
Please beware: special actions like p,h,/, ... are currently not handled, they can stay in, but will be eliminated.

Supported file extensions:

```text
.tab
.txt
.ascii
```

The file format detector checks the content so other TuxGuitar formats such as GP3/GP4/GP5 are not intercepted by the ASCII importer.

## Measures

Measure separators are detected from `|` characters.

A line break in the middle of a measure is currently not supported and needs some hand rework afterwards,

## Tuning

Tuning is detected from the string labels and transferred to the TuxGuitar track.

Standard tuning:

```text
E|
B|
G|
D|
A|
E|
```

Drop D:

```text
E|
B|
G|
D|
A|
D|
```

## Metadata

The importer currently understands:

```text
time: a/b => typical timings like: 2/4, 3/4. 4/4, .... 
tempo: 80
```

Defaults are used if metadata is missing:

```text
time: 4/4
tempo: 120
```

Planned metadata includes:

```text
title:
artist:
album:
author:
tuning:
quantize:
```

## Rhythm and quantization

ASCII tablature normally describes note positions visually rather than with exact musical durations. The importer therefore performs proportional quantization of ASCII positions and converts the result into TuxGuitar durations.

The quantization code evolved from the earlier `tab2gp5` converter:

https://github.com/woody6402/tab2gp5

The resulting rhythm should be considered an approximation. The intended workflow is:

```text
Import
→ listen / inspect in TuxGuitar
→ correct rhythm where necessary
→ continue editing normally
```

## Chords and simultaneous notes

Notes detected at the same musical position are placed into the same TuxGuitar beat. Multi-digit fret numbers are treated as one note rather than separate beats.

## Rests

ASCII spacing can result in a rest before the first note in a measure.

## Effects

ASCII TAB commonly uses symbols such as:

```text
8h10
8p7
4/5
7\5
7~
x
```

At the moment the importer primarily extracts the notes themselves.

A planned second parsing pass will analyze the characters around and between consecutive notes on the same string. Planned effects include hammer-ons, pull-offs, slides, vibrato and dead notes, which can then be mapped to TuxGuitar `TGNoteEffect` information.

## Compatibility

The plugin is implemented in Java and does not require Python or native libraries. It is built as a normal TuxGuitar plugin JAR.

Tested:

```text
TuxGuitar 2.1.0
TuxGuitar 2.1.1
```

A plugin built against TuxGuitar 2.1.1 has successfully been loaded into a separately installed TuxGuitar 2.1.0 package. Compatibility with other 2.x releases is not yet fully tested.

## Installation

Copy the plugin JAR into the appropriate TuxGuitar plugin directory and restart TuxGuitar.

This is only intended as early preview version. As example there's Mozart's Rondo alla Turce added in an handled and unhandled version.
Please BEWARE: you need to edit the first parsing result to achieve a proper playing version. But it reduces rewriting from the tab dramatically.

## Development status

Current focus:

- reliable detection of six-string ASCII TAB blocks
- correct measure detection
- missing final measure separators
- tuning detection
- time signature and tempo metadata
- usable rhythm quantization
- robust handling of different ASCII TAB layouts

Next planned steps:

1. title and additional metadata
2. configurable/adaptive quantization
3. explicit tuning metadata
4. multi-digit fret alignment improvements
5. hammer-on / pull-off / slide / vibrato effects
6. testing with TAB collections from different sources

## Why this can still be useful

There is no universal ASCII TAB standard, so no importer can reliably reconstruct every file found on the Internet. However, many ASCII TAB files share a common structural core.

The objective is not:

> Convert every ASCII TAB perfectly.

but rather:

> Turn as many existing ASCII TABs as possible into a useful, further editable TuxGuitar starting point.

Manual correction inside TuxGuitar is expected and is part of the intended workflow.

## Related project

The importer evolved from:

https://github.com/woody6402/tab2gp5

The earlier project converts ASCII/classic TAB files into GP5. This plugin removes the intermediate GP5 conversion and builds a native `TGSong` directly.

## Feedback

ASCII TAB conventions vary widely. Examples of files that import correctly, partially import, fail completely, or use unusual tuning/rhythm notation are useful for improving the parser.

Please include a small representative TAB fragment when reporting parsing issues.

Also beware that due to the current state no answer or correction can be expected ...

## Status

Very early BETA

## Test Observations

- if midi or automatic generated ascii tabs quantisation can bring playable results
- based on writing style empty measures at the end of an ascii tab may be generated
- --tuning is parsed but not correctly forwarded to tuxguitar-- 
- time measure needs more pattern: time, timing, ... takt
- if time is missing 4/4 is assumed, if another time is needed, simple write it at the beginning of the ascii file => time; 2/4
- archives:
  - classtab: examples from mozart, teleman, arlen, bach, mancini, ... - tons of pieces were tried
    - reading works in > 90%
    - note timing is guessed/calculated and depends on writing style (favours automaic generation i.e if source was midi)
    - beware; main target is to get the note material and adjust timing per hand accordingly
  - LickByNeck
    - some automagic extracted pieces were checked
  - Ultimate guitar
    - since there are tons of contrubutors, there are tons of writing styles. but reading was also possible in >90%
  - Tuxguitar tab export
    - tried for Bach Cello suite No 1 - seems to work
    - Moore Spanish Guitar: after adding time: 3/4 (was not written by export), import succeeded in playable form               

## Disclaimer

bulid under use of AI but with a longtime tabulature experience also in coding back to Atari 1040 times.
