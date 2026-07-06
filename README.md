# Word Filler

[![CI](https://github.com/lubomirdruga/word-filler/actions/workflows/ci.yml/badge.svg)](https://github.com/lubomirdruga/word-filler/actions/workflows/ci.yml)
[![Coverage](https://raw.githubusercontent.com/lubomirdruga/word-filler/badges/jacoco.svg)](https://github.com/lubomirdruga/word-filler/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/lubomirdruga/word-filler?sort=semver)](https://github.com/lubomirdruga/word-filler/releases/latest)
[![API docs](https://img.shields.io/badge/docs-API-blue)](https://lubomirdruga.github.io/word-filler/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/github/license/lubomirdruga/word-filler)](LICENSE)

A Kotlin library for filling Microsoft Word (`.docx`) document templates with dynamic data using Apache Velocity
template expressions.

## Features

- Fill Word document templates with dynamic data via `{...}` placeholders
- Full Apache Velocity syntax inside placeholders (properties, conditionals, loops, method calls)
- Nested `.vm` sub-templates for complex document fragments
- Handles placeholders that Word splits across multiple runs
- Processes paragraphs, tables (including nested content), headers, and footers
- Automatic detection of `http(s)://` URLs and conversion into styled, clickable hyperlinks
- Multi-line values rendered as proper line breaks
- Load Word templates from the classpath, a `File`, or any `InputStream`
- Bring-your-own template engine via the `TemplateDataProvider` SPI

## How It Works

An export is a top-to-bottom pipeline: a Word template and a data object go in, a
filled document comes out. Each stage below feeds the next.

<p align="center">
  <img src="docs/architecture.svg" height="600"
       alt="Processing flow: WordFiller loads the .docx, DocumentWalker walks the document, PlaceholderReplacer evaluates placeholders through a TemplateDataProvider, HyperlinkFormatter converts URLs, then the document is serialized to a ByteArray">
</p>

1. **Load** — `WordFiller.export` (or `exportFromFile` / `exportFromStream`) reads the
   `.docx` into an Apache POI `XWPFDocument`. The classpath location comes from
   `WordFillerConfig`.
2. **Walk** — `DocumentWalker` traverses the whole document structure — headers, body,
   footers, and every table (including tables nested in cells) — emitting one paragraph
   at a time. It is pure traversal and knows nothing about placeholders.
3. **Replace placeholders** — for each paragraph, `PlaceholderReplacer` scans the runs,
   stitching back together `{...}` placeholders that Word split across multiple runs (or
   text nodes) and honouring the `\{`, `\}`, `\\` escapes.
4. **Evaluate** — each placeholder expression is handed to the pluggable
   `TemplateDataProvider`. The default `VelocityDataProvider` evaluates it with Apache
   Velocity against `$model`, resolving `{|name|}` references to nested `.vm`
   sub-templates. Introspection is sandboxed by `SecureUberspector` unless disabled.
5. **Render value** — multi-line evaluated values are written back as proper line breaks
   within the paragraph.
6. **Linkify** — `HyperlinkFormatter` post-processes each paragraph, turning any
   plain-text `http(s)://` URL into a styled, clickable hyperlink run while keeping
   surrounding text, formatting, and trailing sentence punctuation intact.
7. **Serialize** — once every paragraph is processed, the document is written out and
   returned as a `ByteArray`.

`WordFillerConfig` is the shared source of truth for where templates are loaded from,
handed to both `WordFiller` and the data provider so Word templates and their nested
`.vm` files always resolve under the same root.

For the component architecture — a full class diagram plus the dependency and threading
boundaries — see the **Architecture** section of the generated API documentation
(`docs/module.md`, rendered by Dokka into `build/dokka/html/`).

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.lubomirdruga:word-filler:1.0.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.lubomirdruga</groupId>
    <artifactId>word-filler</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

### Basic Usage - Classpath Template

```kotlin
import com.lubomirdruga.wordfiller.WordFiller
import com.lubomirdruga.wordfiller.WordFillerConfig
import com.lubomirdruga.wordfiller.provider.VelocityDataProvider
import java.io.File

data class Person(
    val name: String,
    val jobTitle: String,
)

fun main() {
    val config = WordFillerConfig() // templates under /word-filler/ on the classpath
    val filler = WordFiller(config, VelocityDataProvider(config))

    val person = Person("John Doe", "Software Engineer")

    // Loads the Word template from the classpath at /word-filler/my-template.docx
    val output: ByteArray = filler.export("my-template", person)

    File("output.docx").writeBytes(output)
}
```

### Custom Template Location

`WordFillerConfig` is the single source of truth for where templates live. Pass a custom
base path to load templates from a different classpath root:

```kotlin
val config = WordFillerConfig(templateBasePath = "my/templates")
val filler = WordFiller(config, VelocityDataProvider(config))

// Word template:      /my/templates/invoice.docx
// Nested .vm template: /my/templates/invoice/address.vm
val output = filler.export("invoice", data)
```

### Filesystem or Stream Templates

```kotlin
val config = WordFillerConfig()
val filler = WordFiller(config, VelocityDataProvider(config))

// From a file; "my-template" is still used to locate nested .vm templates
val output = filler.exportFromFile(File("/path/to/template.docx"), "my-template", data)

// From any InputStream
val output2 = filler.exportFromStream(inputStream, "my-template", data)
```

The data object can be any Kotlin/Java object (its public getters are accessible in
templates as `$model.property`) or a `Map<String, Any?>`.

## Template Structure

### Word Document Placeholders

In the Word document, wrap Velocity expressions in curly braces `{}`:

```text
Hello {$model.name}!

Your job title is: {$model.jobTitle}
```

The bound data object is always available as `$model`. Placeholders work in body
paragraphs, table cells, headers, and footers, and are found even when Word internally
splits the placeholder text across multiple runs.

### Nested Velocity Templates

For complex fragments, reference a `.vm` file with the `{|name|}` syntax. Sub-templates
are resolved on the classpath using the pattern `/<templateBasePath>/<templateName>/<name>.vm`
(so `/word-filler/<templateName>/<name>.vm` with the default config), where
`<templateName>` is the name passed to `export` / `exportFromFile` / `exportFromStream`.

**Example directory structure:**

```text
src/main/resources/
  └── word-filler/
      ├── my-template.docx
      └── my-template/
          ├── person_info.vm
          └── address.vm
```

**Word document integration:**

```text
Person: {|person_info|}
Address: {|address|}
```

**person_info.vm example:**

```velocity
#if($model.name)$model.name - $model.jobTitle#{else}No person information available#end
```

If a referenced sub-template cannot be found, the export fails with a
`WordFillerException` naming the missing `.vm` path - see [Error Handling](#error-handling).

### Velocity Syntax

Any Velocity syntax works inside placeholders and `.vm` files:

```velocity
## Simple variable
$model.propertyName

## Conditional
#if($model.value)Value exists: $model.value#{else}No value#end

## Loop
#foreach($item in $model.items)- $item.name
#end

## Method calls
$model.getValue()
$model.name.toUpperCase()
```

### Escaping Literal Braces

Braces normally delimit placeholders. To put literal braces (or a backslash) in the
document text, escape them:

| Sequence | Output |
|----------|--------|
| `\{`     | `{`    |
| `\}`     | `}`    |
| `\\`     | `\`    |

Escapes work inside and outside placeholders, and even when Word splits the escape
sequence across runs.

### Hyperlinks

After substitution, any plain-text `http://` or `https://` URL in the document is
automatically converted into a real clickable hyperlink (blue, underlined), keeping
the surrounding text and its formatting intact. Sentence punctuation directly after
a URL (`.,;:!?"')]}`) is kept as plain text, not made part of the link.

### Multi-line Values

If an evaluated expression produces multiple lines, they are rendered as line breaks
within the paragraph.

## Template Security

Templates are code: a Velocity expression can call methods on the data object you
bind as `$model`. By default the engine runs with Velocity's `SecureUberspector`,
which blocks reflection escapes such as `$model.class.classLoader` or anything on
`ClassLoader`, `Runtime`, `System`, `Thread`, etc. Normal property access and method
calls (`$model.name.toUpperCase()`) work unchanged; blocked references stay
unresolved and render literally.

If your templates are fully trusted and genuinely need unrestricted introspection,
opt out explicitly:

```kotlin
VelocityDataProvider(config, secureIntrospection = false)
```

Even in secure mode, only let people you trust author templates - expressions can
still call any public method on the model you hand them.

## Error Handling

The library fails fast - errors throw instead of silently producing a wrong document:

- `WordFillerException` (unchecked) - template/content problems: Word template not
  found (classpath or file), nested `.vm` template missing, Velocity parse/evaluation
  failure, or an unterminated placeholder (`{` never closed within its paragraph).
  Messages include the template name and offending expression or path.
- `IllegalArgumentException` - wiring mistakes, e.g. constructing `WordFiller` with a
  provider that was created with a different `WordFillerConfig`.

## Thread Safety

A single `WordFiller` + `VelocityDataProvider` pair can be shared across threads and
used for concurrent exports: the substitution state is created per export, the
hyperlink formatter is stateless, and `VelocityEngine` is thread-safe after
initialization with a fresh `VelocityContext` per evaluation.

## Logging

The library logs through SLF4J at debug level only (template resolution, hyperlink
conversion). Add an SLF4J provider (Logback, Log4j2, `slf4j-simple`, …) to see the
output; without one, SLF4J prints its no-operation notice and stays silent.

## API Reference

### WordFiller

```kotlin
WordFiller(config: WordFillerConfig, dataProvider: TemplateDataProvider)
```

Methods (all return `ByteArray` containing the produced `.docx`):

- `export(template: String, obj: Any?)` - loads the Word template from the classpath at
  `/<config.templateBasePath>/<template>.docx`
- `exportFromFile(templateFile: File, templateName: String, obj: Any?)` - loads the Word template from the filesystem
- `exportFromStream(inputStream: InputStream, templateName: String, obj: Any?)` - loads the Word template from a stream

### WordFillerConfig

```kotlin
WordFillerConfig(templateBasePath: String = "word-filler")
```

The single source of truth for the template root. It knows nothing about any
particular template engine:

- `wordTemplatePath(template)` → `/<templateBasePath>/<template>.docx` (used by `export()`)
- `resolve(relativePath)` → `/<templateBasePath>/<relativePath>` - generic helper for
  `TemplateDataProvider` implementations to locate their own resources under the shared root

Leading/trailing slashes are normalized.

### VelocityDataProvider

```kotlin
VelocityDataProvider(config: WordFillerConfig, secureIntrospection: Boolean = true)
```

Evaluates placeholder expressions with Apache Velocity, with secure introspection
enabled by default (see [Template Security](#template-security)). The `.vm` location pattern
(`/<templateBasePath>/<template>/<name>.vm`) is owned by this provider - it derives it
from the shared config's root via `resolve()`. The config is a required parameter, and `WordFiller`
rejects (at construction, with `IllegalArgumentException`) a provider whose config
does not match its own - so Word templates and nested `.vm` templates always resolve
under the same root.

### TemplateDataProvider

```kotlin
fun interface TemplateDataProvider {
    val config: WordFillerConfig?
        get() = null

    fun evaluateExpression(expression: String, template: String, value: Any?): String
}
```

Implement this (or just pass a lambda) to plug in a different expression engine than
Velocity - each provider owns its own resource layout. Providers that resolve templates
by path should override `config`, use `config.resolve(...)` to anchor their resources
under the shared root, so `WordFiller` can verify both use the same root; lambda
providers leave `config` as `null` and skip the check.

## Limitations

- A placeholder must start and end within the same paragraph - a `{` left unclosed at
  the end of a paragraph fails the export with a `WordFillerException`.

## Building the Library

```bash
./gradlew build           # build + run tests and ktlint; JAR lands in build/libs/
./gradlew test            # run the test suite
./gradlew ktlintCheck     # code style check (ktlint, ktlint_official rules)
./gradlew ktlintFormat    # auto-fix code style violations
./gradlew detekt          # static analysis (rules ktlint cannot express, see detekt.yml)
./gradlew publishToMavenLocal
./gradlew publish                            # publishes to build/repo by default; see build.gradle.kts
./gradlew publishAggregationToCentralPortal   # signs and publishes to Maven Central; needs
                                              # SIGNING_KEY/SIGNING_PASSWORD/CENTRAL_USERNAME/
                                              # CENTRAL_PASSWORD env vars (see release.yml)
```

The library version is read from the `VERSION` file at the repository root - edit that
file to cut a new version.

## Requirements

- JVM 17 or higher

## Dependencies

- Apache POI 5.5.1 (Word document processing)
- Apache Velocity 2.4.1 (template engine)
- SLF4J API 2.0.18 (logging facade; bring your own provider)

## License

This project is licensed under the Apache License 2.0.

## Contributing

Contributions are welcome. Please feel free to submit a Pull Request or open an issue for discussion.
