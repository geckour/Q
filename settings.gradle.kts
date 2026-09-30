import java.util.Properties

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
    }
}

val secretProperties = Properties().apply {
    settingsDir.resolve("secret.properties").inputStream().use { load(it) }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/geckour/Q") {
            name = "GitHubPackages"

            credentials {
                username = secretProperties.getProperty("gpr.usr") ?: System.getenv("GPR_USER")
                password = secretProperties.getProperty("gpr.key") ?: System.getenv("GPR_TOKEN")
            }

            content {
                includeModule("androidx.media3", "media3-decoder-ffmpeg")
            }
        }
        flatDir {
            dirs("spotify/libs")
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Q"

include(":app", ":core", ":database", ":spotify", ":worker", ":dropbox")
