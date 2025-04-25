package dev.openrune.clientscript.compiler

enum class ClientScriptLogLevel {
    OFF,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    TRACE,
    ALL;

    override fun toString(): String = name.lowercase()

}
