group = rootProject.group
version = rootProject.version

intellij {
    version = "2020.2"
    type = "PY"
    setPlugins("Pythonid")
}

dependencies {
    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation(project(":common"))
}

tasks {
    runIde {
        val src: String? by project
        val dest: String? by project
        val addDescription: String? by project
        args = listOfNotNull(
            "preprocessing",
            src?.let { "--src=$it" },
            dest?.let { "--dest=$it" },
            addDescription?.let { "--addDescription" }
        )
        jvmArgs = listOf("-Djava.awt.headless=true")
        standardInput = System.`in`
        standardOutput = System.`out`
    }

    register("cli") {
        dependsOn("runIde")
    }
}