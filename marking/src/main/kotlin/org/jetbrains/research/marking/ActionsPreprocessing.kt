package org.jetbrains.research.marking

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


@Serializable
data class Project(val name: String, val language: String)

class ActionsPreprocessing : BasePlatformTestCase() {
    fun test() {
        val data = Project("kotlinx.serialization", "Kotlin")
        val string = Json.encodeToString(data)
        println(string)
        val obj = Json.decodeFromString<Project>(string)
        println(obj)
    }
}