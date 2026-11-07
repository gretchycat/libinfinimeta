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
includeBuild("../libinfinistream")
