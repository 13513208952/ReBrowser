pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Optional verified offline mirror kept outside the source tree. The normal
        // Mozilla repository remains available for users without this local cache.
        providers.environmentVariable("REBROWSER_GECKO_MAVEN_DIR").orNull?.let { directory ->
            maven { url = uri(directory) }
        }
        google()
        mavenCentral()
        // GeckoView is published by Mozilla, not Google or Maven Central.
        maven("https://maven.mozilla.org/maven2")
    }
}

rootProject.name = "ReBrowser"
include(":app", ":compat")
