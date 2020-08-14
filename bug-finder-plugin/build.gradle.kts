plugins {
    id("org.jetbrains.intellij") version "0.4.21"
    java
    kotlin("jvm") version "1.3.72"
}

group = "org.jetbrains.research"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(group = "org.jgrapht", name = "jgrapht-core", version = "1.5.0")
    implementation(group = "org.jgrapht", name = "jgrapht-io", version = "1.5.0")
    implementation("org.junit.jupiter:junit-jupiter:5.4.2")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    setPlugins("Pythonid")
    version = "2020.2"
    type = "PY"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}