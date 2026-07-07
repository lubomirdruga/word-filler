plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    jacoco
    `maven-publish`
    signing
}

group = "com.lubomirdruga"
version =
    providers
        .fileContents(layout.projectDirectory.file("VERSION"))
        .asText
        .get()
        .trim()

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.apache.velocity:velocity-engine-core:2.4.1")
    implementation("org.slf4j:slf4j-api:2.0.18")
    testImplementation(kotlin("test"))

    dokkaPlugin("org.jetbrains.dokka:versioning-plugin:2.2.0")
}

detekt {
    // only the rules explicitly activated in detekt.yml run; ktlint owns formatting
    config.setFrom("detekt.yml")
    buildUponDefaultConfig = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    // SARIF lets CI surface findings in GitHub's code-scanning / PR "Files changed" view
    reports.sarif.required.set(true)
}

tasks.test {
    useJUnitPlatform()
    configure<JacocoTaskExtension> {
        excludes = listOf("org.apache.velocity.*")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true) // consumed by the CI coverage-badge job
    }
}

kotlin {
    jvmToolchain(17)
}

dokka {
    dokkaSourceSets.main {
        includes.from("docs/module.md")
    }
    pluginsConfiguration.html {
        customAssets.from("docs/architecture.svg", "docs/class-diagram.svg")
    }
    pluginsConfiguration.versioning {
        version = project.version.toString()

        // In CI the release workflow restores previously published versions here,
        // one sub-directory per version; the plugin bundles them under `older/` and
        // wires up the version dropdown. When the directory is absent or empty (local
        // builds, Maven javadoc jar, or the very first release) only the current version
        // is generated - no old-version bloat.
        val archive = layout.buildDirectory.dir("docs-archive").get()
        if (archive.asFile.listFiles()?.any { it.isDirectory } == true) {
            olderVersionsDir = archive
        }
    }
}

// Create sources JAR for Maven publication
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// Create Javadoc JAR for Maven publication from Dokka HTML output
// (the plain `javadoc` task is a no-op for Kotlin-only sources)
val dokkaJavadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(dokkaJavadocJar)

            groupId = "com.lubomirdruga"
            artifactId = "word-filler"
            version = project.version.toString()

            pom {
                name.set("Word Filler")
                description.set(
                    "A Kotlin library for filling Word document templates with Velocity templates and dynamic data, or with Template Engine of your choice",
                )
                url.set("https://github.com/lubomirdruga/word-filler")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("lubomirdruga")
                        name.set("Lubomir Druga")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/lubomirdruga/word-filler.git")
                    developerConnection.set("scm:git:ssh://github.com:lubomirdruga/word-filler.git")
                    url.set("https://github.com/lubomirdruga/word-filler")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
        // The Central Portal target itself is wired up by the `com.gradleup.nmcp.settings`
        // plugin in settings.gradle.kts (see nmcpSettings {}) - it registers its own
        // publishing repository and the `publishAggregationToCentralPortal` task.
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications["mavenJava"])
}
