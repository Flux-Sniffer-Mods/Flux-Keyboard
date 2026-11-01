package it.palsoftware.pastiera.airplay

import java.io.ByteArrayOutputStream

/** HomeKit's TLV8, which AirPlay pairing speaks: values over 255 bytes split over the same tag. */
internal object Tlv8 {
    const val METHOD = 0x00
    const val IDENTIFIER = 0x01
    const val SALT = 0x02
    const val PUBLIC_KEY = 0x03
    const val PROOF = 0x04
    const val ENCRYPTED_DATA = 0x05
    const val SEQ_NO = 0x06
    const val ERROR = 0x07
    const val SIGNATURE = 0x0A
    const val FLAGS = 0x13

    /** Pairing that lasts only this connection, with the fixed PIN 3939. */
    const val FLAG_TRANSIENT = 0x10

    fun encode(vararg items: Pair<Int, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((tag, value) in items) {
            var pos = 0
            if (value.isEmpty()) { out.write(tag); out.write(0) }
            while (pos < value.size) {
                val size = minOf(255, value.size - pos)
                out.write(tag)
                out.write(size)
                out.write(value, pos, size)
                pos += size
            }
        }
        return out.toByteArray()
    }

    fun decode(data: ByteArray): Map<Int, ByteArray> {
        val result = LinkedHashMap<Int, ByteArray>()
        var pos = 0
        while (pos + 2 <= data.size) {
            val tag = data[pos].toInt() and 0xFF
            val length = data[pos + 1].toInt() and 0xFF
            val end = minOf(data.size, pos + 2 + length)
            val value = data.copyOfRange(pos + 2, end)
            result[tag] = result[tag]?.plus(value) ?: value
            pos = end
        }
        return result
    }
}
