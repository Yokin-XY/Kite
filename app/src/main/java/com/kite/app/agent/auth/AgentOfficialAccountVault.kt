package com.kite.app.agent.auth

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kite.app.agent.sdk.account.AgentAccountCredentialSnapshot
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class AgentSavedOfficialAccount(
    val agentId: String,
    val accountId: String,
    val displayName: String,
    val createdAt: Long,
    val lastUsedAt: Long,
)

/** 账号元数据和加密原生凭据分开保存；页面只消费元数据。 */
internal interface AgentOfficialAccountVault {
    fun accounts(agentId: String): List<AgentSavedOfficialAccount>

    fun account(agentId: String, accountId: String): AgentSavedOfficialAccount?

    fun currentAccountId(agentId: String): String?

    fun save(
        account: AgentSavedOfficialAccount,
        credential: AgentAccountCredentialSnapshot,
    )

    fun credential(agentId: String, accountId: String): AgentAccountCredentialSnapshot?

    fun markCurrent(agentId: String, accountId: String)

    fun remove(agentId: String, accountId: String)
}

/**
 * Android 私有 SQLite + Android Keystore 账号档案。
 *
 * 数据库中只保存 AES-GCM 密文；Keystore 主密钥不离开系统 Keystore。任何加密/解密失败都
 * 直接报错，不降级为明文存储。
 */
internal class AndroidAgentOfficialAccountVault(context: Context) : AgentOfficialAccountVault {
    private val database = AccountDatabase(context.applicationContext)

