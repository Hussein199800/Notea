import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties
import java.time.Duration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.notea.app"
    minSdk = 24
    targetSdk = 36
    // Version lives in 3 places, checked by VersionMatchTest: here, core/Changelog.kt, release-notes.md.
    versionName = "1.4.0"
    // versionCode = version×10 + abi (universal 0, armeabi-v7a 1, arm64-v8a 2); 1.2.0 → 102000.
    versionCode = noteaBaseCode(versionName!!) * 10

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing: the keystore is NEVER committed. Provide it via keystore.properties
  // (gitignored, see keystore.properties.example) or NOTEA_* environment variables (CI).
  val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
  }
  fun signing(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
  val releaseStore = signing("storeFile", "NOTEA_KEYSTORE_PATH")
  signingConfigs {
    if (releaseStore != null) {
      create("release") {
        storeFile = rootProject.file(releaseStore)
        storePassword = signing("storePassword", "NOTEA_KEYSTORE_PASSWORD")
        keyAlias = signing("keyAlias", "NOTEA_KEY_ALIAS") ?: "notea"
        keyPassword = signing("keyPassword", "NOTEA_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
    debug {
      // Phones (arm) + emulators (x86_64). Release builds use ABI splits instead.
      ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
  }
  // Release: one APK per phone ABI + a universal Notea.apk (the fixed download link).
  splits {
    abi {
      isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
      reset()
      include("armeabi-v7a", "arm64-v8a")
      isUniversalApk = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  // Room schema JSONs for migration tests (Robolectric unit tests read debug assets).
  sourceSets { getByName("debug").assets.directories.add("$projectDir/schemas") }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters.firstOrNull { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
      val digit = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2)[abi] ?: 0
      output.versionCode.set(noteaBaseCode(android.defaultConfig.versionName!!) * 10 + digit)
    }
  }
}

/** 1.2.0 → 10200 (major×10000 + minor×100 + patch). Mirrors UpdateRules.baseCode. */
fun noteaBaseCode(v: String): Int {
  val p = v.substringBefore('-').split('.').map { it.toInt() } + listOf(0, 0)
  return p[0] * 10000 + p[1] * 100 + p[2]
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.generateKotlin", "true")
}

// Firebase is optional: without app/google-services.json the app runs fully offline (no account).
googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.coil.svg)
  implementation(libs.tesseract4android)
  implementation(libs.zxing.core)
  implementation(libs.mlkit.document.scanner)
  implementation(libs.gms.code.scanner)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.firebase.firestore)

  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.work.testing)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

tasks.withType<Test> {
  failFast = false
  timeout.set(Duration.ofMinutes(2))
  testLogging {
    events("passed", "failed", "skipped")
  }
}
