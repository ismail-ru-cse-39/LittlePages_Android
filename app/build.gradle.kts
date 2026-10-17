import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionFile = rootProject.file("version.properties")
val configuredVersion = Properties().apply {
    if (versionFile.exists()) versionFile.inputStream().use { load(it) }
}
val buildVersionName = configuredVersion.getProperty("VERSION_NAME", "1.2")
val buildVersionCode = configuredVersion.getProperty("VERSION_CODE", "3").toInt()
val localEnvironmentProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { input -> load(input) }
}
fun environmentProperty(name: String): String = providers.gradleProperty(name).orNull
    ?: localEnvironmentProperties.getProperty(name)
    ?: ""
fun asBuildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val firebaseEnvironment = environmentProperty("littlepages.environment").ifBlank { "development" }
require(firebaseEnvironment in setOf("development", "staging", "production")) {
    "littlepages.environment must be development, staging, or production."
}
val firebaseProjectId = environmentProperty("firebase.$firebaseEnvironment.projectId")
val firebaseApplicationId = environmentProperty("firebase.$firebaseEnvironment.appId")
val firebaseApiKey = environmentProperty("firebase.$firebaseEnvironment.apiKey")
val firebaseSenderId = environmentProperty("firebase.$firebaseEnvironment.messagingSenderId")
val firebaseConfigured = firebaseProjectId.isNotBlank() && firebaseApplicationId.isNotBlank() &&
    firebaseApiKey.isNotBlank() && firebaseSenderId.isNotBlank()

android {
    namespace = "com.littlepages.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.littlepages.app"
        minSdk = 26
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = buildVersionName
        buildConfigField("String", "FIREBASE_ENVIRONMENT", asBuildConfigString(firebaseEnvironment))
        buildConfigField("String", "FIREBASE_PROJECT_ID", asBuildConfigString(firebaseProjectId))
        buildConfigField("String", "FIREBASE_APP_ID", asBuildConfigString(firebaseApplicationId))
        buildConfigField("String", "FIREBASE_API_KEY", asBuildConfigString(firebaseApiKey))
        buildConfigField("String", "FIREBASE_MESSAGING_SENDER_ID", asBuildConfigString(firebaseSenderId))
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.19.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("LittlePageApp_${buildVersionName}.apk")
        }
    }
}

fun nextVersionName(version: String): String {
    val parts = version.split(".")
    val lastNumber = parts.last().toIntOrNull()
        ?: error("The last part of VERSION_NAME must be a number: $version")
    return (parts.dropLast(1) + (lastNumber + 1).toString()).joinToString(".")
}

val incrementVersionForNextApk = tasks.register("incrementVersionForNextApk") {
    inputs.property("buildVersionName", buildVersionName)
    inputs.property("buildVersionCode", buildVersionCode)
    outputs.file(versionFile)
    doLast {
        val nextVersion = nextVersionName(buildVersionName)
        versionFile.writeText(
            "# Updated automatically after assembling an APK.\n" +
                "VERSION_NAME=$nextVersion\n" +
                "VERSION_CODE=${buildVersionCode + 1}\n"
        )
        logger.lifecycle("Next APK version: $nextVersion")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(incrementVersionForNextApk)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(incrementVersionForNextApk)
}
