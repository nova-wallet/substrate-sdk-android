package jp.co.soramitsu.fearless_utils.runtime.extrinsic.signer

import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.AdditionalExtras
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.fearless_utils.scale.utils.directWrite

private const val PAYLOAD_HASH_THRESHOLD = 256

fun bytesOf(writer: (ScaleCodecWriter) -> Unit) = useScaleWriter(writer)
fun bytesOf(vararg writers: (ScaleCodecWriter) -> Unit) = useScaleWriter {
    writers.forEach { it(this) }
}

fun SignerPayloadExtrinsic.encodeCallDataTo(writer: ScaleCodecWriter) {
    when (call) {
        is Extrinsic.EncodingInstance.CallRepresentation.Bytes ->
            writer.directWrite(call.bytes)

        is Extrinsic.EncodingInstance.CallRepresentation.Instance ->
            GenericCall.encode(writer, runtime, call.call)
    }
}

fun SignerPayloadExtrinsic.encodedCallData() = bytesOf(::encodeCallDataTo)

fun SignerPayloadExtrinsic.encodeExtensionsTo(writer: ScaleCodecWriter) {
    extrinsicType.signedExtrasType.encode(writer, runtime, signedExtras)
    AdditionalExtras.default.encode(writer, runtime, additionalExtras)
}

fun SignerPayloadExtrinsic.encodedExtensions() = bytesOf(::encodeExtensionsTo)

fun SignerPayloadExtrinsic.encodedSignaturePayload(hashBigPayloads: Boolean = true): ByteArray {
    val payloadBytes = bytesOf(
        ::encodeCallDataTo,
        ::encodeExtensionsTo
    )

    val messageToSign = if (hashBigPayloads && payloadBytes.size > PAYLOAD_HASH_THRESHOLD) {
        payloadBytes.blake2b256()
    } else {
        payloadBytes
    }

    return messageToSign
}

val SignerPayloadExtrinsic.genesisHash
    get() = additionalExtras[AdditionalExtras.GENESIS] as ByteArray
