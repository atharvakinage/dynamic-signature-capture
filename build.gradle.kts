// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        google()  // For Firebase and Android dependencies
        mavenCentral()  // For other dependencies
        maven {url = uri("https://jitpack.io")}
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2")  // Android Gradle plugin version
    }
}

