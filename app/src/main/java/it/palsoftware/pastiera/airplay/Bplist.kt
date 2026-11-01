package it.palsoftware.pastiera.airplay

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Apple's binary property lists, which AirPlay's requests and answers carry: maps (string keys),
 * lists, strings, whole numbers, decimals, true/false and bytes. Read back the same way; dates as
 * seconds, anything else as null.
 */
internal object Bplist {
    fun encode(value: Any?): ByteArray {
        val objects = mutableListOf<ByteArray>()
        // Each object written once in order, its references filled in after: the reference size
        // depends on how many there are, so they're counted first
        val count = count(value)
        val refSize = if (count < 256) 1 else 2
        add(value, objects, refSize)
        val out = ByteArrayOutputStream()
        out.write("bplist00".toByteArray())
        val offsets = IntArray(objects.size)
        for ((i, obj) in objects.withIndex()) {
            offsets[i] = out.size()
            out.write(obj)
        }
        val tableOffset = out.size()
        val offsetSize = when {
            tableOffset < 0x100 -> 1
            tableOffset < 0x10000 -> 2
            else -> 4
        }
        for (offset in offsets) out.write(sized(offset.toLong(), offsetSize))
        val trailer = ByteBuffer.allocate(32)
        trailer.position(6)
        trailer.put(offsetSize.toByte()).put(refSize.toByte())
        trailer.putLong(objects.size.toLong()).putLong(0).putLong(tableOffset.toLong())
        out.write(trailer.array())
        return out.toByteArray()
    }

    private fun count(value: Any?): Int = when (value) {
        is Map<*, *> -> 1 + value.entries.sumOf { 1 + count(it.value) }
        is List<*> -> 1 + value.sumOf { count(it) }
        else -> 1
    }

    /** Adds [value] (and what's inside it) to [objects]; its index. */
    private fun add(value: Any?, objects: MutableList<ByteArray>, refSize: Int): Int {
        val index = objects.size
        objects += ByteArray(0)
        objects[index] = when (value) {
            null -> byteArrayOf(0x00)
            is Boolean -> byteArrayOf(if (value) 0x09 else 0x08)
            is Int, is Long, is Short, is Byte -> integer((value as Number).toLong())
            is Float, is Double -> ByteBuffer.allocate(9).put(0x23).putDouble((value as Number).toDouble()).array()
            is ByteArray -> header(0x4, value.size) + value
            is String -> if (value.all { it.code < 0x80 }) header(0x5, value.length) + value.toByteArray(Charsets.US_ASCII)
                else header(0x6, value.length) + value.toByteArray(Charsets.UTF_16BE)
            is List<*> -> {
                val refs = value.map { add(it, objects, refSize) }
                header(0xA, refs.size) + refs.fold(ByteArray(0)) { acc, r -> acc + sized(r.toLong(), refSize) }
            }
            is Map<*, *> -> {
                val keys = value.keys.map { add(it.toString(), objects, refSize) }
                val values = value.values.map { add(it, objects, refSize) }
                header(0xD, keys.size) + (keys + values).fold(ByteArray(0)) { acc, r -> acc + sized(r.toLong(), refSize) }
            }
            else -> header(0x5, value.toString().length) + value.toString().toByteArray(Charsets.UTF_8)
        }
        return index
    }

    private fun header(type: Int, count: Int): ByteArray =
        if (count < 15) byteArrayOf(((type shl 4) or count).toByte())
        else byteArrayOf(((type shl 4) or 0xF).toByte()) + integer(count.toLong())

    private fun integer(value: Long): ByteArray = when {
        value in 0..0xFF -> byteArrayOf(0x10) + sized(value, 1)
        value in 0..0xFFFF -> byteArrayOf(0x11) + sized(value, 2)
        value in 0..0xFFFFFFFFL -> byteArrayOf(0x12) + sized(value, 4)
        else -> byteArrayOf(0x13) + sized(value, 8)
    }

    private fun sized(value: Long, size: Int) = ByteArray(size) { i -> (value shr (8 * (size - 1 - i))).toByte() }

    fun decode(data: ByteArray): Any? {
        if (data.size < 40 || String(data, 0, 8, Charsets.US_ASCII) != "bplist00") return null
        val trailer = ByteBuffer.wrap(data, data.size - 32, 32)
        trailer.position(data.size - 26)
        val offsetSize = trailer.get().toInt()
        val refSize = trailer.get().toInt()
        val count = trailer.long.toInt()
        val top = trailer.long.toInt()
        val tableOffset = trailer.long.toInt()
        fun read(pos: Int, size: Int): Long {
            var v = 0L
            for (i in 0 until size) v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
            return v
        }
        val offsets = IntArray(count) { read(tableOffset + it * offsetSize, offsetSize).toInt() }
        fun obj(index: Int, depth: Int): Any? {
            if (depth > 32 || index !in offsets.indices) return null
            var pos = offsets[index]
            val marker = data[pos].toInt() and 0xFF
            val type = marker shr 4
            val info = marker and 0xF
            pos++
            fun length(): Int {
                if (info != 0xF) return info
                val size = 1 shl (data[pos].toInt() and 0xF)
                val n = read(pos + 1, size).toInt()
                pos += 1 + size
                return n
            }
            return when (type) {
                0x0 -> when (info) { 0x8 -> false; 0x9 -> true; else -> null }
                0x1 -> {
                    val size = 1 shl info
                    if (size == 8) ByteBuffer.wrap(data, pos, 8).long else read(pos, size)
                }
                0x2 -> if (info == 3) ByteBuffer.wrap(data, pos, 8).double else ByteBuffer.wrap(data, pos, 4).float.toDouble()
                0x3 -> ByteBuffer.wrap(data, pos, 8).double
                0x4 -> { val n = length(); data.copyOfRange(pos, pos + n) }
                0x5 -> { val n = length(); String(data, pos, n, Charsets.US_ASCII) }
                0x6 -> { val n = length(); String(data, pos, n * 2, Charsets.UTF_16BE) }
                0x8 -> read(pos, info + 1)
                0xA -> { val n = length(); List(n) { obj(read(pos + it * refSize, refSize).toInt(), depth + 1) } }
                0xD -> {
                    val n = length()
                    val map = LinkedHashMap<String, Any?>()
                    for (i in 0 until n) {
                        val key = obj(read(pos + i * refSize, refSize).toInt(), depth + 1)?.toString() ?: continue
                        map[key] = obj(read(pos + (n + i) * refSize, refSize).toInt(), depth + 1)
                    }
                    map
                }
                else -> null
            }
        }
        return runCatching { obj(top, 0) }.getOrNull()
    }
}
