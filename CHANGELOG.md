# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Extension API 1 with resolved ForgeMultipart selections, HUD registration by part type ID, and optional selected-part class matching.
- Contextual harvest providers for independent tool-class levels, target requirements, harvest overrides, and scoped tier names.

### Fixed

- Dispatch WAILA multipart providers and decorators only for the selected runtime part, independently of tile NBT ordering.
- Invalidate cached target data when multipart selection or block metadata changes, and trace server-side multipart targets along the player's sight line.
- Share contextual harvest conclusions across all harvest styles and the breaking-progress indicator.
