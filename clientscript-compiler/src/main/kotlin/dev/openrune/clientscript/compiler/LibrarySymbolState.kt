package dev.openrune.clientscript.compiler

import com.github.michaelbull.logging.InlineLogger
import me.filby.neptune.clientscript.compiler.SymbolMapper
import me.filby.neptune.runescript.compiler.codegen.Opcode
import me.filby.neptune.runescript.compiler.codegen.script.RuneScript
import me.filby.neptune.runescript.compiler.symbol.BasicSymbol
import me.filby.neptune.runescript.compiler.symbol.LocalVariableSymbol
import me.filby.neptune.runescript.compiler.symbol.ScriptSymbol
import me.filby.neptune.runescript.compiler.symbol.Symbol
import me.filby.neptune.runescript.compiler.trigger.CommandTrigger
import me.filby.neptune.runescript.compiler.type.MetaType
import me.filby.neptune.runescript.compiler.writer.LibraryWritePolicy
import java.nio.file.Path
import java.security.MessageDigest
import java.util.TreeMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

/**
 * Remembers, per library script, a fingerprint of the id every external symbol it references
 * resolved to the last time the project was compiled, and asks for the script to be written
 * again when that fingerprint no longer matches.
 *
 * Library scripts are the vanilla ones: they only exist so custom scripts can call into them,
 * and the cache already holds their compiled form. That form embeds ids though - components,
 * enums, sprites, varbits, other scripts - so when a gameval is renumbered the packed script
 * silently keeps the old number. Recording the ids here turns that into a diff on the next run,
 * without anyone having to copy the script into a custom directory to force it through.
 *
 * The state is one `name<TAB>fingerprint` line per script in [stateFile]. A script with no
 * record is not written; it is only recorded, so the first compile after enabling this (or after
 * the file is deleted) establishes the baseline. A changed library source also changes the
 * fingerprint and gets the script written, which is harmless: the output is what the cache
 * would hold anyway.
 */
class LibrarySymbolState(private val stateFile: Path, private val mapper: SymbolMapper) : LibraryWritePolicy {
    private val logger = InlineLogger()

    private val previous: Map<String, String> = load()
    private val current = TreeMap<String, String>()

    /** Full names of the library scripts this run sent to the writer. */
    val rewritten: MutableSet<String> = linkedSetOf()

    override fun shouldWrite(script: RuneScript): Boolean {
        val fingerprint = fingerprint(referencedIds(script))
        current[script.fullName] = fingerprint

        val recorded = previous[script.fullName] ?: return false
        if (recorded == fingerprint) {
            return false
        }
        rewritten += script.fullName
        return true
    }

    override fun finish() {
        stateFile.parent?.createDirectories()
        stateFile.writeText(current.entries.joinToString("\n", postfix = "\n") { "${it.key}\t${it.value}" })
        logger.info { "Recorded symbol ids for ${current.size} library scripts, ${rewritten.size} rewritten" }
    }

    private fun load(): Map<String, String> {
        if (!stateFile.exists()) {
            return emptyMap()
        }
        return stateFile.readLines()
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else line.substring(0, tab) to line.substring(tab + 1)
            }
            .toMap()
    }

    private fun fingerprint(ids: Map<String, Int>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((symbol, id) in ids) {
            digest.update(symbol.toByteArray())
            digest.update('='.code.toByte())
            digest.update(id.toString().toByteArray())
            digest.update('\n'.code.toByte())
        }
        return digest.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Every symbol the writer would turn into an id, keyed by type and name so a component and
     * an enum sharing a name stay apart.
     */
    private fun referencedIds(script: RuneScript): Map<String, Int> {
        val ids = TreeMap<String, Int>()
        fun note(symbol: Symbol) {
            val key = keyOf(symbol) ?: return
            val id = runCatching { mapper.get(symbol) }.getOrNull() ?: return
            ids[key] = id
        }
        for (block in script.blocks) {
            for ((opcode, operand) in block.instructions) {
                when (opcode) {
                    Opcode.PushConstantSymbol, Opcode.PushVar, Opcode.PopVar, Opcode.Gosub, Opcode.Jump ->
                        note(operand as Symbol)
                    else -> {}
                }
            }
        }
        for (table in script.switchTables) {
            for (case in table.cases) {
                case.keys.filterIsInstance<Symbol>().forEach(::note)
            }
        }
        return ids
    }

    private fun keyOf(symbol: Symbol): String? = when (symbol) {
        is LocalVariableSymbol -> null
        is BasicSymbol -> if (symbol.type is MetaType.Type) null else "${symbol.type.representation}:${symbol.name}"
        is ScriptSymbol -> if (symbol.trigger == CommandTrigger) null else "[${symbol.trigger.identifier},${symbol.name}]"
        else -> null
    }
}
