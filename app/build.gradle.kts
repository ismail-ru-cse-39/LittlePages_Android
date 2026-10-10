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

// AGP 9.2 does not expose the outputFileName property yet, so keep AGP's
// normal artifact intact and create versioned copies after APK builds.
val versionedDebugApk = tasks.register<Copy>("copyVersionedDebugApk") {
    val apkName = "LittlePageApp_${buildVersionName}.apk"
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("outputs/named-apk/debug"))
    rename { apkName }
}

val versionedReleaseApk = tasks.register<Copy>("copyVersionedReleaseApk") {
    val apkName = "LittlePageApp_${buildVersionName}.apk"
    from(fileTree(layout.buildDirectory.dir("outputs/apk/release")) { include("*.apk") })
    into(layout.buildDirectory.dir("outputs/named-apk/release"))
    rename { apkName }
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
    finalizedBy(versionedDebugApk)
    finalizedBy(incrementVersionForNextApk)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(versionedReleaseApk)
    finalizedBy(incrementVersionForNextApk)
}
