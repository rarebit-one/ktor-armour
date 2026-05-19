plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.mavenPublish)
}

mavenPublishing {
    pom {
        name.set("armour-reporting")
        description.set("Reporting/observability hooks for armour-core resilience events.")
    }
}

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(projects.armourCore)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
