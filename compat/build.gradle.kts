import java.io.File

plugins {
    id("com.android.application")
}

// The compatibility engine is kept in a separate application module. Never allow
// its native libraries, engine data, or incomplete UI to enter the WebView APKs.
providers.environmentVariable("REBROWSER_BUILD_ROOT").orNull?.let { root ->
    require(root.isNotBlank() && File(root).isAbsolute) { "REBROWSER_BUILD_ROOT must be absolute" }
    layout.buildDirectory.set(file(root).resolve("compat"))
}

android {
    // Keep the source package and generated R identical to :app so native views
    // and their existing drawables compile unchanged. applicationId stays isolated.
    namespace = "io.github.reoutlook"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rebrowser.compat"
        minSdk = 29
        targetSdk = 36
        versionName = "1.0.6"
        buildConfigField("boolean", "FULL_SUITE", "true")
    }

    // Mozilla publishes independent AARs per CPU. Separate product flavors
    // avoid even downloading the other architectures for an individual build.
    // Current GeckoView does not publish a 32-bit x86 AAR.
    flavorDimensions += "cpu"
    productFlavors {
        create("armv7") {
            dimension = "cpu"
            versionCode = 1060001
            ndk { abiFilters += "armeabi-v7a" }
        }
        create("arm64") {
            dimension = "cpu"
            versionCode = 1060002
            ndk { abiFilters += "arm64-v8a" }
        }
        create("x86_64") {
            dimension = "cpu"
            versionCode = 1060003
            ndk { abiFilters += "x86_64" }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    // Stage ONLY engine-independent source files outside the worktree.
    // AGP ignores source-root include patterns, so adding :app/src/main/java
    // directly would compile the entire WebView application into this APK.
    sourceSets.getByName("main").java.srcDir(
        layout.buildDirectory.dir("generated/shared-workspace-sources"))
    sourceSets.getByName("main").res.srcDirs("../app/src/main/res", "../app/src/full/res")
    // Compat is always the photographic full suite. Keep the background flavor's
    // brand graphics above main resources in Android's normal overlay priority.
    listOf("debug", "release").forEach {
        sourceSets.getByName(it).res.srcDir("../app/src/background/res")
    }
    sourceSets.getByName("main").assets.srcDir(
        layout.buildDirectory.dir("generated/outlook-bridge-assets"))
    sourceSets.getByName("test").java.srcDir(
        layout.buildDirectory.dir("generated/shared-workspace-tests"))

    buildFeatures { buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val stageSharedWorkspaceSources = tasks.register<Sync>("stageSharedWorkspaceSources") {
    from("../app/src/main/java") {
        include("io/github/reoutlook/CachedMessage.java")
        include("io/github/reoutlook/CaptureScript.java")
        include("io/github/reoutlook/ConversationCandidate.java")
        include("io/github/reoutlook/MailDatabase.java")
        include("io/github/reoutlook/MainActivity.java")
        include("io/github/reoutlook/MaintenanceKeys.java")
        include("io/github/reoutlook/ArchiveActivity.java")
        include("io/github/reoutlook/ReBrowserActivity.java")
        include("io/github/reoutlook/ReBrowserAdminController.java")
        include("io/github/reoutlook/ReBrowserAdminKeys.java")
        include("io/github/reoutlook/ReBrowserAdminProtocol.java")
        include("io/github/reoutlook/ReBrowserApplications.java")
        include("io/github/reoutlook/ReBrowserClipboardGesture.java")
        include("io/github/reoutlook/ReBrowserDefaultBrowser.java")
        include("io/github/reoutlook/ReBrowserDevice.java")
        include("io/github/reoutlook/ReBrowserDownloadController.java")
        include("io/github/reoutlook/ReBrowserDownloads.java")
        include("io/github/reoutlook/ReBrowserExternalNavigation.java")
        include("io/github/reoutlook/ReBrowserFavorites.java")
        include("io/github/reoutlook/ReBrowserHistory.java")
        include("io/github/reoutlook/ReBrowserHistoryIcon.java")
        include("io/github/reoutlook/ReBrowserHistoryView.java")
        include("io/github/reoutlook/ReBrowserLauncherActivity.java")
        include("io/github/reoutlook/ReBrowserLinkActivity.java")
        include("io/github/reoutlook/ReBrowserLoadingBar.java")
        include("io/github/reoutlook/ReBrowserMusicEntrance.java")
        include("io/github/reoutlook/ReBrowserMusicLifecycle.java")
        include("io/github/reoutlook/ReBrowserMusicPolicy.java")
        include("io/github/reoutlook/ReBrowserMusicSnapshot.java")
        include("io/github/reoutlook/ReBrowserMusicSources.java")
        include("io/github/reoutlook/ReBrowserNewTabBackground.java")
        include("io/github/reoutlook/ReBrowserNewTabView.java")
        include("io/github/reoutlook/ReBrowserOverviewController.java")
        include("io/github/reoutlook/ReBrowserPageDownloadBridge.java")
        include("io/github/reoutlook/ReBrowserPermissionRequest.java")
        include("io/github/reoutlook/ReBrowserPlayerCompatibility.java")
        include("io/github/reoutlook/ReBrowserPreferences.java")
        include("io/github/reoutlook/ReBrowserSearchEngineAssets.java")
        include("io/github/reoutlook/ReBrowserSettings.java")
        include("io/github/reoutlook/ReBrowserSettingsActivity.java")
        include("io/github/reoutlook/ReBrowserSitePermissions.java")
        include("io/github/reoutlook/ReBrowserSitePermissionsActivity.java")
        include("io/github/reoutlook/ReBrowserSitePermissionsView.java")
        include("io/github/reoutlook/ReBrowserStorageEstimate.java")
        include("io/github/reoutlook/ReBrowserStore.java")
        include("io/github/reoutlook/ReBrowserSubapplicationGate.java")
        include("io/github/reoutlook/ReBrowserUi.java")
        include("io/github/reoutlook/ReBrowserWorkspaceController.java")
        include("io/github/reoutlook/ReOutlookLauncherVisibility.java")
        include("io/github/reoutlook/ReVideoShortcutActivity.java")
        include("io/github/reoutlook/SystemBackDispatcher.java")
        include("io/github/reoutlook/WindowStyling.java")
    }
    from("../app/src/full/java") {
        include("io/github/reoutlook/ReBrowserMusicAccountCheck.java")
        include("io/github/reoutlook/ReBrowserMusicActivity.java")
        include("io/github/reoutlook/ReBrowserMusicArtwork.java")
        include("io/github/reoutlook/ReBrowserMusicControlReceiver.java")
        include("io/github/reoutlook/ReBrowserMusicEvaluation.java")
        include("io/github/reoutlook/ReBrowserMusicInitialization.java")
        include("io/github/reoutlook/ReBrowserMusicLatestTask.java")
        include("io/github/reoutlook/ReBrowserMusicLyricOverlay.java")
        include("io/github/reoutlook/ReBrowserMusicLyrics.java")
        include("io/github/reoutlook/ReBrowserMusicLyricWindow.java")
        include("io/github/reoutlook/ReBrowserMusicPlaybackAuthorization.java")
        include("io/github/reoutlook/ReBrowserMusicPreparation.java")
        include("io/github/reoutlook/ReBrowserMusicSeekDiagnostics.java")
        include("io/github/reoutlook/ReBrowserMusicService.java")
        include("io/github/reoutlook/ReBrowserMusicSystemIcons.java")
        include("io/github/reoutlook/ReBrowserSubapplicationSettingsActivity.java")
    }
    from("../app/src/background/java") {
        include("io/github/reoutlook/ReBrowserBackgroundAsset.java")
    }
    into(layout.buildDirectory.dir("generated/shared-workspace-sources"))
}
val stageSharedWorkspaceTests = tasks.register<Sync>("stageSharedWorkspaceTests") {
    from("../app/src/testFull/java") {
        include("io/github/reoutlook/ReBrowserMusicAccountCheckTest.java")
        include("io/github/reoutlook/ReBrowserMusicArtworkTest.java")
        include("io/github/reoutlook/ReBrowserMusicEvaluationTest.java")
        include("io/github/reoutlook/ReBrowserMusicInitializationTest.java")
        include("io/github/reoutlook/ReBrowserMusicLatestTaskTest.java")
        include("io/github/reoutlook/ReBrowserMusicLyricOverlayTest.java")
        include("io/github/reoutlook/ReBrowserMusicLyricsTest.java")
        include("io/github/reoutlook/ReBrowserMusicLyricWindowTest.java")
        include("io/github/reoutlook/ReBrowserMusicPlaybackAuthorizationTest.java")
        include("io/github/reoutlook/ReBrowserMusicPreparationTest.java")
        include("io/github/reoutlook/ReBrowserMusicSeekDiagnosticsTest.java")
        include("io/github/reoutlook/ReBrowserMusicSystemIconsTest.java")
        include("io/github/reoutlook/ReBrowserStorageEstimateTest.java")
    }
    from("../app/src/test/java") {
        include("io/github/reoutlook/ReBrowserWorkspaceControllerTest.java")
        include("io/github/reoutlook/ReBrowserHistoryTest.java")
        include("io/github/reoutlook/ReBrowserHistoryViewTest.java")
        include("io/github/reoutlook/ReBrowserNewTabTest.java")
        include("io/github/reoutlook/ReBrowserSitePermissionsTest.java")
        include("io/github/reoutlook/ReBrowserMusicPolicyTest.java")
        include("io/github/reoutlook/ReBrowserMusicLifecycleTest.java")
        include("io/github/reoutlook/ReBrowserMusicSnapshotTest.java")
    }
    into(layout.buildDirectory.dir("generated/shared-workspace-tests"))
}
tasks.withType<JavaCompile>().configureEach {
    dependsOn(stageSharedWorkspaceSources, stageSharedWorkspaceTests)
}

// Decode the shared Java text blocks with javac, then package fixed script files.
// MAIN-world document-start injection works under Outlook CSP without eval or a
// privileged arbitrary-script command. The page world receives no browser API.
val compileOutlookBridgeGenerator = tasks.register<JavaCompile>("compileOutlookBridgeGenerator") {
    source("../app/src/main/java/io/github/reoutlook/CaptureScript.java",
        "tools/GenerateOutlookBridge.java")
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("generated/outlook-bridge-generator"))
    options.release.set(17)
}
val generateOutlookBridge = tasks.register<JavaExec>("generateOutlookBridge") {
    dependsOn(compileOutlookBridgeGenerator)
    classpath = files(compileOutlookBridgeGenerator.flatMap { it.destinationDirectory })
    mainClass.set("io.github.reoutlook.GenerateOutlookBridge")
    args(layout.buildDirectory.dir("generated/outlook-bridge-assets").get().asFile.absolutePath)
    inputs.files("../app/src/main/java/io/github/reoutlook/CaptureScript.java",
        "tools/GenerateOutlookBridge.java")
    outputs.dir(layout.buildDirectory.dir("generated/outlook-bridge-assets"))
}
tasks.named("preBuild").configure { dependsOn(generateOutlookBridge) }

dependencies {
    // Temporary API-compatible pin for AGP 8.9.1/SDK 36. Revisit Gecko
    // security support before release; this is not an approved full suite.
    add("armv7Implementation", "org.mozilla.geckoview:geckoview-armeabi-v7a:149.0.20260403140140")
    add("arm64Implementation", "org.mozilla.geckoview:geckoview-arm64-v8a:149.0.20260403140140")
    add("x86_64Implementation", "org.mozilla.geckoview:geckoview-x86_64:149.0.20260403140140")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
