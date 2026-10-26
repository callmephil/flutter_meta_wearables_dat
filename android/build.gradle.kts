group = "io.rodcone.flutter_meta_wearables_dat"
version = "1.0-SNAPSHOT"

val mwdatVersion = "1.0.1"

buildscript {
    val kotlinVersion = "2.4.0"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.library")
}

android {
    namespace = "io.rodcone.flutter_meta_wearables_dat"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 29
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Without this, any `android.util.Log` call reached from code under
            // test throws "not mocked" and fails the test for a reason that has
            // nothing to do with the behavior being asserted.
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()

                it.outputs.upToDateWhen { false }

                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.meta.wearable:mwdat-core:$mwdatVersion")
    implementation("com.meta.wearable:mwdat-camera:$mwdatVersion")
    // Speech (beta): on-device recognition on the glasses, attached to the same
    // DeviceSession as the camera. Pulls in nothing beyond mwdat-core.
    implementation("com.meta.wearable:mwdat-speech:$mwdatVersion")
    // Inputs (beta): the glasses' capture button, attached the same way.
    implementation("com.meta.wearable:mwdat-inputs:$mwdatVersion")
    implementation("androidx.emoji2:emoji2:1.5.0")
    // Required for ActivityResultRegistry and ComponentActivity
    implementation("androidx.activity:activity-ktx:1.9.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.mockito:mockito-core:5.0.0")
    // `useJUnitPlatform()` above has been configured since before any tests
    // existed, but without an engine on the test runtime classpath Gradle
    // discovers zero tests and reports success — so the suite silently did
    // not run. Required for the JUnit 5 annotations to execute.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    // Gradle 9 stopped supplying the launcher itself; without it the test
    // task fails with "Failed to load JUnit Platform".
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
