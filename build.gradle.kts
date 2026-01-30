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
    // 通过 compileOnly 避免将 Kotlin 标准库打包到插件中
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk7")

    implementation("org.yaml:snakeyaml:2.2")
    implementation("org.freemarker:freemarker:2.3.31")
    // MySQL 连接器仅在需要时才包含，避免不必要的依赖
    implementation("com.mysql:mysql-connector-j:8.3.0")
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("")
    }

    // 配置 buildSearchableOptions 任务，仅在发布构建时运行
    buildSearchableOptions {
        enabled = false  // 在开发期间禁用以加快构建速度
    }
}
