plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":meta-core"))
    implementation(project(":meta-atari-lynx"))
    implementation(project(":meta-atari-atr"))
    implementation(project(":meta-sony-psx-exe"))
    implementation(project(":meta-sony-psp-pbp"))
    implementation(project(":meta-sony-pkg"))
    implementation(project(":meta-xbox-xbe"))
    implementation(project(":meta-xbox-xex"))
    implementation(project(":meta-xbox-xvd"))
    implementation(project(":meta-xbox-stfs"))
    implementation(project(":meta-snk-neo"))
    implementation(project(":meta-snk-ngp"))
    implementation(project(":meta-3do-opera"))
    implementation(project(":meta-nec-pce"))
    implementation(project(":meta-atari-jaguar"))
    implementation(project(":meta-gamepark-gp32"))
    implementation(project(":meta-apple2-prodos"))
}
