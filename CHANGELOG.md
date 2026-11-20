# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Spotless formatting checks with Google Java Format in four-space AOSP style for mod and HUD example Java sources.
- Validated server detail snapshots with provider display modes, connection-bound caches, and selected-part request validation, included in extension API 1.
- Named machine sources with interface matching, shared display settings and server locks, and optional input/output limits per tick.
- Client energy projections that consistently convert stored energy, capacity and tick limits without modifying server values.
- Extension API 1 with resolved ForgeMultipart selections, HUD registration by part type ID, and optional selected-part class matching.
- Contextual harvest providers for independent tool-class levels, target requirements, harvest overrides, and scoped tier names.

### Fixed

- Scope generic tank and inventory previews to the selected multipart and validate its selection on the server, preventing covers from showing another part's contents.
- Dispatch WAILA multipart providers and decorators only for the selected runtime part, independently of tile NBT ordering.
- Invalidate cached target data when multipart selection or block metadata changes, and trace server-side multipart targets along the player's sight line.
- Share contextual harvest conclusions across all harvest styles and the breaking-progress indicator.
