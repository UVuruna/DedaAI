/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.meta.wearable.dat.externalsampleapps.cameraaccess"
  compileSdk = 35

  buildFeatures { buildConfig = true }

  defaultConfig {
    // The Java package (namespace above) keeps the DAT sample's path; the
    // applicationId is the product's own. Android therefore sees DedaAI as a
    // NEW app — the old sample install stays until the owner removes it.
    applicationId = "com.uvuruna.dedaai"
    minSdk = 31
    targetSdk = 34
    versionCode = 2
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
    // Both target phones (the S25 Ultra and the second user's) are arm64;
    // shipping one ABI keeps the APK small.
    ndk { abiFilters += "arm64-v8a" }
  }

  signingConfigs {
    getByName("debug") {
      storeFile = file("sample.keystore")
      storePassword = "sample"
      keyAlias = "sample"
      keyPassword = "sample"
    }
    // Real release signing, read from four environment variables so no
    // secret ever lands in the repo:
    //   DEDA_KEYSTORE       absolute path to the release .jks keystore
    //   DEDA_KEYSTORE_PASS  keystore password
    //   DEDA_KEY_ALIAS      key alias (defaults to "dedaai")
    //   DEDA_KEY_PASS       key password (defaults to DEDA_KEYSTORE_PASS)
    // With DEDA_KEYSTORE unset, release falls back to the debug keystore
    // below, so local builds keep working.
    create("release") {
      val ksPath = System.getenv("DEDA_KEYSTORE")
      if (ksPath != null) {
        storeFile = file(ksPath)
        storePassword = System.getenv("DEDA_KEYSTORE_PASS")
        keyAlias = System.getenv("DEDA_KEY_ALIAS") ?: "dedaai"
        keyPassword = System.getenv("DEDA_KEY_PASS") ?: System.getenv("DEDA_KEYSTORE_PASS")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig =
          if (System.getenv("DEDA_KEYSTORE") != null) signingConfigs.getByName("release")
          else signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.1" }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.androidx.material3)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.mwdat.core)
  implementation(libs.mwdat.camera)
  implementation(libs.mwdat.mockdevice)
  // DedaAI additions
  implementation(libs.okhttp)
  implementation(libs.camerax.core)
  implementation(libs.camerax.camera2)
  implementation(libs.camerax.lifecycle)
  implementation(libs.camerax.view)
  implementation(libs.datastore.preferences)
  implementation(libs.lifecycle.process)
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.uiautomator)
  androidTestImplementation(libs.androidx.test.rules)
}
