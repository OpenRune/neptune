package me.filby.neptune.runescript.compiler.writer

import me.filby.neptune.runescript.compiler.codegen.script.RuneScript

/**
 * Decides whether a script that lives under a library path is still handed to the
 * [ScriptWriter]. Library scripts are normally compiled for their symbols only and never
 * written; a policy can opt individual ones back in, for example when something they
 * reference has been renumbered since they were last written.
 */
public interface LibraryWritePolicy {
    /**
     * Called for every compiled library script, whether or not it ends up written.
     */
    public fun shouldWrite(script: RuneScript): Boolean

    /**
     * Called once after all scripts have been offered to the writer.
     */
    public fun finish() {}
}
