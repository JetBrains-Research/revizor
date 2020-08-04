package org.jetbrains.research.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.OptionTag
import java.nio.file.Path
import java.nio.file.Paths

@State(name = "BugFinderConfig", storages = [Storage("bugfinder.config.xml")])
class BugFinderConfigService : PersistentStateComponent<BugFinderConfigService.ConfigState> {
    data class ConfigState(
        @OptionTag(converter = PathConverter::class)
        var patternsOutputPath: Path = Paths.get("/home/oleg/prog/jetbrains/data/"),
        @OptionTag(converter = PathConverter::class)
        var codeChangeMinerPath: Path = Paths.get("/home/oleg/prog/jetbrains/bug-finder/code_change_miner"),
        @OptionTag(converter = PathConverter::class)
        var pythonExecutablePath: Path = Paths.get("/home/oleg/miniconda3/envs/bug-finder/bin/python"),
        @OptionTag(converter = PathConverter::class)
        var tempDirectory: Path = Paths.get("/home/oleg/.temp/")
    )

    private var configState = ConfigState()

    override fun getState(): ConfigState {
        return configState
    }

    override fun loadState(state: ConfigState) {
        configState = state
    }

    class PathConverter : Converter<Path>() {
        override fun toString(value: Path): String? {
            return value.toString()
        }

        override fun fromString(value: String): Path? {
            return Paths.get(value)
        }
    }
}