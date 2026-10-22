import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Dùng chung endpoint với app Flutter (../Novel_Persona/app/.env) để so trên cùng dữ liệu.
val env = rootProject.file("../Novel_Persona/app/.env").takeIf { it.exists() }?.readLines()
    ?.mapNotNull { line -> line.split("=", limit = 2).takeIf { !line.startsWith("#") && it.size == 2 } }
    ?.associate { (k, v) -> k.trim() to v.trim() }
    .orEmpty()
// Bản phát hành trỏ endpoint public: supabase.url=https://... trong local.properties
val supabaseUrl = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}.getProperty("supabase.url") ?: System.getenv("SUPABASE_URL") ?: env["SUPABASE_URL"].orEmpty()
// CI (release.yml) lấy từ GitHub Secrets; máy dev đọc .env của Flutter
val anonKey = System.getenv("SUPABASE_ANON_KEY") ?: env["SUPABASE_ANON_KEY"].orEmpty()
// Ký phát hành: CI giải keystore từ secret ra file rồi đặt ANDROID_KEYSTORE_FILE/ANDROID_KEYSTORE_PASSWORD (alias "upload")
val keystoreFile: String? = System.getenv("ANDROID_KEYSTORE_FILE")

android {
    namespace = "com.thanhnb.novelnative"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.thanhnb.novelnative"
        minSdk = 26
        targetSdk = 36
        // CI truyền -PversionName từ tag, -PversionCode từ số lần chạy
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 2
        versionName = findProperty("versionName") as String? ?: "0.2.0"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$anonKey\"")
    }

    signingConfigs {
        create("upload") {
            keystoreFile?.let { storeFile = file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Máy dev: ký debug để thử bản R8; CI phát hành: keystore thật (cập nhật cài đè cần cùng chữ ký)
            signingConfig = signingConfigs.getByName(if (keystoreFile != null) "upload" else "debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
}
