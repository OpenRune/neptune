package dev.openrune.clientscript.compiler

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.tomlMapper
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.github.michaelbull.logging.InlineLogger
import com.github.michaelbull.logging.Logger
import me.filby.neptune.clientscript.compiler.ClientScriptCompiler
import me.filby.neptune.clientscript.compiler.SymbolMapper
import me.filby.neptune.clientscript.compiler.configuration.BinaryFileWriterConfig
import me.filby.neptune.clientscript.compiler.configuration.ClientScriptCompilerConfig
import me.filby.neptune.clientscript.compiler.configuration.ClientScriptCompilerFeatureSet
import me.filby.neptune.clientscript.compiler.writer.BinaryScriptWriter
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readLines
import kotlin.system.exitProcess

private const val VERSION = "1.0.1-SNAPSHOT"
private val logger = InlineLogger()

object ClientScripts {

    fun compileTask(
        configPath: Path,
        clientVersion: Int,
        clientScriptLogLevel: ClientScriptLogLevel = ClientScriptLogLevel.INFO,
    ): MutableList<ScriptEntry> {

        configureLogLevel(clientScriptLogLevel.toString())

        val config = loadConfig(configPath, clientVersion)

        val basePath = configPath.absolute().parent
        val sourcePaths = config.sourcePaths.map { basePath.resolve(it) }
        val symbolPaths = config.symbolPaths.map { basePath.resolve(it) }
        val libraryPaths = config.libraryPaths.map { basePath.resolve(it) }
        val features = config.features

        val mapper = SymbolMapper()
        val writer = OpenRuneScriptWriter(mapper, sourcePaths, BinaryScriptWriter.DebugMode.FULL, features)

        // load commands and clientscript id mappings
        loadSpecialSymbols(symbolPaths, mapper)

        // setup compiler and execute it
        val compiler = ClientScriptCompiler(sourcePaths, libraryPaths, writer, features, symbolPaths, mapper)
        compiler.setup()
        compiler.run()

        return writer.scripts
    }
}


private fun configureLogLevel(levelName: String) {
    val context = LoggerFactory.getILoggerFactory() as LoggerContext
    val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
    val level = when (levelName) {
        "off" -> Level.OFF
        "error" -> Level.ERROR
        "warn" -> Level.WARN
        "info" -> Level.INFO
        "debug" -> Level.DEBUG
        "trace" -> Level.TRACE
        "all" -> Level.ALL
        else -> error("Unknown log level: $levelName")
    }
    root.level = level
}

private fun loadConfig(configPath: Path,clientVersion : Int): ClientScriptCompilerConfig {
    if (configPath.notExists()) {
        logger.error { "Unable to locate configuration file: $configPath." }
        exitProcess(1)
    }

    val document = TomlValue.from(configPath)
    val defaultFeatures = getDefaultFeaturesForVersion(clientVersion)
    val tomlMapper = tomlMapper {
        // these defaults are required for
        //  1) if features is not defined at all
        //  2) if some features are defined
        default(ClientScriptCompilerConfig(features = defaultFeatures))
        default(defaultFeatures)

        mapping<ClientScriptCompilerConfig>(
            "sources" to "sourcePaths",
            "symbols" to "symbolPaths",
            "libraries" to "libraryPaths",
            "excludes" to "excludePaths",
            "writer" to "writers",
        )
        mapping<ClientScriptCompilerFeatureSet>(
            "db_find_returns_count" to "dbFindReturnsCount",
            "cc_create_optional_assert_new" to "ccCreateAssertNewArg",
            "prefix_postfix_expressions" to "prefixPostfixExpressions",
            "arrays_v2" to "arraysV2",
            "simplified_type_codes" to "simplifiedTypeCodes",
            "long_support" to "longSupport",
        )
        mapping<BinaryFileWriterConfig>("output" to "outputPath")
    }
    logger.info { "Loading configuration from $configPath." }
    return tomlMapper.decode<ClientScriptCompilerConfig>(document)
}

private fun getDefaultFeaturesForVersion(clientVersion: Int): ClientScriptCompilerFeatureSet {

    return ClientScriptCompilerFeatureSet(
        dbFindReturnsCount = clientVersion >= 228,
        ccCreateAssertNewArg = clientVersion >= 230,
        prefixPostfixExpressions = false,
        arraysV2 = clientVersion >= 231,
        simplifiedTypeCodes = clientVersion >= 231,
        longSupport = clientVersion >= 237,
        foldJoinedStringConstants = clientVersion >= 240,
        stringTemplates = clientVersion >= 240,
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
