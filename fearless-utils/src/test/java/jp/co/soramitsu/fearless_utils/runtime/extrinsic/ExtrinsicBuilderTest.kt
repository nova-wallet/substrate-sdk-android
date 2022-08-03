package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.integration.transfer
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.multiAddressFromId
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.signer.KeyPairSigner
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.publicKeyToSubstrateAccountId
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

private val KEYPAIR = BaseKeypair(
    publicKey = "fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168".fromHex(),
    privateKey = "f3923eea431177cd21906d4308aea61c037055fb00575cae687217c6d8b2397f".fromHex()
)

private const val SINGLE_TRANSFER_EXTRINSIC =
    "0x41028400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d16800080bfe8bc67f44b498239887dc5679523cfcb1d20fd9ec9d6bae0a385cca118d2cb7ef9f4674d52a810feb32932d7c6fe3e05ce9e06cd72cf499c8692206410ab5038800040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402"
private val TRANSFER_CALL_BYTES =
    "0x040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402".fromHex()
private const val EXTRINSIC_SIGNATURE =
    "0x00080bfe8bc67f44b498239887dc5679523cfcb1d20fd9ec9d6bae0a385cca118d2cb7ef9f4674d52a810feb32932d7c6fe3e05ce9e06cd72cf499c8692206410a"

private const val BIG_TRANSACTION =
    "0x790e8400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168008d6ba41aa56d09071bbd96c1aa0378000b8a048699d5b751a27a01e7c3ce955363005df217f3ef1edb63d4c26787e8743d3797a1cf76fcaf3ae4899bd2cc660eb5038800100250040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402"

private fun ExtrinsicBuilder.testSingleTransfer(): ExtrinsicBuilder {
    return transfer(
        recipientAccountId = "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex(),
        amount = BigInteger("10000000000")
    )
}

class ExtrinsicBuilderTest {

    val runtime = RealRuntimeProvider.buildRuntime("westend")

    @Test
    fun `should build extrinsic from raw call bytes`() = runBlockingTest{
        val extrinsic = createExtrinsicBuilder()
            .build(TRANSFER_CALL_BYTES)

        assertEquals(SINGLE_TRANSFER_EXTRINSIC, extrinsic)
    }

    @Test
    fun `should build single transfer extrinsic`() = runBlockingTest {
        val encoded = createExtrinsicBuilder()
            .testSingleTransfer()
            .build()

        assertEquals(SINGLE_TRANSFER_EXTRINSIC, encoded)
    }

    @Test
    fun `should build extrinsic signature from call instance`() = runBlockingTest {
        val actualSignature = createExtrinsicBuilder()
            .testSingleTransfer()
            .buildSignature()

        assertEquals(EXTRINSIC_SIGNATURE, actualSignature)
    }

    @Test
    fun `should build extrinsic signature from raw call bytes`() = runBlockingTest {
        val extrinsic = createExtrinsicBuilder()
            .buildSignature(TRANSFER_CALL_BYTES)

        assertEquals(EXTRINSIC_SIGNATURE, extrinsic)
    }

    @Test
    fun `should replace call`() = runBlockingTest {
        val wrongAMount = "123".toBigInteger()
        val recipientAccountId =
            "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex()

        val encoded = createExtrinsicBuilder()
            .transfer(
                recipientAccountId = recipientAccountId,
                amount = wrongAMount
            )
            .reset()
            .testSingleTransfer()
            .build()

        assertEquals(SINGLE_TRANSFER_EXTRINSIC, encoded)
    }

