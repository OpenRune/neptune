package dev.openrune.clientscript.compiler

import ch.qos.logback.classic.LoggerContext
import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.logging.Logger
import com.google.gson.GsonBuilder
import me.filby.neptune.clientscript.compiler.SymbolMapper
import me.filby.neptune.clientscript.compiler.command.DbFindCommandHandler
import me.filby.neptune.clientscript.compiler.command.DbGetFieldCommandHandler
import me.filby.neptune.clientscript.compiler.command.EnumCommandHandler
import me.filby.neptune.clientscript.compiler.command.ParamCommandHandler
import me.filby.neptune.clientscript.compiler.command.PlaceholderCommand
import me.filby.neptune.clientscript.compiler.command.debug.DumpCommandHandler
import me.filby.neptune.clientscript.compiler.command.debug.ScriptCommandHandler
import me.filby.neptune.clientscript.compiler.configuration.BinaryFileWriterConfig
import me.filby.neptune.clientscript.compiler.configuration.ClientScriptCompilerConfig
import me.filby.neptune.clientscript.compiler.configuration.ClientScriptWriterConfig
import me.filby.neptune.clientscript.compiler.trigger.ClientTriggerType
import me.filby.neptune.clientscript.compiler.type.DbColumnType
import me.filby.neptune.clientscript.compiler.type.ParamType
import me.filby.neptune.clientscript.compiler.type.ScriptVarType
import me.filby.neptune.clientscript.compiler.writer.BinaryFileScriptWriter
import me.filby.neptune.runescript.compiler.ScriptCompiler
import me.filby.neptune.runescript.compiler.configuration.SymbolLoader
import me.filby.neptune.runescript.compiler.symbol.SymbolTable
import me.filby.neptune.runescript.compiler.type.MetaType
import me.filby.neptune.runescript.compiler.type.PrimitiveType
import me.filby.neptune.runescript.compiler.type.TupleType
import me.filby.neptune.runescript.compiler.type.Type
import me.filby.neptune.runescript.compiler.type.wrapped.VarBitType
import me.filby.neptune.runescript.compiler.type.wrapped.VarClanSettingsType
import me.filby.neptune.runescript.compiler.type.wrapped.VarClanType
import me.filby.neptune.runescript.compiler.type.wrapped.VarClientType
import me.filby.neptune.runescript.compiler.type.wrapped.VarPlayerType
import me.filby.neptune.runescript.compiler.writer.ScriptWriter
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines
import kotlin.io.path.useLines
import kotlin.system.exitProcess

private const val VERSION = "1.0.1-SNAPSHOT"
private val logger = InlineLogger()

fun parseArgs(args: Array<String>): Map<String, String> {
    val parsedArgs = mutableMapOf<String, String>()
    var currentOption = ""
    for (i in args.indices) {
        if (args[i].startsWith("--")) {
            currentOption = args[i].removePrefix("--")
        } else {
            parsedArgs[currentOption] = args[i]
        }
    }
    return parsedArgs
}


fun main(args: Array<String>) {
    val parsedArgs = parseArgs(args)

    val configPath = parsedArgs["config-path"]?.let { Path(it) } ?: Path("neptune.json")
    val print = parsedArgs.containsKey("print")
    val logLevelName = parsedArgs["log-level"] ?: "info"
    val version = parsedArgs.containsKey("version")

    configureLogLevel(logLevelName)

    if (version) {
        println("Neptune ClientScript 2 Compiler")
        println("Version $VERSION")
        exitProcess(0)
    }

    val config = createConfig()
    if (print) {
        val json = GsonBuilder()
            .create()
            .toJson(config)
        println(json)
        exitProcess(0)
    }

    val basePath = configPath.absolute().parent
    val sourcePaths = config.sourcePaths.map { basePath.resolve(it) }
    val symbolPaths = config.symbolPaths.map { basePath.resolve(it) }
    val libraryPaths = config.libraryPaths.map { basePath.resolve(it) }
    val (binaryWriterConfig) = config.writers

    val mapper = SymbolMapper()
    val writer = if (binaryWriterConfig != null) {
        val outputPath = basePath.resolve(binaryWriterConfig.outputPath)
        BinaryFileScriptWriter(outputPath, mapper)
    } else {
        null
    }

    // load commands and clientscript id mappings
    loadSpecialSymbols(symbolPaths, mapper)

    // setup compiler and execute it
    val compiler = ClientScriptCompiler(sourcePaths, libraryPaths, writer, symbolPaths, mapper)
    compiler.setup()
    compiler.run()
}

