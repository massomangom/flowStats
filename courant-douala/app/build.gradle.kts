import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase n'est activé que si app/google-services.json est présent.
// Sans ce fichier, l'appli tourne en mode local (démo) : pratique pour tester.
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) apply(plugin = "com.google.gms.google-services")

// Secrets : variables d'environnement (CI) ou keystore.properties (local).
val secrets = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String? = System.getenv(name) ?: secrets.getProperty(name)

// Identifiants de test officiels Google AdMob (aucun revenu).
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/6300978111"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

val admobAppId = secret("ADMOB_APP_ID") ?: testAppId
val admobBannerId = secret("ADMOB_BANNER_ID") ?: testBannerId
val admobInterstitialId = secret("ADMOB_INTERSTITIAL_ID") ?: testInterstitialId

android {
    namespace = "com.massomangom.courantdouala"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.massomangom.courantdouala"
        minSdk = 23
        targetSdk = 36
        versionCode = (secret("VERSION_CODE") ?: "1").toInt()
        versionName = secret("VERSION_NAME") ?: "1.0.0"
        resourceConfigurations += listOf("fr", "en")
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
    }

    signingConfigs {
        create("release") {
            val storePath = secret("KEYSTORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "BANNER_ID", "\"$testBannerId\"")
            buildConfigField("String", "INTERSTITIAL_ID", "\"$testInterstitialId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "BANNER_ID", "\"$admobBannerId\"")
            buildConfigField("String", "INTERSTITIAL_ID", "\"$admobInterstitialId\"")
            if (secret("KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Les captures (lentes, Robolectric) ne tournent que sur demande : ./gradlew testDebugUnitTest -Pscreenshots
tasks.withType<Test>().configureEach {
    if (!project.hasProperty("screenshots")) exclude("**/screenshots/**")
    else systemProperty("screenshotsDir", rootProject.file("store/screenshots").absolutePath)
}

// Garde-fou : on ne publie jamais une version release sans vraies pubs ni Firebase,
// sinon l'appli ne rapporte rien et ne partage aucune donnée entre utilisateurs.
tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (project.findProperty("allowTestConfig") == "true") return@doFirst
        val problems = buildList {
            if (admobAppId == testAppId) add("ADMOB_APP_ID manquant")
            if (admobBannerId == testBannerId) add("ADMOB_BANNER_ID manquant")
            if (admobInterstitialId == testInterstitialId) add("ADMOB_INTERSTITIAL_ID manquant")
            if (!hasFirebase) add("app/google-services.json manquant")
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Release bloquée : ${problems.joinToString()}. Voir courant-douala/README.md (ou -PallowTestConfig=true pour un test).")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    // AdMob tire une vieille version de Fragment, incompatible avec registerForActivityResult.
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    implementation("com.google.android.play:review-ktx:2.0.2")

    testImplementation("junit:junit:4.13.2")
    // Captures d'écran Play Store générées depuis les vrais écrans (./gradlew testDebugUnitTest -Pscreenshots).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
