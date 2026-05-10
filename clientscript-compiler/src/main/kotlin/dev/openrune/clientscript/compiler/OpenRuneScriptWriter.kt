package dev.openrune.clientscript.compiler

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import me.filby.neptune.clientscript.compiler.configuration.ClientScriptCompilerFeatureSet
import me.filby.neptune.clientscript.compiler.writer.BinaryScriptWriter
import me.filby.neptune.runescript.compiler.codegen.script.RuneScript
import java.io.ByteArrayOutputStream

data class ScriptEntry(
    val id: Int,
    val archiveName: String,
    val bytes: ByteArray,
)

/**
 * An implementation of [BinaryScriptWriter] that writes the scripts to [scripts] .
 */
class OpenRuneScriptWriter(
    idProvider: IdProvider,
    features: ClientScriptCompilerFeatureSet,
    allocator: ByteBufAllocator = ByteBufAllocator.DEFAULT,
) : BinaryScriptWriter(idProvider, features, allocator) {

    val scripts = emptyList<ScriptEntry>().toMutableList()

    override fun outputScript(script: RuneScript, data: ByteBuf) {
        val id = idProvider.get(script.symbol)
        val byteArrayOutputStream = ByteArrayOutputStream()
        data.readBytes(byteArrayOutputStream, data.readableBytes())
        scripts.add(ScriptEntry(id, script.fullName, byteArrayOutputStream.toByteArray()))
    }
}
