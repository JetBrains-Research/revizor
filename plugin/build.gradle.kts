group = rootProject.group
version = rootProject.version

intellij {
    version = "2020.2"
    type = "PY"
    setPlugins("Pythonid")
}

dependencies {
    implementation(project(":common"))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}