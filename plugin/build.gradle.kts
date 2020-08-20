group = rootProject.group
version = rootProject.version

intellij {
    version = "2019.3"
    type = "PY"
    setPlugins("Pythonid")
//    sandboxDirectory = File(rootProject.projectDir, "plugin/build/idea-sandbox").canonicalPath
}

tasks.withType<Test> {
    useJUnit()
}