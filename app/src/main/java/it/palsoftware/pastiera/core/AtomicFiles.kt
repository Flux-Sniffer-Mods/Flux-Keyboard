package it.palsoftware.pastiera.core

import java.io.File
import java.io.IOException

/**
 * Writes the file whole, then swaps it in: a save cut short (a crash, the battery) leaves the
 * previous version rather than a half-written file that reads as empty.
 */
fun File.writeBytesAtomically(bytes: ByteArray) {
    val temp = File(parentFile, "$name.tmp")
    temp.writeBytes(bytes)
    if (!temp.renameTo(this)) {
        temp.delete()
        throw IOException("Couldn't save $name")
    }
}

fun File.writeTextAtomically(text: String) = writeBytesAtomically(text.toByteArray(Charsets.UTF_8))
