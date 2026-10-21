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
}.getProperty("supabase.url") ?: env["SUPABASE_URL"].orEmpty()

android {
    namespace = "com.thanhnb.novelnative"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.thanhnb.novelnative"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${env["SUPABASE_ANON_KEY"].orEmpty()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // ponytail: ký bằng debug key cho bản thử, cần keystore thật nếu phát hành
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
}
