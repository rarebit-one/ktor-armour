plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

subprojects {
    repositories {
        mavenCentral()
        google()
    }
}
