pluginManagement {

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    val kotlinVersion: String by settings
    resolutionStrategy.eachPlugin {
        if (requested.id.toString().startsWith("org.jetbrains.kotlin.")) {
            useVersion(kotlinVersion)
        }
    }
}

rootProject.name = "aws-codeartifact-maven-proxy-parent"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

enableFeaturePreview("VERSION_CATALOGS")

include(
    "aws-codeartifact-maven-proxy",
    "aws-codeartifact-maven-proxy-cli"
)
