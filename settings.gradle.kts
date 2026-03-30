rootProject.name = "ktor-armour"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":armour-core")
include(":armour-retry")
include(":armour-reporting")
include(":armour-ktor")
