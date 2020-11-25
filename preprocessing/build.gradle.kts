group = rootProject.group
version = rootProject.version

dependencies {
    implementation("com.xenomachina:kotlin-argparser:2.0.7")
    implementation(project(":common"))
}

tasks {
    runIde {
        val src: String? by project
        val dst: String? by project
        val addDescription: String? by project
        args = listOfNotNull(
            "preprocessing",
            src?.let { "--src=$it" },
            dst?.let { "--dest=$it" },
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