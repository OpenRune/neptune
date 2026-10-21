package dev.openrune.clientscript.compiler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A three-file project: a custom script that calls two library scripts, one of which returns a
 * component. Packing it three times - as is, again unchanged, then with that component renumbered
 * - shows the library script following the renumber and nothing else being dragged along.
 */
class LibrarySymbolStateTest {
    @TempDir
    lateinit var project: Path

    @Test
    fun `library script is repacked only when a symbol it references moves`() {
        writeProject(sideComponent = "161:77")

        assertEquals(listOf("[clientscript,custom_entry]"), pack(), "first run only records the baseline")
        assertTrue(project.resolve(".library-symbols").exists())
        assertTrue("[proc,lib_uses_side1]\t" in project.resolve(".library-symbols").readText())

        assertEquals(listOf("[clientscript,custom_entry]"), pack(), "nothing moved, nothing extra written")

        symbols().resolve("component.sym").writeText("161:78\ttoplevel:side1\n")
        assertEquals(
            listOf("[clientscript,custom_entry]", "[proc,lib_uses_side1]"),
            pack(),
            "the script that bakes in side1 is written; the one that does not is left alone",
        )

        assertEquals(listOf("[clientscript,custom_entry]"), pack(), "the new id is now the baseline")
    }

    private fun pack(): List<String> =
        ClientScripts.compileTask(project.resolve("neptune.toml"), clientVersion = 240)
            .map { it.archiveName }
            .sorted()

    private fun symbols(): Path = project.resolve("symbols")

    private fun writeProject(sideComponent: String) {
        project.resolve("neptune.toml").writeText(
            """
            name = "test"
            client_version = 240
            sources = ["script/", "custom/"]
            symbols = ["symbols/"]
            libraries = ["script/"]
            """.trimIndent(),
        )

        symbols().createDirectories()
        symbols().resolve("component.sym").writeText("$sideComponent\ttoplevel:side1\n")
        symbols().resolve("clientscript.sym").writeText(
            "1\t[proc,lib_uses_side1]\n2\t[proc,lib_plain]\n3\t[clientscript,custom_entry]\n",
        )

        val library = project.resolve("script").createDirectories()
        library.resolve("[proc,lib_uses_side1].cs2").writeText(
            "[proc,lib_uses_side1]()(component)\nreturn(toplevel:side1);\n",
        )
        library.resolve("[proc,lib_plain].cs2").writeText(
            "[proc,lib_plain](int \$int0)(int)\nreturn(calc(\$int0 + 1));\n",
        )

        val custom = project.resolve("custom").createDirectories()
        custom.resolve("[clientscript,custom_entry].cs2").writeText(
            "[clientscript,custom_entry]()\ndef_component \$c = ~lib_uses_side1;\ndef_int \$n = ~lib_plain(1);\n",
        )
    }
}
