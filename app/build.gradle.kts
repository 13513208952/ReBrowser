import java.io.File

plugins {
    id("com.android.application")
}

// All generated artifacts for the 1.06-alpha1 workspace can be redirected outside
// the source tree. Gradle's project cache and user home must also be redirected
// with --project-cache-dir and GRADLE_USER_HOME when invoking the wrapper.
providers.environmentVariable("REBROWSER_BUILD_ROOT").orNull?.let { root ->
    require(root.isNotBlank() && File(root).isAbsolute) { "REBROWSER_BUILD_ROOT must be absolute" }
    layout.buildDirectory.set(file(root).resolve("app"))
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
        versionCode = 106
        versionName = "1.0.5"
    }

    flavorDimensions += listOf("suite", "background")
    productFlavors {
        create("minimal") {
            dimension = "suite"
            versionCode = 106
            buildConfigField("boolean", "FULL_SUITE", "false")
        }
        create("full") {
            dimension = "suite"
            // Private settings previews used 1_000_106; formal release supersedes them.
            versionCode = 1_000_107
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
    add("fullImplementation", project(":revideo-platform"))

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
