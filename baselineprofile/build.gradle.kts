import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Sinh baseline profile cho :app trên máy ảo/máy thật API 33+ — xem tools/gen_baseline_profile.ps1.
// Không dùng plugin androidx.baselineprofile: 1.5.0 vỡ với AGP 9.1 + kotlin.android rời ("provider(?)" thành đường dẫn).
// shortcut: chạy trên bản debug (không R8) — khi bật android.builtInKotlin thì thử lại plugin để sinh từ bản release.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.thanhnb.novelnative.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    // Macrobenchmark chạy ở tiến trình riêng: nó force-stop app đích nhiều lần
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
