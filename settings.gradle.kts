pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
        }
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "libinfinimeta"
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
includeBuild("../libinfinistream")