    @Test
    fun `should build batch extrinsic`() = runBlockingTest {
        val extrinsicInHex =
            "0xf1028400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d168005b94d4436372ba74895936695e97d543358219e77f3e827f77b2e26f53413363a5dd098e172a51308e7d35aa6c03c5f171c4b43732db61c3d86b62d83e626b07b5038800100008040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402040000340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c630700e40b5402"

        val builder = createExtrinsicBuilder()

        repeat(2) {
            builder.transfer(
                recipientAccountId = "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex(),
                amount = BigInteger("10000000000")
            )
        }

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    @Test
    fun `should build batch_all extrinsic`() = runBlockingTest{
        val extrinsicBuilder = createExtrinsicBuilder()

        repeat(2) {
            extrinsicBuilder.transfer(
                recipientAccountId = "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex(),
                amount = BigInteger("10000000000")
            )
        }

        val encoded = extrinsicBuilder.build(useBatchAll = true)
        val decoded = Extrinsic().fromHex(runtime, encoded)

        assertEquals(decoded.call.function.name, "batch_all")
    }

    @Test
    fun `should build big extrinsic`() = runBlockingTest{
        val extrinsicBuilder = createExtrinsicBuilder()

        repeat(20) {
            extrinsicBuilder.transfer(
                recipientAccountId = "340a806419d5e278172e45cb0e50da1b031795366c99ddfe0a680bd53b142c63".fromHex(),
                amount = BigInteger("10000000000")
            )
        }

        val encoded = extrinsicBuilder.build(useBatchAll = true)

        assertEquals(BIG_TRANSACTION, encoded)
    }

    @Test
    fun `should build single transfer extrinsic statemine`() = runBlockingTest{
        val runtime = RealRuntimeProvider.buildRuntimeV14("statemine")

        val extrinsicInHex =
            "0x45028400fdc41550fb5186d71cae699c31731b3e1baa10680c7bd6b3831a6d222cf4d1680045ba1f9d291fff7dddf36f7ec060405d5e87ac8fab8832cfcc66858e6975141748ce89c41bda6c3a84204d3c6f929b928702168ca38bbed69b172044b599a10ab5038800000a0000bcc5ecf679ebd776866a04c212a4ec5dc45cefab57d7aa858c389844e212693f0700e40b5402"

        val chargeAssetTxPayment = SignedExtension(
            name = "ChargeAssetTxPayment",
            type = runtime.typeRegistry["pallet_asset_tx_payment.ChargeAssetTxPayment"]!!
        )

        val builder = ExtrinsicBuilder(
            runtime = runtime,
            nonce = 34.toBigInteger(),
            runtimeVersion = RuntimeVersion(601, 4),
            genesisHash = "48239ef607d7928874027a43a67689209727dfb3d3dc5e5b03a39bdc2eda771a".fromHex(),
            signer = keypairSigner(),
            accountId = KEYPAIR.publicKey.publicKeyToSubstrateAccountId(),
            era = Era.Mortal(64, 59),
            customSignedExtensions = mapOf(
                chargeAssetTxPayment to Struct.Instance(
                    mapOf(
                        "tip" to BigInteger.ZERO,
                        "assetId" to null
                    )
                )
            ),
            blockHash = "0xdd7532c5c01242696001e57cded1bc1326379059300287552a9c344e5bea1070".fromHex()
        )

        builder.transfer(
            recipientAccountId = "GqqKJJZ1MtiWiC6CzNg3g8bawriq6HZioHW1NEpxdf6Q6P5".toAccountId(),
            amount = BigInteger("10000000000")
        )

        val encoded = builder.build()

        assertEquals(extrinsicInHex, encoded)
    }

    private fun createExtrinsicBuilder() = ExtrinsicBuilder(
        runtime = runtime,
        signer = keypairSigner(),
        nonce = 34.toBigInteger(),
        runtimeVersion = RuntimeVersion(48, 4),
        genesisHash = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e".fromHex(),
        accountId = KEYPAIR.publicKey.publicKeyToSubstrateAccountId(),
        era = Era.Mortal(64, 59),
        blockHash = "0x1b876104c68b4a8924c098d61d2ad798761bb6fff55cca2885939ffc27ef5ecb".fromHex()
    )

    private fun keypairSigner() = KeyPairSigner(
        keypair = KEYPAIR,
        encryption = MultiChainEncryption.Substrate(EncryptionType.ED25519)
    )
}