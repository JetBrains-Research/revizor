group = rootProject.group
version = rootProject.version

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "org.jetbrains.research.marking.VariableLabelsMarking.kt"
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}

dependencies {
    implementation(project(rootProject.path))
}