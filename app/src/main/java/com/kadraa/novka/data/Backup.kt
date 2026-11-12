package com.kadraa.novka.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupFile(
    val version: Int = BackupCodec.VERSION,
    val createdAt: Long,
    val settings: Settings,
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Tx>,
    val rules: List<MerchantRule>,
    val cards: List<CardMapping>,
    val budgets: List<Budget>,
    val recurring: List<RecurringRule>,
    val rates: List<ExchangeRate>,
    val goals: List<Goal>,
    val debts: List<Debt>,
    val debtPayments: List<DebtPayment>,
)

class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NEEDS_PASSWORD, WRONG_PASSWORD, CORRUPT, TOO_NEW }
}

/** Plain backups are JSON. Encrypted ones are MAGIC + salt + nonce + AES-256-GCM ciphertext. */
object BackupCodec {
    const val VERSION = 1
    private val MAGIC = "NOVKA1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val NONCE = 12
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(file: BackupFile, password: String?): ByteArray {
        val plain = json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)
        if (password.isNullOrEmpty()) return plain
        val random = SecureRandom()
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        return MAGIC + salt + nonce + cipher.doFinal(plain)
    }

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size + SALT + NONCE && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun decode(bytes: ByteArray, password: String?): BackupFile {
        val plain = if (isEncrypted(bytes)) {
            if (password.isNullOrEmpty()) throw BackupException(BackupException.Reason.NEEDS_PASSWORD)
            val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + SALT)
            val nonce = bytes.copyOfRange(MAGIC.size + SALT, MAGIC.size + SALT + NONCE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
            try {
                cipher.doFinal(bytes, MAGIC.size + SALT + NONCE, bytes.size - MAGIC.size - SALT - NONCE)
            } catch (e: AEADBadTagException) {
                throw BackupException(BackupException.Reason.WRONG_PASSWORD)
            }
        } else {
            bytes
        }
        val file = try {
            json.decodeFromString(BackupFile.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.CORRUPT)
        }
        if (file.version > VERSION) throw BackupException(BackupException.Reason.TOO_NEW)
        return file
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 210_000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
}
