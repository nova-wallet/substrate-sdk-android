@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.fearless_utils.keyring.json

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun ByteArray.asLittleEndianInt() = ByteBuffer.wrap(this)
    .order(ByteOrder.LITTLE_ENDIAN)
    .int

fun Int.asLittleEndianBytes() = usingLittleEndian(Int.SIZE_BYTES) {
    putInt(this@asLittleEndianBytes)
}

fun usingLittleEndian(size: Int, builder: ByteBuffer.() -> Unit): ByteArray {
    val buffer = ByteBuffer.allocate(size)
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    builder.invoke(buffer)

    return buffer.array()
}
