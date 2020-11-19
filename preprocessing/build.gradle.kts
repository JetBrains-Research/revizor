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
    runIde {
        standardInput = System.`in`
        standardOutput = System.`out`
        args = listOfNotNull("preprocessing")
        jvmArgs = listOf("-Djava.awt.headless=true")
    }

    register("cli") {
        dependsOn("runIde")
    }
}