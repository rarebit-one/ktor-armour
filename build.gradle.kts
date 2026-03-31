plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }

    afterEvaluate {
        extensions.findByType<PublishingExtension>()?.repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/rarebit-one/ktor-armour")
                credentials {
                    username = System.getenv("GPR_USER") ?: findProperty("gpr.user") as? String
                    password = System.getenv("GPR_TOKEN") ?: findProperty("gpr.key") as? String
                }
            }
        }
    }
}