private fun configureLogLevel(levelName: String) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
    val level = when (levelName) {
        "off" -> ch.qos.logback.classic.Level.OFF
        "error" -> ch.qos.logback.classic.Level.ERROR
        "warn" -> ch.qos.logback.classic.Level.WARN
        "info" -> ch.qos.logback.classic.Level.INFO
        "debug" -> ch.qos.logback.classic.Level.DEBUG
        "trace" -> ch.qos.logback.classic.Level.TRACE
        "all" -> ch.qos.logback.classic.Level.ALL
        else -> error("Unknown log level: $levelName")
    }
    root.level = level
}

private fun createConfig(): ClientScriptCompilerConfig {
    return ClientScriptCompilerConfig(
        sourcePaths = listOf("src/"),
        symbolPaths = listOf("symbols/"),
        libraryPaths = listOf("src/osrs/"),
        excludePaths = listOf("pack/"),
        writers = ClientScriptWriterConfig(BinaryFileWriterConfig(outputPath = "pack/cs2/"))
    )
}

private fun loadSpecialSymbols(symbolsPaths: List<Path>, mapper: SymbolMapper) {
    for (symbolPath in symbolsPaths) {
        val commandMappings = symbolPath.resolve("commands.sym")
        if (commandMappings.exists()) {
            for (line in commandMappings.readLines()) {
                if (line.isBlank()) {
                    continue
                }

                val split = line.split("\t")
                val id = split[0].toInt()
                val name = split[1]
                mapper.putCommand(id, name)
            }
        }

        // TODO move somewhere else?
        val scriptMappings = symbolPath.resolve("clientscript.sym")
        if (scriptMappings.exists()) {
            for (line in scriptMappings.readLines()) {
                if (line.isBlank()) {
                    continue
                }

                val split = line.split("\t")
                val id = split[0].toInt()
                val name = split[1]
                mapper.putScript(id, name)
            }
        }
    }
}

