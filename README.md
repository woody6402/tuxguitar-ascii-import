# TuxGuitar ASCII TAB Importer

Experimental ASCII tablature importer for TuxGuitar.

The plugin adds support for opening common ASCII guitar tablature files
directly in TuxGuitar.

It is intended as a **best-effort importer**, not as an implementation of a
formal ASCII TAB standard.

## Background

ASCII guitar tablature has existed for decades, but there is no single
universally adopted specification.

Different TAB collections and websites use different conventions for:

- string labels and tuning
- measure separators
- note spacing
- rhythm
- hammer-ons / pull-offs
- slides
- bends
- vibrato
- repeats
- metadata

The approach of this plugin is therefore pragmatic:

1. recognize the common de-facto ASCII TAB structure
2. extract as much musical information as possible
3. create a TuxGuitar song for further editing 
4. allow the user to correct or refine the result inside TuxGuitar

The goal is not perfect reconstruction of the original score.

ASCII TAB usually does not contain enough information for that.

---

## Current features

### ASCII TAB detection

The importer recognizes typical six-string guitar tablature such as:

```text
E|------------------------|
B|----1---------3p1--0h1--|
G|------------------------|
D|---------2----2----2----|
A|----0----3----3----3----|
E|------------------------|
