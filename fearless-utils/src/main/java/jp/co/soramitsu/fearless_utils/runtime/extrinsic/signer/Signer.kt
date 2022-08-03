package jp.co.soramitsu.fearless_utils.runtime.extrinsic.signer

import jp.co.soramitsu.fearless_utils.encrypt.SignatureWrapper
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic.EncodingInstance.CallRepresentation

class SignerPayloadRaw(
    val message: ByteArray,
    val accountId: AccountId,
    val skipMessageHashing: Boolean = false,
) {
    companion object;
}

class SignerPayloadExtrinsic(
    val runtime: RuntimeSnapshot,
    val extrinsicType: Extrinsic,

    val accountId: AccountId,

    val call: CallRepresentation,
    val signedExtras: Map<String, Any?>,
    val additionalExtras: Map<String, Any?>
)

fun SignerPayloadRaw.Companion.fromUtf8(
    utf8Message: String,
    accountId: AccountId,
    skipMessageHashing: Boolean = false
) = SignerPayloadRaw(utf8Message.encodeToByteArray(), accountId, skipMessageHashing)

fun SignerPayloadRaw.Companion.fromHex(
    hexMessage: String,
    accountId: AccountId,
    skipMessageHashing: Boolean = false
) = SignerPayloadRaw(hexMessage.fromHex(), accountId, skipMessageHashing)

interface Signer {

    suspend fun signExtrinsic(payloadExtrinsic: SignerPayloadExtrinsic): SignatureWrapper

    suspend fun signRaw(payload: SignerPayloadRaw): SignatureWrapper
}
