plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.dokka") version "2.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    jacoco
    `maven-publish`
}

group = "com.lubomirdruga"
// the VERSION file at the repo root is the single source of truth for the library version
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
}

detekt {
    // only the rules explicitly activated in detekt.yml run; ktlint owns formatting
    config.setFrom("detekt.yml")
    buildUponDefaultConfig = false
}

tasks.test {
    useJUnitPlatform()
    configure<JacocoTaskExtension> {
        // Velocity's DeprecationAwareExtProperties crashes on classes with fields injected
        // by coverage instrumentation, so keep the agent away from Velocity classes
        excludes = listOf("org.apache.velocity.*")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
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
                description.set("A Kotlin library for filling Word document templates with Velocity expressions")
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
        // Uncomment and configure for publishing to Maven Central or other repositories
        // maven {
        //     name = "sonatype"
        //     val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
        //     val snapshotsRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        //     url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        //     credentials {
        //         username = project.findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME")
        //         password = project.findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD")
        //     }
        // }
    }
}
