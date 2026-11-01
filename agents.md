# Vanilla Minecraft Source Code

This repository contains extracted copies of decompiled vanilla Minecraft source code under:
- `fabric/sources/26.3/` (Current target version)
- `fabric/sources/26.2/` (Previous version reference)

## What is this?
When updating mods to newer versions of Minecraft (e.g., migrating from 26.2 to 26.3), method signatures, fields, class locations, and hierarchies in the underlying game code frequently change. This directory acts as a local, fully searchable repository of the vanilla Minecraft codebase (both common and client-side logic).

> **Important for 26.3:** In Minecraft 26.3, first-person hand rendering was split out of `ItemInHandRenderer` (removed) into `FirstPersonHandsAndItems` (tick + render state extraction, owned by `LocalPlayer`) and `FirstPersonHandsAndItemsRenderer` (draws from `PlayerRenderState`, extracted in `LevelExtractor`). Always check `fabric/sources/26.3/` for current implementation details.

## How to use it
As an AI Agent or developer, you can use these sources to investigate API changes and resolve compilation or mixin errors.

**Best Practices for Agents:**
1. **Search for references in the current version:** If you encounter an `InvalidInjectionException`, `NoSuchMethodError`, or missing symbol, use `grep_search` within `fabric/sources/26.3/` (or `fabric/sources/26.2/` for diffing) to find target classes and inspect method signatures/fields.
   * Example (current version): `grep_search(SearchPath="fabric/sources/26.3", Query="setScreen", MatchPerLine=true)`
   * Example (diffing against 26.2): `grep_search(SearchPath="fabric/sources/26.2", Query="setScreen", MatchPerLine=true)`
2. **Examine classes:** Once you find the target file, use `view_file` to inspect exact parameters, fields, annotations, and implementations to properly configure your Mixins or API calls.
3. **Trace logic:** You can trace how vanilla Minecraft implements specific mechanics by searching interfaces or base classes within `fabric/sources/26.3/` to understand intended usage in the current version.

*Note: The `fabric/sources/` directory is ignored by Git to prevent bloating the repository.*
