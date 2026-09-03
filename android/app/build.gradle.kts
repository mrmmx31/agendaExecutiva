plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val releaseSigningEnvironment = mapOf(
    "storeFile" to System.getenv("AGENDA_RELEASE_STORE_FILE"),
    "storePassword" to System.getenv("AGENDA_RELEASE_STORE_PASSWORD"),
    "keyAlias" to System.getenv("AGENDA_RELEASE_KEY_ALIAS"),
    "keyPassword" to System.getenv("AGENDA_RELEASE_KEY_PASSWORD"),
)
val releaseSigningConfigured = releaseSigningEnvironment.values.all { !it.isNullOrBlank() }
require(releaseSigningEnvironment.values.none { !it.isNullOrBlank() } || releaseSigningConfigured) {
    "Configure todas as variáveis AGENDA_RELEASE_* ou nenhuma delas."
}

android {
    namespace = "com.pessoal.agenda.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pessoal.agenda.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        manifestPlaceholders["appLabel"] = "Agenda Sensorial"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("releaseEnvironment") {
                storeFile = file(requireNotNull(releaseSigningEnvironment["storeFile"]))
                storePassword = releaseSigningEnvironment["storePassword"]
                keyAlias = releaseSigningEnvironment["keyAlias"]
                keyPassword = releaseSigningEnvironment["keyPassword"]
            }
        }
    }

    buildTypes {
        create("fieldTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".fieldtest"
            versionNameSuffix = "-fieldtest"
            manifestPlaceholders["appLabel"] = "Agenda Sensorial - Teste"
            matchingFallbacks += listOf("debug")
        }
        release {
            signingConfig = signingConfigs.findByName("releaseEnvironment")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("test").resources.srcDir("$rootDir/contracts")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation(project(":wear-contract"))
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.health.connect:connect-client:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.work:work-testing:2.9.1")
    testImplementation("org.robolectric:robolectric:4.14.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
    androidTestImplementation("com.google.guava:guava:31.1-android")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
