plugins { id("com.android.application") }

android {
    namespace = "dev.hdrlab"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "dev.hdrlab.light"
        minSdk = 31
        targetSdk = 36
        versionCode = 7
        versionName = "0.1.6.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        create("release") {
            storeFile = file("hdrlab-release.keystore")
            storePassword = "hdrlab123"
            keyAlias = "hdrlab"
            keyPassword = "hdrlab123"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Local testing update: preserve the existing APK's signing identity.
            // Use a dedicated release key before distributing through an app store.
            signingConfig = signingConfigs.getByName("release")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
