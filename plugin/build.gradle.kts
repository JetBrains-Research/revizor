group = rootProject.group
version = rootProject.version

dependencies {
    implementation(project(":common"))
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = "5.6.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}