// Imported rather than spelled java.util.Properties: in a build script
// `java` is also the project's Java extension, and inside a lambda the
// name resolves to it and not to the package.
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The upload key, which Google Play's App Signing checks every bundle
// against, never enters the repository. A properties file outside it names
// the keystore and its passwords -- storeFile, storePassword, keyAlias,
// keyPassword -- and is found through the Gradle property noteorgUploadKey
// (in the user's own ~/.gradle/gradle.properties) or the environment
// variable NOTEORG_UPLOAD_KEY. A file or a keystore inside the repository
// is refused: a key committed by mistake is a key anybody can sign with.
val uploadKey: Properties? =
    (providers.gradleProperty("noteorgUploadKey").orNull
        ?: providers.environmentVariable("NOTEORG_UPLOAD_KEY").orNull)
        ?.let { path ->
            val repository = rootDir.canonicalFile
            fun outside(f: File, what: String): File {
                val real = f.canonicalFile
                if (!real.isFile) throw GradleException("the upload key's $what is not at $real")
                if (real.toPath().startsWith(repository.toPath())) {
                    throw GradleException("the upload key's $what is inside the repository ($real): keep it outside")
                }
                return real
            }
            val properties = Properties()
            outside(file(path), "properties file").inputStream().use { properties.load(it) }
            val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .filter { properties.getProperty(it).isNullOrBlank() }
            if (missing.isNotEmpty()) {
                throw GradleException("the upload key's properties file has no ${missing.joinToString()}")
            }
            properties.setProperty("storeFile", outside(file(properties.getProperty("storeFile")), "keystore").path)
            properties
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
        versionCode = 22
        versionName = "0.15.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: the code and resources nothing reaches are left out, the
            // rest made smaller -- the build Play installs.
            optimization {
                enable = true
            }
            // With the upload key named (above), the build Google Play takes.
            // Without it, this machine's debug key, so a release build
            // installs OVER the debug one on a test phone and keeps its
            // notes: a different key would make Android refuse it, and
            // uninstalling first would delete the notebook. Google Play
            // refuses a debug-signed upload, and tools/keep_release.py
            // refuses to keep one for it.
            signingConfig = signingConfigs.getByName(if (uploadKey != null) "upload" else "debug")
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

// The unit tests' temporary folders go under build/, one folder per test
// task, emptied when the task starts. In the system's temp folder they
// were never removed -- deleteOnExit takes only an EMPTY folder -- and
// 30,640 of them had piled up there by 2026-10-23. TempPlaceTest fails if
// this is taken away.
tasks.withType<Test>().configureEach {
    val scratch = layout.buildDirectory.dir("test-tmp/$name").get().asFile
    systemProperty("java.io.tmpdir", scratch.absolutePath)
    doFirst {
        // Asked again, and said when it cannot: a file another program held
        // for a moment (a build beside it, a scanner) let one pass keep 276
        // of the last run's folders, and TempPlaceTest read them as a pile-up.
        var tries = 0
        while (scratch.exists() && !scratch.deleteRecursively() && ++tries < 5) Thread.sleep(1000)
        if (scratch.listFiles().orEmpty().isNotEmpty()) {
            throw GradleException("could not empty $scratch: a program may hold a file in it")
        }
        scratch.mkdirs()
    }
}

// The release's Kotlin is compiled whole, never incrementally. Twice
// (2026-10-23 on debug, 2026-10-24 on release) an incremental compile lost
// sight of every top-level declaration in the module's other files and
// failed with the same 226 errors in NoteEditor.kt, while a whole compile
// of the same sources passed: a fault in Kotlin's incremental compilation,
// not in the code. A failure that loud does no harm; the same fault made
// quiet would ship stale classes, so what ships is compiled whole (about
// 10 s instead of 4). Debug stays incremental. Set after evaluation: the
// Android plugin sets `incremental` itself later than a configureEach
// here, and its setting won.
afterEvaluate {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>()
        .matching { it.name == "compileReleaseKotlin" }
        .configureEach { incremental = false }
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