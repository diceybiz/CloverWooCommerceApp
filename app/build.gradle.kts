import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}



android {
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    signingConfigs {
        create("releaseConfig") {
            storeFile = file("C:\\Users\\super\\AndroidStudioProjects\\CloverWooCommerceApp\\my-release-key3.jks")
            storePassword = "woopass1!"
            keyAlias = "my-key-alias"
            keyPassword = "woopass2!"
            enableV1Signing = true
            enableV2Signing = false
        }
    }
    namespace = "com.example.cloverwoocommerceapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.DiceyCredit"
        minSdk = 24
        targetSdk = 30
        versionCode = 19
        versionName = "3.11"
        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val buildDate = dateFormat.format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

    }

    @Suppress("DEPRECATION") // Silence the deprecation warning
    flavorDimensions("sdkDimension")
    productFlavors {
        create("development") {
            dimension = "sdkDimension"
            targetSdk = 29
        }
        create("clover") {
            dimension = "sdkDimension"
            targetSdk = 29
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("releaseConfig")
        }
        debug {
            isMinifyEnabled = false
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
        dataBinding = true
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("com.google.code.gson:gson:2.9.1")
    }
}

dependencies {

    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(libs.androidx.lifecycle.runtime.ktx.v287)
    implementation(libs.androidx.activity.compose.v1100)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(libs.ui.test.junit4)
    implementation(libs.androidx.databinding.runtime)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx")
    implementation(libs.androidx.lifecycle.extensions)
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation(libs.clover.android.sdk)
    implementation(libs.clover.android.loyalty.kit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation("com.squareup.retrofit2:converter-gson:2.10.0")
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.gson)
    implementation("com.jakewharton.timber:timber:5.0.1")



    implementation(libs.junit)
    implementation(libs.androidx.junit.v115)
    implementation(libs.androidx.espresso.core)
    implementation("androidx.compose.ui:ui-test-junit4")

    implementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-test-manifest")
}
