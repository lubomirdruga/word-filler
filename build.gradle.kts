plugins {
    kotlin("jvm") version "2.2.20"
    `maven-publish`
}

group = "com.lubomirdruga"
version = "1.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.4.0")
    implementation("org.apache.velocity:velocity-engine-core:2.4.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

// Create sources JAR for Maven publication
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

// Create Javadoc JAR for Maven publication
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.javadoc)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

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

