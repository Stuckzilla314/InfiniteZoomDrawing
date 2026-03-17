plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.infiniteuniversedrawing"
    compileSdk = 34

    val releaseStoreFile = providers
        .environmentVariable("INFINITE_UNIVERSE_DRAWING_RELEASE_STORE_FILE")
        .orElse(providers.environmentVariable("INFINITE_ZOOM_DRAWING_RELEASE_STORE_FILE"))
        .orNull
    val releaseStorePassword = providers
        .environmentVariable("INFINITE_UNIVERSE_DRAWING_RELEASE_STORE_PASSWORD")
        .orElse(providers.environmentVariable("INFINITE_ZOOM_DRAWING_RELEASE_STORE_PASSWORD"))
        .orNull
    val releaseKeyAlias = providers
        .environmentVariable("INFINITE_UNIVERSE_DRAWING_RELEASE_KEY_ALIAS")
        .orElse(providers.environmentVariable("INFINITE_ZOOM_DRAWING_RELEASE_KEY_ALIAS"))
        .orNull
    val releaseKeyPassword = providers
        .environmentVariable("INFINITE_UNIVERSE_DRAWING_RELEASE_KEY_PASSWORD")
        .orElse(providers.environmentVariable("INFINITE_ZOOM_DRAWING_RELEASE_KEY_PASSWORD"))
        .orNull
    val releaseStore = releaseStoreFile?.let { file(it) }
    val hasAllReleaseSigningEnvVars = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }
    val hasReadableReleaseStore = releaseStore?.isFile == true && releaseStore.canRead()
    val hasReleaseSigning = hasAllReleaseSigningEnvVars && hasReadableReleaseStore

    defaultConfig {
        applicationId = "com.example.infiniteuniversedrawing"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseStore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
