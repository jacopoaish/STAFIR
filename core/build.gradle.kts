plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stafir.core"
    compileSdk = 35
    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":domain"))
    implementation(libs.androidx.core.ktx)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
    api(libs.paging.runtime)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)
    implementation(libs.exifinterface)
    testImplementation(libs.junit)
}
