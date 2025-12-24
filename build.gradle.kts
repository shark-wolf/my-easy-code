plugins {
    kotlin("jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.17.3"
}

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.2")
    type.set("IU")
    pluginName.set("my-easy-code")
    plugins.set(listOf("com.intellij.java", "com.intellij.database"))
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.freemarker:freemarker:2.3.31")
    implementation("com.mysql:mysql-connector-j:8.3.0")
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("")
    }
}
