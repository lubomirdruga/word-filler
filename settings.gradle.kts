plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.1"
}
rootProject.name = "word-filler"

nmcpSettings {
    centralPortal {
        username.set(System.getenv("CENTRAL_USERNAME"))
        password.set(System.getenv("CENTRAL_PASSWORD"))
        publishingType.set("USER_MANAGED")
    }
}
