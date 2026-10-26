group = "io.rodcone.flutter_meta_wearables_dat_mock_device"
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
    namespace = "io.rodcone.flutter_meta_wearables_dat_mock_device"

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
    // mwdat-core provides the Permission / PermissionStatus types reused
    // by this add-on, plus the Wearables singleton MockDeviceKit registers
    // its mock devices into. The core plugin already pulls it transitively,
    // but declaring it here keeps this module compilable in isolation.
    implementation("com.meta.wearable:mwdat-core:$mwdatVersion")
    implementation("com.meta.wearable:mwdat-mockdevice:$mwdatVersion")
    // MockInputKit takes the Inputs types (CapturePressType); the mock
    // artifact does not depend on mwdat-inputs itself.
    implementation("com.meta.wearable:mwdat-inputs:$mwdatVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.0.0")
}