    override fun accounts(agentId: String): List<AgentSavedOfficialAccount> =
        database.readableDatabase.query(
            TABLE_ACCOUNTS,
            arrayOf(COL_AGENT_ID, COL_ACCOUNT_ID, COL_DISPLAY_NAME, COL_CREATED_AT, COL_LAST_USED_AT),
            "$COL_AGENT_ID = ?",
            arrayOf(agentId),
            null,
            null,
            "$COL_LAST_USED_AT DESC, $COL_CREATED_AT ASC, $COL_ACCOUNT_ID ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AgentSavedOfficialAccount(
                            agentId = cursor.getString(0),
                            accountId = cursor.getString(1),
                            displayName = cursor.getString(2),
                            createdAt = cursor.getLong(3),
                            lastUsedAt = cursor.getLong(4),
                        )
                    )
                }
            }
        }

    override fun account(agentId: String, accountId: String): AgentSavedOfficialAccount? =
        database.readableDatabase.query(
            TABLE_ACCOUNTS,
            arrayOf(COL_AGENT_ID, COL_ACCOUNT_ID, COL_DISPLAY_NAME, COL_CREATED_AT, COL_LAST_USED_AT),
            "$COL_AGENT_ID = ? AND $COL_ACCOUNT_ID = ?",
            arrayOf(agentId, accountId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            AgentSavedOfficialAccount(
                agentId = cursor.getString(0),
                accountId = cursor.getString(1),
                displayName = cursor.getString(2),
                createdAt = cursor.getLong(3),
                lastUsedAt = cursor.getLong(4),
            )
        }

    override fun currentAccountId(agentId: String): String? =
        database.readableDatabase.query(
            TABLE_CURRENT,
            arrayOf(COL_ACCOUNT_ID),
            "$COL_AGENT_ID = ?",
            arrayOf(agentId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    override fun save(
        account: AgentSavedOfficialAccount,
        credential: AgentAccountCredentialSnapshot,
    ) {
        val encrypted = encrypt(storageKey(account.agentId, account.accountId), credential.bytes)
        database.writableDatabase.runInTransaction {
            val metadata = ContentValues().apply {
                put(COL_AGENT_ID, account.agentId)
                put(COL_ACCOUNT_ID, account.accountId)
                put(COL_DISPLAY_NAME, account.displayName)
                put(COL_CREATED_AT, account.createdAt)
                put(COL_LAST_USED_AT, account.lastUsedAt)
            }
            val updated = update(
                TABLE_ACCOUNTS,
                metadata,
                "$COL_AGENT_ID = ? AND $COL_ACCOUNT_ID = ?",
                arrayOf(account.agentId, account.accountId),
            )
            if (updated == 0) {
                insertOrThrow(TABLE_ACCOUNTS, null, metadata)
            }
            val credentialRow = insertWithOnConflict(
                TABLE_CREDENTIALS,
                null,
                ContentValues().apply {
                    put(COL_AGENT_ID, account.agentId)
                    put(COL_ACCOUNT_ID, account.accountId)
                    put(COL_CIPHERTEXT, encrypted)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            check(credentialRow != -1L) { "无法保存账号加密凭据" }
        }
    }

    override fun credential(agentId: String, accountId: String): AgentAccountCredentialSnapshot? =
        database.readableDatabase.query(
            TABLE_CREDENTIALS,
            arrayOf(COL_CIPHERTEXT),
            "$COL_AGENT_ID = ? AND $COL_ACCOUNT_ID = ?",
            arrayOf(agentId, accountId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val encrypted = cursor.getBlob(0)
            AgentAccountCredentialSnapshot(decrypt(storageKey(agentId, accountId), encrypted))
        }

    override fun markCurrent(agentId: String, accountId: String) {
        database.writableDatabase.runInTransaction {
            val currentRow = insertWithOnConflict(
                TABLE_CURRENT,
                null,
                ContentValues().apply {
                    put(COL_AGENT_ID, agentId)
                    put(COL_ACCOUNT_ID, accountId)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            check(currentRow != -1L) { "无法更新当前账号档案" }
            execSQL(
                "UPDATE $TABLE_ACCOUNTS SET $COL_LAST_USED_AT = ? WHERE $COL_AGENT_ID = ? AND $COL_ACCOUNT_ID = ?",
                arrayOf<Any?>(System.currentTimeMillis(), agentId, accountId),
            )
        }
    }

    override fun remove(agentId: String, accountId: String) {
        database.writableDatabase.delete(
            TABLE_ACCOUNTS,
            "$COL_AGENT_ID = ? AND $COL_ACCOUNT_ID = ?",
            arrayOf(agentId, accountId),
        )
    }

    private fun encrypt(aadKey: String, bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(aadKey.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(bytes)
        return ByteArray(cipher.iv.size + ciphertext.size).also { payload ->
            cipher.iv.copyInto(payload)
            ciphertext.copyInto(payload, cipher.iv.size)
        }
    }

    private fun decrypt(aadKey: String, payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "账号凭据密文无效" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES),
        )
        cipher.updateAAD(aadKey.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(payload.copyOfRange(IV_BYTES, payload.size))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun storageKey(agentId: String, accountId: String): String =
        "$agentId\u0000$accountId"

    private class AccountDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

        init {
            setWriteAheadLoggingEnabled(true)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            createSchema(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            createSchema(db)
        }

        private fun createSchema(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_ACCOUNTS (
                    $COL_AGENT_ID TEXT NOT NULL,
                    $COL_ACCOUNT_ID TEXT NOT NULL,
                    $COL_DISPLAY_NAME TEXT NOT NULL,
                    $COL_CREATED_AT INTEGER NOT NULL,
                    $COL_LAST_USED_AT INTEGER NOT NULL,
                    PRIMARY KEY($COL_AGENT_ID, $COL_ACCOUNT_ID)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CREDENTIALS (
                    $COL_AGENT_ID TEXT NOT NULL,
                    $COL_ACCOUNT_ID TEXT NOT NULL,
                    $COL_CIPHERTEXT BLOB NOT NULL,
                    PRIMARY KEY($COL_AGENT_ID, $COL_ACCOUNT_ID),
                    FOREIGN KEY($COL_AGENT_ID, $COL_ACCOUNT_ID)
                        REFERENCES $TABLE_ACCOUNTS($COL_AGENT_ID, $COL_ACCOUNT_ID)
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_CURRENT (
                    $COL_AGENT_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_ACCOUNT_ID TEXT NOT NULL,
                    FOREIGN KEY($COL_AGENT_ID, $COL_ACCOUNT_ID)
                        REFERENCES $TABLE_ACCOUNTS($COL_AGENT_ID, $COL_ACCOUNT_ID)
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )
        }
    }

    private fun SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "kite_agent_accounts.db"
        const val DATABASE_VERSION = 1
        const val TABLE_ACCOUNTS = "agent_account_metadata"
        const val TABLE_CREDENTIALS = "agent_account_credentials"
        const val TABLE_CURRENT = "agent_account_current"
        const val COL_AGENT_ID = "agent_id"
        const val COL_ACCOUNT_ID = "account_id"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_CREATED_AT = "created_at"
        const val COL_LAST_USED_AT = "last_used_at"
        const val COL_CIPHERTEXT = "ciphertext"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kite.agent.accounts.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
