import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val tmdbKey = localProperties.getProperty("TMDB_API_KEY", "")

android {
    namespace = "tv.noirstream"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.noirstream"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
