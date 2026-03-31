plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

subprojects {
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