class ClientScriptCompiler(
    sourcePaths: List<Path>,
    libraryPaths: List<Path>,
    scriptWriter: ScriptWriter?,
    private val symbolPaths: List<Path>,
    private val mapper: SymbolMapper,
) : ScriptCompiler(sourcePaths, libraryPaths, scriptWriter) {
    fun setup() {
        triggers.registerAll<ClientTriggerType>()

        // register types
        types.registerAll<ScriptVarType>()
        types.changeOptions("long") {
            allowDeclaration = false
        }

        // special types for commands
        types.register("hook", MetaType.Hook(MetaType.Unit))
        types.register("stathook", MetaType.Hook(ScriptVarType.STAT))
        types.register("invhook", MetaType.Hook(ScriptVarType.INV))
        types.register("varphook", MetaType.Hook(VarPlayerType(MetaType.Any)))
        types.register("dbcolumn", DbColumnType(MetaType.Any))
        types.register("clientopnpc", MetaType.Script(ClientTriggerType.CLIENTOPNPC, MetaType.Unit, MetaType.Unit))
        types.register("clientoploc", MetaType.Script(ClientTriggerType.CLIENTOPLOC, MetaType.Unit, MetaType.Unit))
        types.register("clientopobj", MetaType.Script(ClientTriggerType.CLIENTOPOBJ, MetaType.Unit, MetaType.Unit))
        types.register(
            "clientopplayer",
            MetaType.Script(ClientTriggerType.CLIENTOPPLAYER, MetaType.Unit, MetaType.Unit),
        )
        types.register("clientoptile", MetaType.Script(ClientTriggerType.CLIENTOPTILE, MetaType.Unit, MetaType.Unit))

        // allow assignment of namedobj to obj
        types.addTypeChecker { left, right -> left == ScriptVarType.OBJ && right == ScriptVarType.NAMEDOBJ }

        // treat varp as alias of varp<int>
        types.addTypeChecker { left, right ->
            (left is VarPlayerType && left.inner == PrimitiveType.INT && right == ScriptVarType.VARP) ||
                (left == ScriptVarType.VARP && right is VarPlayerType && right.inner == PrimitiveType.INT)
        }

        // register the dynamic command handlers
        addDynamicCommandHandler("enum", EnumCommandHandler())
        addDynamicCommandHandler("oc_param", ParamCommandHandler(ScriptVarType.OBJ))
        addDynamicCommandHandler("nc_param", ParamCommandHandler(ScriptVarType.NPC))
        addDynamicCommandHandler("lc_param", ParamCommandHandler(ScriptVarType.LOC))
        addDynamicCommandHandler("struct_param", ParamCommandHandler(ScriptVarType.STRUCT))
        addDynamicCommandHandler("db_find", DbFindCommandHandler(false))
        addDynamicCommandHandler("db_find_with_count", DbFindCommandHandler(true))
        addDynamicCommandHandler("db_find_refine", DbFindCommandHandler(false))
        addDynamicCommandHandler("db_find_refine_with_count", DbFindCommandHandler(true))
        addDynamicCommandHandler("db_getfield", DbGetFieldCommandHandler())

        addDynamicCommandHandler("event_opbase", PlaceholderCommand(PrimitiveType.STRING, "event_opbase"))
        addDynamicCommandHandler("event_mousex", PlaceholderCommand(PrimitiveType.INT, Int.MIN_VALUE + 1))
        addDynamicCommandHandler("event_mousey", PlaceholderCommand(PrimitiveType.INT, Int.MIN_VALUE + 2))
        addDynamicCommandHandler("event_com", PlaceholderCommand(ScriptVarType.COMPONENT, Int.MIN_VALUE + 3))
        addDynamicCommandHandler("event_op", PlaceholderCommand(PrimitiveType.INT, Int.MIN_VALUE + 4))
        addDynamicCommandHandler("event_comsubid", PlaceholderCommand(PrimitiveType.INT, Int.MIN_VALUE + 5))
        addDynamicCommandHandler("event_com2", PlaceholderCommand(ScriptVarType.COMPONENT, Int.MIN_VALUE + 6))
        addDynamicCommandHandler("event_comsubid2", PlaceholderCommand(PrimitiveType.INT, Int.MIN_VALUE + 7))
        addDynamicCommandHandler("event_keycode", PlaceholderCommand(PrimitiveType.INT, Int.MIN_VALUE + 8))
        addDynamicCommandHandler("event_keychar", PlaceholderCommand(PrimitiveType.CHAR, Int.MIN_VALUE + 9))

        addDynamicCommandHandler("dump", DumpCommandHandler())
        addDynamicCommandHandler("script", ScriptCommandHandler())

        // symbol loaders
        addSymConstantLoaders()

        addSymLoader("bugtemplate", ScriptVarType.BUG_TEMPLATE)
        addSymLoader("graphic", ScriptVarType.GRAPHIC)
        addSymLoader("fontmetrics", ScriptVarType.FONTMETRICS)
        addSymLoader("stat", ScriptVarType.STAT)
        addSymLoader("synth", ScriptVarType.SYNTH)
        addSymLoader("locshape", ScriptVarType.LOC_SHAPE)
        addSymLoader("model", ScriptVarType.MODEL)
        addSymLoader("interface", ScriptVarType.INTERFACE)
        addSymLoader("toplevelinterface", ScriptVarType.TOPLEVELINTERFACE)
        addSymLoader("overlayinterface", ScriptVarType.OVERLAYINTERFACE)
        addSymLoader("component", ScriptVarType.COMPONENT)
        addSymLoader("category", ScriptVarType.CATEGORY)
        addSymLoader("wma", ScriptVarType.MAPAREA)
        addSymLoader("mapelement", ScriptVarType.MAPELEMENT)
        addSymLoader("loc", ScriptVarType.LOC)
        addSymLoader("npc", ScriptVarType.NPC)
        addSymLoader("obj", ScriptVarType.NAMEDOBJ)
        addSymLoader("inv", ScriptVarType.INV)
        addSymLoader("enum", ScriptVarType.ENUM)
        addSymLoader("struct", ScriptVarType.STRUCT)
        addSymLoader("seq", ScriptVarType.SEQ)
        addSymLoader("dbtable", ScriptVarType.DBTABLE)
        addSymLoader("dbrow", ScriptVarType.DBROW)
        addSymLoader("dbcolumn") { DbColumnType(it) }
        addSymLoader("param") { ParamType(it) }
        addSymLoader("varp") { VarPlayerType(it) }
        addSymLoader("varc") { VarClientType(it) }
        addSymLoader("varbit", VarBitType)
        addSymLoader("varclan") { VarClanType(it) }
        addSymLoader("varclansetting") { VarClanSettingsType(it) }
        addSymLoader("stringvector", ScriptVarType.STRINGVECTOR)
    }

    /**
     * Looks for `constant.sym` and all `sym` files in `/constant` and registers them
     * with a [ConstantLoader].
     */
    private fun addSymConstantLoaders() {
        for (symbolPath in symbolPaths) {
            // look for {symbol_path}/constant.sym
            val constantsFile = symbolPath.resolve("constant.sym")
            if (constantsFile.exists()) {
                addSymbolLoader(ConstantLoader(constantsFile))
            }

            // look for {symbol_path}/constant/**.sym
            val constantDir = symbolPath.resolve("constant")
            if (constantDir.exists() && constantDir.isDirectory()) {
                val files = constantDir
                    .toFile()
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "sym" }
                for (file in files) {
                    addSymbolLoader(ConstantLoader(file.toPath()))
                }
            }
        }
    }

    /**
     * Helper for loading external symbols from `sym` files with a specific [type].
     */
    private fun addSymLoader(name: String, type: Type) {
        addSymLoader(name) { type }
    }

    /**
     * Helper for loading external symbols from `sym` files with subtypes.
     */
    private fun addSymLoader(name: String, typeSuppler: (subTypes: Type) -> Type) {
        for (symbolPath in symbolPaths) {
            // look for {symbol_path}/{name}.sym
            val typeFile = symbolPath.resolve("$name.sym")
            if (typeFile.exists()) {
                addSymbolLoader(TsvSymbolLoader(mapper, typeFile, typeSuppler))
            }

            // look for {symbol_path}/{name}/**.sym
            val typeDir = symbolPath.resolve(name)
            if (typeDir.exists() && typeDir.isDirectory()) {
                val files = typeDir
                    .toFile()
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "sym" }
                for (file in files) {
                    addSymbolLoader(TsvSymbolLoader(mapper, file.toPath(), typeSuppler))
                }
            }
        }
    }
}

