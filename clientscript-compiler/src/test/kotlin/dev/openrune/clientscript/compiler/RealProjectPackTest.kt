package dev.openrune.clientscript.compiler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.assertTrue

/**
 * Packs a real Neptune project on its own, without the cache build around it, so the library
 * tracking can be watched against the full script set. Point it at the directory holding
 * `neptune.toml`:
 *
 * ```
 * gradlew :clientscript-compiler:test --tests "*RealProjectPackTest*" -Dneptune.project=C:\Users\me\AppData\Local\Fluxious\cs2
 * ```
 *
 * Run it once to lay down `.library-symbols.json`, change a symbol id (or let the cache build
 * re-dump the symbols), run it again and the log lists every library script written and why.
 */
class RealProjectPackTest {
    @Test
    @EnabledIfSystemProperty(named = "neptune.project", matches = ".+")
    fun `pack the project and report the library scripts written`() {
        val config = Path.of(System.getProperty("neptune.project")).resolve("neptune.toml")
        assertTrue(config.exists(), "no neptune.toml at $config")

        val scripts = ClientScripts.compileTask(config, clientVersion = clientVersion(config))
        println("packed ${scripts.size} scripts")
        scripts.map { it.archiveName }.sorted().forEach(::println)
    }

    private fun clientVersion(config: Path): Int =
        Regex("""(?m)^\s*client_version\s*=\s*(\d+)""").find(config.toFile().readText())?.groupValues?.get(1)?.toInt()
            ?: 240
}
