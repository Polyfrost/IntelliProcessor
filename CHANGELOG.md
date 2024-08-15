<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IntelliProcessor Changelog

## [Unreleased]

## [3.1.0] - 2024-08-15

### Fixed

- Remove deprecated IntelliJ platform APIs (psi `startOffset`, `endOffset`)
- Fix `//$$ ` directive highlighting in cases where there is no spacing (`//#"1.14.2"`)

## [3.0.0] - 2024-08-14

### Added

- Update to IntelliJ Platform `2.0.0`

### Fixed

- Fixed formatting issues
- Removed testing

## [2.1.0] - 2024-06-15

### Added

- Add jump to/from preprocessed file action. You will need to manually assign a keybinding for this feature.

### Fixed

- Allowed hyphens as a valid Preprocessor `#if` identifier.

## [2.0.0] - 2024-05-03

### Added

- Initial commit to IntelliProcessor IDEA plugin

### fixed

- Updated IntelliJ version and refactored code

[Unreleased]: https://github.com/Polyfrost/IntelliProcessor/compare/v3.1.0...HEAD
[3.1.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v2.1.0...v3.0.0
[2.1.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/Polyfrost/IntelliProcessor/commits/v2.0.0
