plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":meta-core"))
    implementation(project(":meta-nintendo-nes"))
    implementation(project(":meta-nintendo-gameboy"))
    implementation(project(":meta-nintendo-snes"))
    implementation(project(":meta-nintendo-n64"))
    implementation(project(":meta-nintendo-gba"))
    implementation(project(":meta-nintendo-ds"))
    implementation(project(":meta-nintendo-gamecube"))
    implementation(project(":meta-nintendo-wii"))
    implementation(project(":meta-nintendo-virtualboy"))
}
