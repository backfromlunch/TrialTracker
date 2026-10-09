import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("com.diffplug.spotless") version "8.8.0"
}
// Computed once at configuration time from the current commit's tag/sha history. Format is
// git's own: "<nearest-tag>-<commits-since-tag>-g<abbrev-sha>[-dirty]", e.g. "v1.2.0-5-gabcdef1".
// This sidesteps the chicken-and-egg problem of embedding a commit's own sha: it's derived from
// whatever HEAD is at build time, so it never needs to "know" a future commit.
val gitVersion: String =
    try {
        providers
            .exec {
                commandLine("git", "describe", "--tags", "--always", "--long", "--dirty=-dirty")
            }.standardOutput.asText
            .get()
            .trim()
    } catch (e: Exception) {
        "no-git"
    }

android {
    namespace = "dev.traumatisedturkey.trialtracker"
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    // TODO
    // change applicationId = "com.example.trialtracker" ===> "dev.traumatisedturkey.trialtracker"
    // BUT this will cause loss of access to any existing installation data
    defaultConfig {
        applicationId = "com.example.trialtracker"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "GIT_VERSION", "\"$gitVersion\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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

    sourceSets {
        // Room's exported schema snapshots (see ksp { arg("room.schemaLocation", ...) } below)
        // double as MigrationTestHelper's seed data: it reads the old-version schema JSON from
        // assets to build a database at that exact version, then runs a real Migration against
        // it. Without this, androidTest has no access to app/schemas/.
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

tasks.withType<Test>().configureEach {
    // Reliably unearths the path to 'samples/'
    systemProperty("samplesDir", rootProject.file("samples").absolutePath)
    // To get single line per passing test output, run tests with '-PverboseTests'
    testLogging {
        events("skipped", "failed")
        if (project.hasProperty("verboseTests")) {
            events("passed", "skipped", "failed")
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0").editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
}

ksp {
    // Room writes one schema snapshot per DB version here (committed to VCS). AutoMigration
    // diffs consecutive snapshots at compile time to generate purely-additive migrations
    // (e.g. new Settings column with a default) without hand-written SQL.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Splash screen
    implementation(libs.androidx.core.splashscreen)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // kotlinx.serialization
    implementation(libs.kotlinx.serialization.json)
    // material
    implementation(libs.androidx.compose.material.icons.extended)
    // markdown
    implementation(libs.markdown.renderer)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
