pluginManagement {
    resolutionStrategy.eachPlugin {
        if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:8.9.2")
        if (requested.id.id.startsWith("org.jetbrains.kotlin.")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.omicronapps") } }
    }
}
rootProject.name = "libinfinimeta"

// Read only this checkout's SDK configuration. Composite builds share the resolved SDK.
if (System.getenv("ANDROID_HOME").isNullOrBlank() && System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() && System.getProperty("android.home").isNullOrBlank()) {
    val local = file("local.properties")
    if (local.isFile) java.util.Properties().apply { local.inputStream().use { load(it) } }
        .getProperty("sdk.dir")?.let { System.setProperty("android.home", file(it).absolutePath) }
}
val dependencyRoot = System.getProperty("infini.dependencies")?.let(::file) ?: file("dependencies")
System.setProperty("infini.dependencies", dependencyRoot.canonicalPath)
includeBuild(dependencyRoot.resolve("libinfinistream"))
include(":meta-core", ":meta-sega-common", ":meta-sega-megadrive", ":meta-sega-smd", ":meta-sega-sms", ":meta-sega-gamegear",
    ":meta-sega-sg1000", ":meta-sega-cd", ":meta-sega-saturn", ":meta-sega-dreamcast", ":meta-sega-vmu", ":meta-sega")
include(":meta-nintendo-common", ":meta-nintendo-nes", ":meta-nintendo-gameboy", ":meta-nintendo-snes",
    ":meta-nintendo-n64", ":meta-nintendo-gba", ":meta-nintendo-ds", ":meta-nintendo-gamecube",
    ":meta-nintendo-wii", ":meta-nintendo-virtualboy", ":meta-nintendo")
include(":meta-atari-lynx", ":meta-atari-atr", ":meta-sony-psx-exe", ":meta-sony-psp-pbp", ":meta-sony-pkg",
    ":meta-xbox-xbe", ":meta-xbox-xex", ":meta-xbox-xvd", ":meta-xbox-stfs", ":meta-snk-neo", ":meta-snk-ngp", ":meta-consoles")
include(":meta-3do-opera")
include(":meta-nec-pce", ":meta-atari-jaguar", ":meta-gamepark-gp32")
include(":meta-apple2-prodos")
