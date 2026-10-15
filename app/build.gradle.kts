plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.reoutlook"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.reoutlook"
        minSdk = 29
        targetSdk = 36
        // Minimal codes must remain below the full range forever. Normal Android install
        // rejects full -> minimal as a version downgrade, including newer minimal releases.
        versionCode = 101
        versionName = "1.0.3-beta"
    }

    flavorDimensions += listOf("suite", "background")
    productFlavors {
        create("minimal") {
            dimension = "suite"
            versionCode = 101
            buildConfigField("boolean", "FULL_SUITE", "false")
        }
        create("full") {
            dimension = "suite"
            versionCode = 1_000_101
            buildConfigField("boolean", "FULL_SUITE", "true")
        }
        create("lite") { dimension = "background" }
        create("background") { dimension = "background" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        resources {
            // This Java-only app has no kotlin-reflect; R8 retains no Kotlin runtime classes.
            // Reevaluate if future features introduce Kotlin runtime reflection.
            excludes += setOf("kotlin/*.kotlin_builtins", "kotlin/**/*.kotlin_builtins")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        if (variant.productFlavors.contains("suite" to "full") &&
            variant.productFlavors.contains("background" to "lite")) variant.enable = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    providers.gradleProperty("robolectricRepoUrl").orNull?.let {
        systemProperty("robolectric.dependency.repo.url", it)
    }
    providers.gradleProperty("robolectricDependencyDir").orNull?.let {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", it)
    }
}
