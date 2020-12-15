[![JetBrains Research](https://jb.gg/badges/research.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
#  Bug Finder

A static code analysis tool for locating and quick-fixing semantic patterns in your Python code. 
We use graphs-based patterns, which were gathered from 120 GitHub repositories by 
[code-change-miner](https://github.com/JetBrains-Research/code-change-miner) 
tool. 

Behind the scenes, the plugin runs an inspection which builds 
isomorphic mappings between fine-grained Program Dependence Graphs 
of mined patterns and a similar graph of your code, and then suggests 
you a relevant quick-fix to repair the highlighted code fragment.

<img src="https://i.ibb.co/ySN4dcy/presentation.gif" alt="Plugin demo">

## Installation

 - Clone this repository: `git clone https://github.com/JetBrains-Research/bug-finder.git`
 - Open the root directory: `cd bug-finder`
 
**Build plugin from sources**

 - Run `./gradlew :plugin:buildPlugin`
 - Check out `./plugin/build/distributions/plugin-*.zip`
 - Install the plugin in your PyCharm via `File - Settings - Plugins - Install Plugin from Disk...`
 
**Quick IDE launch for evaluation**
 
 - Run `./gradlew :plugin:runIde`
 - Open any Python file with functions, possibly containing mined code patterns 
 - Wait until the code analysis is complete
 - Check out `WARNING` messages

## Custom patterns support

 - Let's say you've already mined patterns using 
 [code-change-miner](https://github.com/JetBrains-Research/code-change-miner)
 - Select the ones you need and put them in a separate folder.
 Make sure each pattern is represented by a directory
 with a unique name and contains necessary `fragment-*.dot` files.
 - Add files `before.py` and `after.py` with minimal change pattern reproduction code snippet
 manually to each pattern's folder. The files should contain only one function with statements corresponding to pattern.
 The plugin need it to produce GumTree edit actions over the PSI. You can find examples in \
 `./plugin/src/main/resources/patterns`
 - Run `./gradlew :preprocessing:cli -Psrc=path/to/pattern/dir/ -Pdst=path/to/destination/dir/ -PaddDescription`
 - Follow the instructions in the command line to mark variable labels groups and provide descriptions.
 - Gradle task arguments: 
   - `-Psrc`: Path to input directory with patterns mined by code-change-miner (always required)
   - `-Pdst`: Path to output directory for processed patterns (always required)
   - `-PaddDescription`: Add description manually for each pattern (optional)
 - When you are finished, copy processed patterns from the
  destination directory to `./plugin/src/main/resources/patterns` and re-build the plugin
