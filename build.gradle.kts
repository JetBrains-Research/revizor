group = "org.jetbrains.research.bug-finder"
version = "1.0-SNAPSHOT"

plugins {
    id("org.jetbrains.intellij") version "0.4.21"
    kotlin("jvm") version "1.3.72"
    java
}

intellij {
    version = "2020.2"
    type = "PY"
    setPlugins("Pythonid")
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://kotlin.bintray.com/kotlinx")
    }

    apply {
        plugin("kotlin")
        plugin("java")
    }

    dependencies {
        compileOnly(kotlin("stdlib"))
        compileOnly(kotlin("stdlib-jdk8"))
        implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
        implementation(group = "org.jgrapht", name = "jgrapht-core", version = "1.5.0")
        implementation(group = "org.jgrapht", name = "jgrapht-io", version = "1.5.0")
        implementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.6.2")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType<Test> {
        useJUnit()
    }
}

tasks.withType<org.jetbrains.intellij.tasks.BuildSearchableOptionsTask>().forEach { it.enabled = false }