package com.kite.app.foundation.runtime

import android.content.Context
import android.os.Environment
import com.kite.app.foundation.logging.Logger
import java.io.File

object ExternalExchangeManager {

    private const val LOG_TAG = "ExternalExchange"
    private const val EXCHANGE_ROOT_NAME = "Kite"
    private const val LEGACY_EXCHANGE_ROOT_NAME = "KF"
    private const val README_FILE_NAME = "README.txt"
    private const val CARD_SCHEMA_FILE_NAME = "HOME_CARD_SCHEMA.md"
    private const val FILES_DIR_NAME = "files"
    private const val DELIVERY_IN_DIR_NAME = "in"
    private const val DELIVERY_OUT_DIR_NAME = "out"
    private const val GROUPED_FILES_DIR_NAME = "files"
    private const val CARDS_DIR_NAME = "cards"
    private const val IMPORTS_DIR_NAME = "imports"
    const val CONTAINER_DELIVERY_PATH = "/chuan"
    const val CONTAINER_ALBUM_PATH = "/xiangce"
    const val CONTAINER_MOUNT_PATH = "/exchange"
    const val CONTAINER_CARDS_PATH = "/exchange/cards"

    @Synchronized
    fun ensureExchangeDir(context: Context): File {
        val appContext = context.applicationContext
        val exchangeDir = resolveHostExchangeDir()
        if (!exchangeDir.exists()) {
            val created = exchangeDir.mkdirs()
            Logger.i(LOG_TAG, "Create external delivery directory: path=${exchangeDir.absolutePath} created=$created")
        }
        migrateLegacyExchange(appContext, exchangeDir)
        ensureDeliverySubdirs(exchangeDir)
        ensureCardSchemaGuide(File(exchangeDir, CARDS_DIR_NAME))
        ensureReadme(exchangeDir)
        return exchangeDir
    }

    @Synchronized
    fun ensureCardsDir(context: Context): File {
        val cardsDir = File(ensureExchangeDir(context), CARDS_DIR_NAME).apply {
            if (!exists()) {
                val created = mkdirs()
                Logger.i(LOG_TAG, "Prepare cards directory: path=$absolutePath created=$created")
            }
        }
        ensureCardSchemaGuide(cardsDir)
        return cardsDir
    }

    @Synchronized
    fun ensureImportsDir(context: Context): File {
        return File(ensureExchangeDir(context), IMPORTS_DIR_NAME).apply {
            if (!exists()) {
                val created = mkdirs()
                Logger.i(LOG_TAG, "Prepare imports directory: path=$absolutePath created=$created")
            }
        }
    }

    @Synchronized
    fun ensureAlbumDir(): File {
        val picturesRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return try {
            if (!picturesRoot.exists()) {
                picturesRoot.mkdirs()
            }
            picturesRoot
        } catch (throwable: Throwable) {
            Logger.i(LOG_TAG, "Public Pictures directory is not available: ${throwable.message}")
            picturesRoot
        }
    }

    private fun resolveHostExchangeDir(): File = downloadExchangeDir()