class ConstantLoader(private val path: Path) : SymbolLoader {
    override fun SymbolTable.load(compiler: ScriptCompiler) {
        path.useLines {
            for (line in it) {
                val split = line.split('\t', limit = 2)
                if (split.size != 2) {
                    continue
                }

                addConstant(split[0], split[1])
            }
        }
    }
}

class TsvSymbolLoader(
    private val mapper: SymbolMapper,
    private val path: Path,
    private val typeSupplier: (subTypes: Type) -> Type,
) : SymbolLoader {
    constructor(mapper: SymbolMapper, path: Path, type: Type) : this(mapper, path, { type })

    override fun SymbolTable.load(compiler: ScriptCompiler) {
        path.useLines { lines ->
            for (line in lines) {
                val split = line.split('\t')
                if (split.size < 2) {
                    continue
                }

                val id = split[0].toInt()
                val name = split[1]
                val subTypes = if (split.size >= 3) {
                    val typeSplit = split[2].split(',')
                    val types = typeSplit.map { typeName -> compiler.types.find(typeName) }
                    TupleType.fromList(types)
                } else {
                    MetaType.Unit
                }
                val type = typeSupplier(subTypes)

                val symbol = addBasic(type, name)
                mapper.putSymbol(id, symbol)
            }
        }
    }
}
