// Igual que TV Lite: AGP 9 compila Kotlin por su cuenta y no hay Compose.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ar.carta.tvliterca"
    compileSdk = 37

    defaultConfig {
        // Otro id que TV Lite, para que nunca se pisen si alguna vez
        // terminan en el mismo equipo.
        applicationId = "ar.carta.tvliterca"
        // La gran diferencia con TV Lite. El TV box conectado al RCA
        // es un Android chino generico, y no sabemos cual. 19 es
        // Android 4.4: cubre todo lo que razonablemente puede tener. Lo que
        // es de versiones nuevas se usa solo detras de un chequeo de version.
        minSdk = 19
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Clave de depuracion a proposito: se instala a mano, nunca va a
            // Play, y asi no hay keystore que administrar.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// Sin dependencias, como TV Lite. Ver el README.
dependencies { }
