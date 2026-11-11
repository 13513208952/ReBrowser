import java.io.File

plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
}

providers.environmentVariable("REBROWSER_BUILD_ROOT").orNull?.let { root ->
    require(root.isNotBlank() && File(root).isAbsolute) { "REBROWSER_BUILD_ROOT must be absolute" }
    layout.buildDirectory.set(file(root).resolve("root"))
}
