package jp.co.soramitsu.fearless_utils.encrypt

enum class EncryptionType(val rawName: String, val signatureVersion: Int) {
    ED25519("ed25519", 0),
    SR25519("sr25519", 1),
    ECDSA("ecdsa", 2);

    companion object {
        fun fromString(string: String): EncryptionType {
            return when (string) {
                SR25519.rawName -> SR25519
                ECDSA.rawName -> ECDSA
                ED25519.rawName -> ED25519
                else -> throw JsonSeedDecodingException.UnsupportedEncryptionTypeException()
            }
        }
    }
}