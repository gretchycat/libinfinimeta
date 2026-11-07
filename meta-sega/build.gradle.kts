plugins { id("org.jetbrains.kotlin.jvm"); `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":meta-core"))
    implementation(project(":meta-sega-megadrive"))
    implementation(project(":meta-sega-smd"))
    implementation(project(":meta-sega-sms"))
    implementation(project(":meta-sega-gamegear"))
    implementation(project(":meta-sega-sg1000"))
    implementation(project(":meta-sega-cd"))
    implementation(project(":meta-sega-saturn"))
    implementation(project(":meta-sega-dreamcast"))
    implementation(project(":meta-sega-vmu"))
    testImplementation("junit:junit:4.13.2")
}
