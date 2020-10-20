package jp.co.soramitsu.fearless_utils.hash

import net.jpountz.xxhash.XXHash64
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun XXHash64.hash(bytes: ByteArray, seed: Long = 0) = hash(bytes, 0, bytes.size, seed)

fun Blake2b128.hashConcat(bytes: ByteArray) = digest(bytes) + bytes

fun XXHash64.hashConcat(bytes: ByteArray): ByteArray {
    val hashBytes = ByteBuffer.allocate(Long.SIZE_BYTES + bytes.size)
    hashBytes.order(ByteOrder.LITTLE_ENDIAN)

    hashBytes.putLong(hash(bytes))
    hashBytes.put(bytes)

    return hashBytes.array()
}