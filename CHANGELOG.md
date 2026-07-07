# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-07-07

Initial release.

### Added

- `WordFiller` service for filling Word (`.docx`) templates with dynamic data:
  `export` (classpath), `exportFromFile`, and `exportFromStream`, all returning the
  filled document as a `ByteArray`.
- `{...}` placeholders with full Apache Velocity syntax (properties, conditionals,
  loops, method calls), evaluated against the data object bound as `$model`.
- Nested `.vm` sub-templates referenced as `{|name|}`, resolved under the shared
  template root.
- Placeholder handling across Word's internal structure: body paragraphs, tables
  (including nested tables), headers, and footers - even when Word splits a
  placeholder across multiple runs or text nodes.
- Escape sequences for literal braces and backslashes: `\{`, `\}`, `\\` - working
  inside and outside placeholders, including escapes split across runs.
- Automatic conversion of plain-text `http(s)://` URLs into styled, clickable
  hyperlinks, keeping trailing sentence punctuation out of the link.
- Multi-line values are rendered as line breaks within the paragraph.
- `WordFillerConfig` as the single source of truth for the template root
  (default: `word-filler` on the classpath).
- `TemplateDataProvider` SPI (`com.lubomirdruga.wordfiller.provider`) for plugging
  in custom expression engines; `VelocityDataProvider` ships as the default
  implementation.
- Secure template introspection by default (Velocity `SecureUberspector`) blocking
  reflection escapes such as `$model.class.classLoader`; opt out via
  `VelocityDataProvider(config, secureIntrospection = false)`.
- Fail-fast error handling: `WordFillerException` for template/content problems
  (missing templates, evaluation failures, unterminated placeholders),
  `IllegalArgumentException` for wiring mistakes such as mismatched configs.
- Thread safety: one `WordFiller` + `VelocityDataProvider` pair can serve
  concurrent exports.
- SLF4J debug-level logging (bring your own provider).
- Runs on JVM 17 or higher.

[Unreleased]: https://github.com/lubomirdruga/word-filler/compare/v1.0.0...HEAD

[1.0.0]: https://github.com/lubomirdruga/word-filler/releases/tag/v1.0.0
