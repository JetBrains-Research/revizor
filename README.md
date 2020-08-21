# Bug Finder

A static code analysis tool that tries to find 
complicated, semantic, and (as far as possible) 
buggy patterns in your Python code. We use 
patterns represented in control flow graphs, 
which were gathered by 
[code-change-miner](https://github.com/JetBrains-Research/code-change-miner) 
tool. For real, this plugin runs an inspection which 
aims to build mappings between isomorphic subgraphs 
of patterns and control flow graph of your code, 
which is built automatically on the fly.  

## Installation

**Build plugin from sources**

 - Run `./gradlew buildPlugin`
 - Check out `build/distributions/bug-finder-*.zip`
 
**Quick IDE launch for evaluation**
 
 - Run `./gradlew runIde`
 - Open any Python file containing functions/class methods
 - Wait until the code analysis is complete
 - Check out `WARNING` messages
 
**Run tests**

 - Install [code-change-miner](https://github.com/JetBrains-Research/code-change-miner)
 by following the instructions in README.md
 - In the `src/main/resources/config.json` specify:
   - `code_change_miner_path` (correct path to the installation folder)
   - `python_executable_path` (your python executable)
   - `temp_directory_path` (directory for temporary files)
 - Run `./gradlew test`
 
## Custom patterns support

 - Let's say you've already mined patterns using [code-change-miner](https://github.com/JetBrains-Research/code-change-miner)
 - Select the ones you need and put them in a separate folder.
 Make sure each pattern is represented by a directory
 with a unique name (id) and contains `.dot` files
 - Run `./gradlew :marking:jar`
 - Now you can mark the patterns using CLI interface: `java -jar marking/build/libs/marking-*.jar` 
   - `--input`, `-i`: Input directory with patterns mined by code-change-miner tool (always required) { path }
   - `--output`, `-o`: Output directory for processed patterns (always required) { path }
   - `add-description`, `-d`: Add description manually for each pattern { optional }
   - `--help`, `-h`: Usage info
 - Copy processed patterns to `src/main/resources/patterns` and re-build plugin