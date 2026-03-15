pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application" -> useModule("com.android.tools.build:gradle:${requested.version}")
                "org.jetbrains.kotlin.android" -> {
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                }
            }
        }
    }
    repositories {
        val androidMavenMirrorUrl = System.getenv("ANDROID_MAVEN_MIRROR_URL")
        val candidateSdkRoots = buildList {
            System.getenv("ANDROID_SDK_ROOT")?.let(::add)
            System.getenv("ANDROID_HOME")?.let(::add)
            add("/usr/local/lib/android/sdk")
            add("/opt/android/sdk")
            add("${System.getProperty("user.home")}/Library/Android/sdk")
            add("${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk")
        }.distinct()
        val sdkRepositories = candidateSdkRoots.flatMap { sdkRoot ->
            listOf(
                java.io.File(sdkRoot, "extras/google/m2repository"),
                java.io.File(sdkRoot, "extras/android/m2repository")
            )
        }.filter { it.isDirectory }

        mavenLocal()
        androidMavenMirrorUrl?.let { mirrorUrl ->
            maven { url = uri(mirrorUrl) }
        }
        sdkRepositories.forEach { repository ->
            maven { url = repository.toURI() }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val androidMavenMirrorUrl = System.getenv("ANDROID_MAVEN_MIRROR_URL")
        val candidateSdkRoots = buildList {
            System.getenv("ANDROID_SDK_ROOT")?.let(::add)
            System.getenv("ANDROID_HOME")?.let(::add)
            add("/usr/local/lib/android/sdk")
            add("/opt/android/sdk")
            add("${System.getProperty("user.home")}/Library/Android/sdk")
            add("${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk")
        }.distinct()
        val sdkRepositories = candidateSdkRoots.flatMap { sdkRoot ->
            listOf(
                java.io.File(sdkRoot, "extras/google/m2repository"),
                java.io.File(sdkRoot, "extras/android/m2repository")
            )
        }.filter { it.isDirectory }

        mavenLocal()
        androidMavenMirrorUrl?.let { mirrorUrl ->
            maven { url = uri(mirrorUrl) }
        }
        sdkRepositories.forEach { repository ->
            maven { url = repository.toURI() }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "InfiniteZoomDrawing"
include(":app")