    private fun downloadExchangeDir(rootName: String = EXCHANGE_ROOT_NAME): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), rootName)

    private fun mediaExchangeDir(context: Context, rootName: String): File {
        // Production and development builds share the same package-stable media directory.
        val sharedPackageName = "com.kite.app"
        val mediaRoot = context.externalMediaDirs
            .asSequence()
            .filterNotNull()
            .map { normalizeMediaRoot(it, sharedPackageName) }
            .firstOrNull()
            ?: File("/sdcard/Android/media/${sharedPackageName}")

        return if (mediaRoot.name == rootName) {
            mediaRoot
        } else {
            File(mediaRoot, rootName)
        }
    }

    private fun migrateLegacyExchange(context: Context, exchangeDir: File) {
        listOf(
            downloadExchangeDir(LEGACY_EXCHANGE_ROOT_NAME),
            mediaExchangeDir(context, LEGACY_EXCHANGE_ROOT_NAME)
        ).distinctBy { it.absolutePath }.forEach { legacyDir ->
            if (!legacyDir.exists() || legacyDir.absolutePath == exchangeDir.absolutePath) return@forEach
            legacyDir.listFiles().orEmpty().forEach childLoop@ { child ->
                val target = File(exchangeDir, child.name)
                if (target.exists()) return@childLoop
                runCatching {
                    child.copyRecursively(target, overwrite = false)
                    child.deleteRecursively()
                }.onFailure { throwable ->
                    Logger.i(LOG_TAG, "Legacy exchange migration skipped: from=${child.absolutePath} error=${throwable.message}")
                }
            }
        }
    }

    private fun normalizeMediaRoot(candidate: File, packageName: String): File {
        if (candidate.name == packageName) {
            return candidate
        }
        if (candidate.name == FILES_DIR_NAME && candidate.parentFile?.name == packageName) {
            return candidate.parentFile ?: candidate
        }
        return candidate
    }

    private fun ensureDeliverySubdirs(exchangeDir: File) {
        listOf(
            DELIVERY_IN_DIR_NAME,
            DELIVERY_OUT_DIR_NAME,
            CARDS_DIR_NAME,
            IMPORTS_DIR_NAME,
            "$GROUPED_FILES_DIR_NAME/$DELIVERY_IN_DIR_NAME",
            "$GROUPED_FILES_DIR_NAME/$DELIVERY_OUT_DIR_NAME"
        ).forEach { name ->
            val dir = File(exchangeDir, name)
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Logger.i(LOG_TAG, "Prepare delivery subdirectory: path=${dir.absolutePath} created=$created")
            }
        }
    }

    private fun ensureReadme(exchangeDir: File) {
        val readme = File(exchangeDir, README_FILE_NAME)
        if (readme.exists()) {
            return
        }
        runCatching {
            readme.writeText(
                """
                Kite file delivery area.

                Container paths:
                - /chuan: preferred short transfer path.
                - /chuan/in: files delivered from Android into Linux.
                - /chuan/out: files exported from Linux back to Android.
                - /exchange/cards: shared Kite card JSON directory.
                - /exchange: compatibility alias for existing scripts.

                This directory is backed by Download/Kite so humans can find, add, edit, and delete shared files.

                Keep shared app data here: cards, imports, delivery files, and export files.
                Move real projects into /workspace before building or editing heavily.
                """.trimIndent() + "\n"
            )
        }.onFailure { throwable ->
            Logger.i(LOG_TAG, "Exchange README is not writable; keep startup running: ${throwable.message}")
        }
    }

    private fun ensureCardSchemaGuide(cardsDir: File) {
        val guide = File(cardsDir, CARD_SCHEMA_FILE_NAME)
        val content = """
            # Kite Home Card Schema

            Put home card JSON files in this directory.

            Android path:
            - /sdcard/Download/Kite/cards

            Ubuntu/PRoot path:
            - /exchange/cards

            Android and Ubuntu see the same shared card area.

            ID rule:
            - Do not fill the card ID.
            - Use "base": { "id": "" } or omit base.id.
            - Kite assigns the local ID, writes it back, and uses it for run state, shortcuts, process binding, and stop actions.
            - Do not use top-level "id" for new cards.

            Minimal process-container test card:

            {
              "base": {
                "id": "",
                "name": "Kite Process Container Test",
                "description": "Runs a long process for stop verification",
                "icon": {
                  "type": "builtin",
                  "name": "server"
                }
              },
              "launch": {
                "openInstance": true
              },
              "recipe": [
                {
                  "type": "shell",
                  "cmd": "bash -lc 'echo KITE_PROCESS_TEST_START; sleep 300'",
                  "workdir": "/workspace",
                  "surfaceMode": "panel",
                  "timeoutMs": 600000
                }
              ]
            }

            Save it as:
            - /exchange/cards/kite-process-container-test.json

            Test flow:
            1. Refresh Kite home.
            2. Start the card.
            3. Inspect processes containing KITE_PROCESS_TEST_START or sleep 300.
            4. Close the card.
            5. Inspect again; the process group should be gone.

            Step types:
            - shell: run a Linux command inside Ubuntu/PRoot.
            - open_web: open a URL in Android/Kite.
            - terminal: open or feed terminal text.

            Do not put bridgeUrl, bridgePort, token, runId, pid, rootPid, processGroupId, systemSessionId, or runtime status into card JSON.

            Full repository guide:
            - docs/HOME_CARD_SCHEMA.md
        """.trimIndent() + "\n"
        runCatching {
            val existingContent = if (guide.exists()) {
                runCatching { guide.readText() }.getOrNull()
            } else {
                null
            }
            if (existingContent == null && !guide.exists()) {
                guide.writeText(content)
            } else if (existingContent != null && existingContent != content) {
                guide.writeText(content)
            }
        }.onFailure { throwable ->
            Logger.i(LOG_TAG, "Card schema guide is not writable; keep startup running: ${throwable.message}")
        }
    }
}
