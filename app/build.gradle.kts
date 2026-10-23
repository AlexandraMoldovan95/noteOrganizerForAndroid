plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ro.noteorganizer.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ro.noteorganizer.app"
        // Android 8.0. Raised from 7.0 for java.util.Base64, which a share
        // code needs: the alternative, android.util.Base64, exists only on
        // a device, so the encoding could not be tested on the computer.
        // Android 7 is from 2016 and the version below this one.
        minSdk = 26
        targetSdk = 37
        // See CHANGELOG.md: versionCode is the number of released version
        // sections there, so the two cannot drift apart; versionName is
        // the newest of them. Both were the template's 1 / "1.0" until
        // 2026-09-15, and "1.0" was not true of an app still exploring.
        versionCode = 13
        versionName = "0.11.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: the code and resources nothing reaches are left out, the
            // rest made smaller -- the build Play installs.
            optimization {
                enable = true
            }
            // Signed with this machine's debug key for now, so a release
            // build installs OVER the debug one on a test phone and keeps
            // its notes: a different key would make Android refuse it, and
            // uninstalling first would delete the notebook. Play refuses a
            // debug-signed upload, so this changes before the first upload,
            // to an upload key that never enters the repository.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    // Android ships org.json, but the SDK jar the JVM unit tests compile
    // against holds only stubs that throw "not mocked". Without a real
    // implementation on the TEST classpath, every test that parses a note
    // fails for a reason that has nothing to do with the code being
    // tested. Test scope only -- the app keeps using the platform's own.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}