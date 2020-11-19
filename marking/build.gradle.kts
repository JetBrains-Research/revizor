group = rootProject.group
version = rootProject.version

intellij {
    version = "2020.2"
    type = "PY"
    setPlugins("Pythonid")
}

dependencies {
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-cli-jvm", version = "0.3")
    implementation(project(":common"))
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "org.jetbrains.research.marking.VariableLabelsMarking.kt"
        }
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }

    runIde {
        args = listOfNotNull("preprocessing")
        jvmArgs = listOf("-Djava.awt.headless=true")
    }

    register("preprocessing") {
        dependsOn(runIde)
    }
}