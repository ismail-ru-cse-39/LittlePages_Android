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

android {
    namespace = "com.littlepages.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.littlepages.app"
        minSdk = 26
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
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
