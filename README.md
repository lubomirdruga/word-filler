# Word Filler Library

A Kotlin library for filling Microsoft Word document templates with dynamic data using Velocity template expressions.

## Features

- Fill Microsoft Word document templates with dynamic data
- Support for Apache Velocity template expressions
- Load templates from both classpath and filesystem
- Automatic hyperlink detection and professional formatting
- Comprehensive support for tables, paragraphs, and headers
- Nested template support for complex document generation

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.lubomirdruga:word-filler:1.0-SNAPSHOT")
}
```

### Maven

```xml
<dependency>
    <groupId>com.lubomirdruga</groupId>
    <artifactId>word-filler</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Usage

### Basic Usage - Classpath Templates

```kotlin
import com.lubomirdruga.wordfiller.VelocityDataProvider
import com.lubomirdruga.wordfiller.WordExportService
import java.io.File

data class Person(
    val name: String,
    val jobTitle: String
)

fun main() {
    // Create service with classpath templates
    val service = WordExportService(VelocityDataProvider())
    
    val person = Person("John Doe", "Software Engineer")
    
    // Export using template from classpath
    val output = service.export("template-name", person)
    
    // Save to file
    File("output.docx").writeBytes(output)
}
```

### Advanced Usage - Custom Template Path

```kotlin
import com.lubomirdruga.wordfiller.VelocityDataProvider
import com.lubomirdruga.wordfiller.WordExportService
import java.io.File

fun main() {
    // Create service with custom filesystem templates
    val templatePath = "/path/to/templates"
    val velocityProvider = VelocityDataProvider(customTemplatePath = templatePath)
    val service = WordExportService(velocityProvider)
    
    val data = mapOf(
        "name" to "John Doe",
        "company" to "Acme Corp"
    )
    
    // Load Word template from file
    val templateFile = File("/path/to/template.docx")
    val output = service.exportFromFile(templateFile, "template-name", data)
    
    File("output.docx").writeBytes(output)
}
```

## Template Structure

### Word Document Templates

In your Word document, use curly braces `{}` to define placeholders. For example:

```text
Hello {$model.name}!

Your job title is: {$model.jobTitle}
```

### Velocity Templates

For complex nested structures, you can create `.vm` files in the template directory.

**Example directory structure:**
```text
templates/
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
#if($model.name)
$model.name - $model.jobTitle
#else
No person information available
#end
```

### Velocity Template Syntax

The library uses Apache Velocity for template processing. Common patterns:

```velocity
## Simple variable
$model.propertyName

## Conditional
#if($model.value)
  Value exists: $model.value
#else
  No value
#end

## Loop
#foreach($item in $model.items)
  - $item.name
#end

## Method calls
$model.getValue()
$model.name.toUpperCase()
```

## API Reference

### VelocityDataProvider

Constructor:
- `VelocityDataProvider(customTemplatePath: String? = null, templateBasePath: String = "/export-word/%s/%s.vm")`
  - `customTemplatePath`: Optional filesystem path for templates
  - `templateBasePath`: Pattern for classpath template resolution

### WordExportService

Constructor:
- `WordExportService(velocityDataProvider: VelocityDataProvider, templateBasePath: String = "/export-word/%s.docx")`

Methods:
- `export(template: String, obj: Any?): ByteArray` - Export using classpath template
- `exportFromFile(templateFile: File, templateName: String, obj: Any?): ByteArray` - Export using file template
- `exportFromStream(inputStream: InputStream, templateName: String, obj: Any?): ByteArray` - Export using stream

## Building the Library

### Build JAR

```bash
./gradlew build
```

The JAR will be created in `build/libs/word-filler-1.0-SNAPSHOT.jar`

### Publish to Local Maven Repository

```bash
./gradlew publishToMavenLocal
```

### Publish to Custom Repository

```bash
./gradlew publish
```

This publishes to `build/repo` by default. Configure other repositories in `build.gradle.kts`.

## Requirements

- JVM 21 or higher
- Kotlin 2.2.20 or higher

## Dependencies

- Apache POI 5.3.0 (Word document processing)
- Apache Velocity 2.4.1 (Template engine)

## License

This project is licensed under the Apache License 2.0.

## Contributing

Contributions are welcome. Please feel free to submit a Pull Request or open an issue for discussion.
