plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

// Option A: Uncomment to publish to GitHub Packages
// Run: GITHUB_ACTOR=jaryl GITHUB_TOKEN=<pat> ./gradlew publish
//
// subprojects {
//     afterEvaluate {
//         extensions.findByType<PublishingExtension>()?.repositories {
//             maven {
//                 name = "GitHubPackages"
//                 url = uri("https://maven.pkg.github.com/rarebit-one/ktor-armour")
//                 credentials {
//                     username = System.getenv("GITHUB_ACTOR") ?: findProperty("gpr.user") as? String
//                     password = System.getenv("GITHUB_TOKEN") ?: findProperty("gpr.key") as? String
//                 }
//             }
//         }
//     }
// }
