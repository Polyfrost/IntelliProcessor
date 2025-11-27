<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# IntelliProcessor Changelog

## [Unreleased]

### Added
- Allowed double clicking on entries in the "jump to preprocessed files" dialogue window to open the file.
- Preprocessor comment blocks can now fold each if/ifdef/elseif/else block separately, rather than the entire block from #if -> #endif
- Added a plugin settings screen
- Preprocessor fold regions
   - can optionally collapse inactive regions by default (based on preprocessor condition checks)
   - can optionally collapse all by default
- Preprocessor condition checking
   - now support chained checks e.g. `//#if this && that`
   - now support loader checks e.g. `//#if FABRIC && !FORGE`
   - now correctly checks conditions that need to be false, such as the #if/elseif's preceding an #else/elseif block being tested
- Preprocessor jump to file action
   - now also correctly moves you to the same caret position and scrolls to it
   - can now differentiate files based on the preprocessor conditions that apply at the caret position
   - can now optionally hide those differentiated results
   - can now press the down arrow from the search bar to navigate to the list (keyboard navigation streamlining)
- can optionally highlight formatting/clarity problems such as:
   - #if directive not being indented further than it's containing preprocessor block
   - #else/elseif/endif directives not having a matching indent with their initial #if directive 
- Added an option to disable the `//$$ ` insertion on new lines
<img width="750" height="405" alt="image" src="https://github.com/user-attachments/assets/dc364ea4-3502-45e0-88ec-e12c8c5bee76" />

- Added an action to toggle all preprocessor comments `//$$ ` for the selected lines
- Added an action to toggle all preprocessor comments `//$$ ` for the entire preprocessor block the caret is within
- Further keyboard navigation improvements to file jump action

### Fixed
- Fixed sorting of entries in the "jump to preprocessed files" dialogue window.
- Fixed kotlin k2 mode not registering correctly and always ignoring the optional kotlin-plugin.xml, and thus no working kotlin file folding in k2
- Fixed/improved `PreprocessorNewLineHandler` noticably
- Several bugs from the previous preprocessor condition checking fixed, including the `prevSibling` iterating not correctly finding previous directives
- Correct version checking has replaced some logic that used `mainVersion` for all files, `mainVersion` is still a fallback if this fails

### Updated
- Changed preprocessor comment styling to have more muted colours & use italics
<img width="381" height="218" alt="image" src="https://github.com/user-attachments/assets/2009d6aa-baa6-4117-adcf-c53e47941b83" />

- Added support for override files within `versions/<version>/src/` to the "jump to preprocessed files" action.
- Improved the visual clarity of the list in the "jump to preprocessed files" dialogue window.
- Improved keyboard navigation of the "jump to preprocessed files" dialogue window.
- Having ! before a condition identifer is no longer highlighted as an error, e.g. `!FABRIC`

Thank you to [@Traben](https://github.com/Traben-0) for contributing this update!


## [4.0.0] - 2025-07-31

### Added

- Add automatic preprocessor code opening when starting a newline after a non-matching preprocessor statement (such as `//#if MC >= 1.16.5` when your main project is 1.8.9)
- Redesign file jump action
  - Add search bar
  - Improve item ordering
  - Make it work in subprojects/submodules
  - Give clearer warnings when something isn't working as expected

### Fixed

- Fix memory leak in syntax highlighter
- Fix indentation on automatically added newline characters being incorrect

### Updated

- Bump supported IntelliJ version to latest
- Improve preprocessor statement folding algorithm
- Improve preprocessor statement autocompletion

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

[Unreleased]: https://github.com/Polyfrost/IntelliProcessor/compare/v4.0.0...HEAD
[4.0.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v3.1.0...v4.0.0
[3.1.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v2.1.0...v3.0.0
[2.1.0]: https://github.com/Polyfrost/IntelliProcessor/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/Polyfrost/IntelliProcessor/commits/v2.0.0
