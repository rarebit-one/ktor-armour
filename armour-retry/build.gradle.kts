plugins {
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
}

group = "one.rarebit.armour"
version = "0.3.0"

kotlin {
    jvm()
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
