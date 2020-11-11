group = rootProject.group
version = rootProject.version

intellij {
    version = "2020.2"
    type = "PY"
    setPlugins("Pythonid")
}

dependencies {
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.6")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}