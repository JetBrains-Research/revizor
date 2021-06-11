[![JetBrains Research](https://jb.gg/badges/research.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
# Revizor

A plugin for PyCharm that takes the recurrent change patterns and highlights potential places for their application in
the developer’s code editor. We use graph-based code change representations gathered from 120 GitHub
repositories by [Python Change Miner](https://github.com/JetBrains-Research/python-change-miner).

Behind the scenes, the plugin runs an inspection which builds isomorphic mappings between fine-grained Program
Dependence Graphs of the mined patterns and a similar graph of your code and then suggests a relevant quick-fix to
apply for the highlighted code fragment.

<img src="https://i.ibb.co/sgvB8gf/Peek-2021-06-09-12-57.gif" alt="Plugin Demo">

## Installation

At first, clone this repository and open the root folder.

**Build the plugin from sources and go:**

 - Run `./gradlew :plugin:buildPlugin`
 - Check out `./plugin/build/distributions/plugin-*.zip`
 - Install the plugin in your PyCharm via `File - Settings - Plugins - Install Plugin from Disk...`

**Quick IDE launch for evaluation:**
 
 - Run `./gradlew :plugin:runIde`
 - Open any Python file with functions, possibly containing mined code patterns 
   - You can find a test project for evaluation [here](https://github.com/SmirnovOleg/revizor-test)
 - Wait until the code analysis is complete
 - Check out `WARNING` messages

## Custom patterns support

- Let's say you've already mined patterns using
  [Python Change Miner](https://github.com/JetBrains-Research/python-change-miner).
- Select the ones you need to automate and put them in a separate folder. Make sure each pattern is represented by a
  directory and contains all the necessary files as they were provided by the miner (`fragment-*.dot`, `sample-*.html`,
  ...).
- Run the preprocessing script via:
  ```./gradlew :preprocessing:cli -Psrc=path/to/patterns/dir/ -Pdst=path/to/destination/dir/ -PaddDescription```
    - Gradle task arguments:
        - `-Psrc`: Path to input directory with the patterns mined by the miner (always required)
        - `-Pdst`: Path to output directory for processed patterns (always required)
        - `-PaddDescription`: If you want to add description for each pattern manually (optional)
- Follow the instructions in the command line (actually, you only need to provide tooltip annotations for the
  patterns, because all the other preparation tasks will be done automatically).
- Finally, each preprocessed pattern's folder should contain 4 files:
    - `graph.dot`: an actual graph of the pattern
    - `actions.json`: edit actions stored by [GumTree](https://github.com/GumTreeDiff/gumtree) when it processes corresponding commits of a change pattern
    - `labels_groups.json`: matched nodes and groups of labels for all the variables in the graph, to be used for
      localization in the IDE
    - `description.txt`: textual description you provided when the pattern was processed; they can explain the reason why
      a developer may be interested in this type of code change
- When you are finished, just copy preprocessed patterns from the destination directory to
  `./plugin/src/main/resources/patterns` and re-build the plugin.

## Contacts

If you have any questions or suggestions, don't hesitate to open an issue or contact the developers at oleg.smirnov@jetbrains.com
