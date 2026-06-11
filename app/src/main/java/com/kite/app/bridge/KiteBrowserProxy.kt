package com.kite.app.bridge

import android.content.Context
import com.kftest.app.foundation.workspace.WorkSurfaceRuntimeBridge
import java.io.File

data class KiteBrowserOpenRequest(
    val url: String,
    val recipeId: String? = null,
    val instanceId: String? = null,
    val source: String = SOURCE_UBUNTU_BROWSER
) {
    companion object {
        const val SOURCE_UBUNTU_BROWSER = "ubuntu_browser"
    }
}

object KiteBrowserProxyInstaller {
    const val ENDPOINT = "http://127.0.0.1:8791/open-web"
    const val CONTAINER_COMMAND = "/workspace/.kf/bin/kite-open-url"
    private const val DESKTOP_FILE = "kite-browser.desktop"
    private const val MARKER = "# Kite generated browser proxy"

    fun environment(
        context: Context,
        recipeId: String,
        instanceId: String,
        source: String
    ): Map<String, String> {
        ensureInstalled(context.applicationContext)
        return linkedMapOf(
            "KITE_OPEN_URL_ENDPOINT" to ENDPOINT,
            "KITE_RECIPE_ID" to recipeId,
            "KITE_INSTANCE_ID" to instanceId,
            "KITE_BROWSER_SOURCE" to source,
            "KITE_BROWSER_PROXY" to CONTAINER_COMMAND,
            "BROWSER" to CONTAINER_COMMAND
        )
    }

    fun defaultEnvironment(
        context: Context,
        source: String
    ): Map<String, String> {
        ensureInstalled(context.applicationContext)
        return linkedMapOf(
            "KITE_OPEN_URL_ENDPOINT" to ENDPOINT,
            "KITE_BROWSER_SOURCE" to source,
            "KITE_BROWSER_PROXY" to CONTAINER_COMMAND,
            "BROWSER" to CONTAINER_COMMAND
        )
    }

    fun ensureInstalled(context: Context) {
        runCatching {
            val container = WorkSurfaceRuntimeBridge.resolveActiveContainer(context.applicationContext)
            val binDir = File(container.workspacePath, ".kf/bin").also { it.mkdirs() }
            val script = proxyScript()
            listOf(
                "kite-open-url",
                "xdg-open",
                "sensible-browser",
                "www-browser",
                "x-www-browser",
                "gnome-open",
                "kde-open",
                "kde-open5",
                "exo-open"
            ).forEach { name ->
                val target = File(binDir, name)
                if (shouldWrite(target)) {
                    target.writeText(script)
                    target.setExecutable(true, false)
                }
            }
            installDesktopContract(
                dataHome = File(container.rootfsPath, "root/.local/share"),
                configHome = File(container.rootfsPath, "root/.config")
            )
            installDesktopContract(
                dataHome = File(container.workspacePath, ".kf/share"),
                configHome = File(container.workspacePath, ".kf/config")
            )
        }
    }

    private fun shouldWrite(file: File): Boolean {
        if (!file.exists()) return true
        val head = runCatching { file.readText().take(256) }.getOrDefault("")
        return head.contains(MARKER)
    }

    private fun installDesktopContract(dataHome: File, configHome: File) {
        val applicationsDir = File(dataHome, "applications").also { it.mkdirs() }
        val desktop = File(applicationsDir, DESKTOP_FILE)
        if (shouldWrite(desktop)) {
            desktop.writeText(desktopEntry())
        }

        configHome.mkdirs()
        val mimeapps = File(configHome, "mimeapps.list")
        if (shouldWrite(mimeapps)) {
            mimeapps.writeText(mimeappsList())
        }
    }

    private fun desktopEntry(): String = """
        |[Desktop Entry]
        |$MARKER
        |Type=Application
        |Name=Kite Browser
        |Comment=Open URLs in the Android Kite browser surface
        |Exec=$CONTAINER_COMMAND %u
        |Terminal=false
        |NoDisplay=true
        |Categories=Network;WebBrowser;
        |MimeType=text/html;text/xml;application/xhtml+xml;application/xml;x-scheme-handler/http;x-scheme-handler/https;
    """.trimMargin()

    private fun mimeappsList(): String = """
        |$MARKER
        |[Default Applications]
        |x-scheme-handler/http=$DESKTOP_FILE
        |x-scheme-handler/https=$DESKTOP_FILE
        |text/html=$DESKTOP_FILE
        |text/xml=$DESKTOP_FILE
        |application/xhtml+xml=$DESKTOP_FILE
        |application/xml=$DESKTOP_FILE
        |
        |[Added Associations]
        |x-scheme-handler/http=$DESKTOP_FILE;
        |x-scheme-handler/https=$DESKTOP_FILE;
        |text/html=$DESKTOP_FILE;
        |text/xml=$DESKTOP_FILE;
        |application/xhtml+xml=$DESKTOP_FILE;
        |application/xml=$DESKTOP_FILE;
    """.trimMargin()

    private fun proxyScript(): String = """
        |#!/usr/bin/env sh
        |$MARKER
        |url=""
        |for arg in "${'$'}@"; do
        |  case "${'$'}arg" in
        |    --) continue ;;
        |    -*) continue ;;
        |    *) url="${'$'}arg"; break ;;
        |  esac
        |done
        |if [ -z "${'$'}url" ]; then
        |  exit 0
        |fi
        |
        |endpoint="${'$'}{KITE_OPEN_URL_ENDPOINT:-$ENDPOINT}"
        |recipe="${'$'}{KITE_RECIPE_ID:-}"
        |instance="${'$'}{KITE_INSTANCE_ID:-}"
        |source="${'$'}{KITE_BROWSER_SOURCE:-${KiteBrowserOpenRequest.SOURCE_UBUNTU_BROWSER}}"
        |query="recipeId=${'$'}recipe&instanceId=${'$'}instance&source=${'$'}source"
        |
        |if command -v curl >/dev/null 2>&1; then
        |  printf '%s' "${'$'}url" | curl -fsS -X POST \
        |    -H 'Content-Type: text/plain; charset=utf-8' \
        |    --data-binary @- \
        |    "${'$'}endpoint?${'$'}query" >/dev/null 2>&1 && exit 0
        |fi
        |
        |if command -v wget >/dev/null 2>&1; then
        |  wget -q -O /dev/null --post-data="${'$'}url" "${'$'}endpoint?${'$'}query" >/dev/null 2>&1 && exit 0
        |fi
        |
        |printf 'Kite browser proxy could not reach Android endpoint: %s\n' "${'$'}url" >&2
        |exit 0
    """.trimMargin()
}
