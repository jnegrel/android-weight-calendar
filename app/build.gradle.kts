// Build settings for the app module.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)   // code generator used by Room
}

android {
    namespace = "no.jene.weightcalendar"
    compileSdk = 37

    defaultConfig {
        applicationId = "no.jene.weightcalendar"
        minSdk = 26          // Android 8.0
        targetSdk = 37       // Android 17
        // versionCode must increase with every APK installed on the phone,
        // otherwise Android refuses the update. versionName is what the user sees.
        versionCode = 4
        versionName = "1.2"
    }

    // Release signing key. The key file and passwords are NOT in the project: they are read
    // from ~/.gradle/gradle.properties on this machine. Without them, release builds are
    // simply left unsigned (debug builds always work).
    val storeFile = providers.gradleProperty("WEIGHTCAL_STORE_FILE").orNull
    signingConfigs {
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = providers.gradleProperty("WEIGHTCAL_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("WEIGHTCAL_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("WEIGHTCAL_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            // R8 removes unused code and resources: the APK goes from ~11 MB to ~2 MB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // The debug app gets its own ID, so it installs *next to* the real app instead of
            // replacing it: testing never touches your real data. Its name is set in
            // src/debug/res/values/strings.xml.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room: database library. KSP generates the database code from our annotations.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // The BOM ("bill of materials") picks matching versions of all Compose libraries
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
