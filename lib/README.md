# Local host libraries

Place **remapped production JARs**, not development or sources JARs, in the folder matching this source branch:

```
lib/1.20.1/  # CML for 1.20.1 + IRL Core built for 1.20.1
lib/1.20.4/  # CML for 1.20.4 + IRL Core built for 1.20.4
lib/1.21.1/  # CML for 1.21.1 + IRL Core built for 1.21.1
```

Run `gradlew build` on the matching source branch. Java 17 bytecode is used for 1.20.x; Java 21 for 1.21.1.

The build checks mod IDs, the exact `Fabric-Minecraft-Version` manifest attribute and the `intermediary` mapping namespace. It rejects missing, duplicate or mismatched libraries. An old filename ending in `1.20.jar` does not establish compatibility with both 1.20.1 and 1.20.4.

For a shared library directory, pass `-PlibDir=<absolute path to lib>`. Individual remapped JARs can be supplied with `-PcmlJar=<path>` and `-PcoreJar=<path>`. Run `gradlew verifyLocalLibraries` to inspect the selection.

Core resolves through a local Ivy module (required for Loom nesting) and is bundled through Loom `include`; CML stays an external mod dependency. Binary JARs remain ignored by Git. No Maven Local publication is required.

`-Pmc` may confirm the current branch target, but cannot turn a 1.21.1 source branch into a 1.20.x source port. Switch to the matching branch first.

Use `-PrunDir=build/dev-run` for an isolated client directory.

The client uses Java 17 for 1.20.x and Java 21 for 1.21.1. Gradle itself can run on a newer JDK. By default, Gradle selects the matching installed Java toolchain. To use an existing JRE/JDK, set `runJavaHome=<path>` in the ignored `gradle/local-runtime.properties` file, or pass `-PrunJavaHome=<path>`. The runtime's major version is validated before launch.

Minecraft 1.20.1 sets Loom's runtime compatibility to Java 17. This preserves its LWJGL 3.3.1 runtime instead of the automatic LWJGL upgrade that newer Gradle JVMs otherwise trigger and Sodium rejects.
