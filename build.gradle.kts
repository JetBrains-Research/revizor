group = "org.jetbrains.research.bug-finder"
version = "1.0-SNAPSHOT"

plugins {
    java
    kotlin("jvm") version "1.4.10"
    id("org.jetbrains.intellij") version "0.4.21"
    kotlin("plugin.serialization") version "1.4.10"
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

    dependencies {
        compileOnly(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = "1.4.10")
        implementation(group = "com.github.gumtreediff", name = "core", version = "2.1.2")
        implementation(group = "org.jgrapht", name = "jgrapht-core", version = "1.5.0")
        implementation(group = "org.jgrapht", name = "jgrapht-io", version = "1.5.0")
        implementation("org.jetbrains.kotlinx", "kotlinx-serialization-json-jvm", "1.0.0")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            languageVersion = "1.4"
            apiVersion = "1.4"
        }
    }

    tasks.withType<org.jetbrains.intellij.tasks.BuildSearchableOptionsTask>()
        .forEach { it.enabled = false }
}