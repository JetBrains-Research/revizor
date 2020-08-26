group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(rootProject.path))
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-cli", version = "0.3")
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "org.jetbrains.research.marking.ApplicationKt"
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}