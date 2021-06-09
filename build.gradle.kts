group = "org.jetbrains.research.revizor"
version = "1.0-SNAPSHOT"


plugins {
    java
    id("org.jetbrains.intellij") version "0.7.2"
    val kotlinVersion = "1.4.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

allprojects {
    repositories {
        jcenter()
        maven("https://kotlin.bintray.com/kotlinx")
    }
}

subprojects {
    apply {
        plugin("java")
        plugin("kotlin")
        plugin("org.jetbrains.kotlin.plugin.serialization")
        plugin("org.jetbrains.intellij")
    }

    intellij {
        type = "PC"
        version = "2020.3.3"
        setPlugins("PythonCore")
        updateSinceUntilBuild = true
    }

    dependencies {
        compileOnly(kotlin("stdlib-jdk8"))
        implementation(group = "com.github.gumtreediff", name = "core", version = "2.1.2")
        implementation(group = "org.jgrapht", name = "jgrapht-core", version = "1.5.0")
        implementation(group = "org.jgrapht", name = "jgrapht-io", version = "1.5.0")
        implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.0.0")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType<org.jetbrains.intellij.tasks.BuildSearchableOptionsTask>()
        .forEach { it.enabled = false }
}