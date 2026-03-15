fun org.gradle.api.artifacts.dsl.RepositoryHandler.configureAndroidRepositories(
    includeGradlePluginPortal: Boolean = false
) {
    val androidMavenMirrorUrl = System.getenv("ANDROID_MAVEN_MIRROR_URL")
    val androidSdkRoot = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: "/usr/local/lib/android/sdk"
    val googleSdkRepository = java.io.File(androidSdkRoot, "extras/google/m2repository")
    val androidSdkRepository = java.io.File(androidSdkRoot, "extras/android/m2repository")

    mavenLocal()
    androidMavenMirrorUrl?.let { mirrorUrl ->
        maven { url = uri(mirrorUrl) }
    }
    if (googleSdkRepository.isDirectory) maven { url = googleSdkRepository.toURI() }
    if (androidSdkRepository.isDirectory) maven { url = androidSdkRepository.toURI() }
    google()
    mavenCentral()
    if (includeGradlePluginPortal) {
        gradlePluginPortal()
    }
}

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
        configureAndroidRepositories(includeGradlePluginPortal = true)
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        configureAndroidRepositories()
    }
}

rootProject.name = "InfiniteZoomDrawing"
include(":app")
