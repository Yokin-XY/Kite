package com.kite.app.bridge

import android.content.Context
import com.kite.app.foundation.workspace.WorkSurfaceRuntimeBridge
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

data class KiteDesktopOpenRequest(
    val command: String,
    val title: String? = null,
    val recipeId: String? = null,
    val instanceId: String? = null,
    val source: String = SOURCE_UBUNTU_DESKTOP
) {
    companion object {
        const val SOURCE_UBUNTU_DESKTOP = "ubuntu_desktop"
    }
}

data class KiteDesktopOpenResponse(
    val accepted: Boolean,
    val recipeId: String?,
    val instanceId: String?,
    val display: String,
    val socketPath: String,
    val error: String = ""
)

data class KiteInstallApkRequest(
    val path: String,
    val source: String = SOURCE_UBUNTU_SHELL
) {
    companion object {
        const val SOURCE_UBUNTU_SHELL = "ubuntu_shell"
    }
}

data class KiteInstallApkResponse(
    val accepted: Boolean,
    val path: String,
    val resolvedPath: String = "",
    val error: String = ""
)

object KiteBrowserProxyInstaller {
    const val ENDPOINT = "http://127.0.0.1:8791/open-web"
    // T014b/c：Android 注入的浏览器代理落在共享 .kf/system/bin，不写环境变化目录 .kf/bin。
    const val CONTAINER_COMMAND = "/workspace/.kf/system/bin/kite-open-url"
    private const val DESKTOP_FILE = "kite-browser.desktop"
    private const val MARKER = "# Kite generated browser proxy"

    fun environment(
        context: Context,
        recipeId: String,
        instanceId: String,
        source: String
    ): Map<String, String> {
        ensureInstalled(context.applicationContext)
        KiteDesktopProxyInstaller.ensureInstalled(context.applicationContext)
        return linkedMapOf(
            "KITE_OPEN_URL_ENDPOINT" to ENDPOINT,
            "KITE_RECIPE_ID" to recipeId,
            "KITE_CARD_INSTANCE_ID" to instanceId,
            "KITE_INSTANCE_ID" to instanceId,
            "KITE_BROWSER_SOURCE" to source,
            "KITE_BROWSER_PROXY" to CONTAINER_COMMAND,
            "BROWSER" to CONTAINER_COMMAND
        ).apply {
            putAll(KiteDesktopProxyInstaller.environmentVars(recipeId, instanceId, source))
        }
    }

    fun defaultEnvironment(
        context: Context,
        source: String
    ): Map<String, String> {
        ensureInstalled(context.applicationContext)
        KiteDesktopProxyInstaller.ensureInstalled(context.applicationContext)
        return linkedMapOf(
            "KITE_OPEN_URL_ENDPOINT" to ENDPOINT,
            "KITE_BROWSER_SOURCE" to source,
            "KITE_BROWSER_PROXY" to CONTAINER_COMMAND,
            "BROWSER" to CONTAINER_COMMAND
        ).apply {
            putAll(KiteDesktopProxyInstaller.environmentVars(recipeId = "", instanceId = "", source = source))
        }
    }

    fun ensureInstalled(context: Context) {
        runCatching {
            val container = WorkSurfaceRuntimeBridge.resolveActiveContainer(context.applicationContext)
            // T014b/c：Android 注入的浏览器代理属于 Android 持有的 helper，落在共享 .kf/system/bin；
            // 不再写环境变化目录 .kf/bin，也不再直接写 rootfs 的 /root/.local/share、/root/.config。
            val binDir = File(container.workspacePath, ".kf/system/bin").also { it.mkdirs() }
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
            // desktop contract 只写 workspace 侧（bind 到 /workspace/.kf），由 XDG_DATA_HOME/
            // XDG_CONFIG_HOME 指向；不再直接写 rootfs。
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
        |instance="${'$'}{KITE_CARD_INSTANCE_ID:-${'$'}{KITE_INSTANCE_ID:-}}"
        |source="${'$'}{KITE_BROWSER_SOURCE:-${KiteBrowserOpenRequest.SOURCE_UBUNTU_BROWSER}}"
        |query="recipeId=${'$'}recipe&instanceId=${'$'}instance&cardInstanceId=${'$'}instance&source=${'$'}source"
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

object KiteDesktopProxyInstaller {
    const val ENDPOINT = "http://127.0.0.1:8791/open-desktop"
    // T014b/c：Android 注入的桌面代理落在共享 .kf/system/bin。
    const val CONTAINER_COMMAND = "/workspace/.kf/system/bin/kite-open-desktop"
    private const val MARKER = "# Kite generated desktop proxy"

    fun environmentVars(
        recipeId: String,
        instanceId: String,
        source: String
    ): Map<String, String> = linkedMapOf(
        "KITE_OPEN_DESKTOP_ENDPOINT" to ENDPOINT,
        "KITE_DESKTOP_SOURCE" to source,
        "KITE_DESKTOP_PROXY" to CONTAINER_COMMAND,
        "KITE_RECIPE_ID" to recipeId,
        "KITE_CARD_INSTANCE_ID" to instanceId,
        "KITE_INSTANCE_ID" to instanceId
    )

    fun ensureInstalled(context: Context) {
        runCatching {
            val container = WorkSurfaceRuntimeBridge.resolveActiveContainer(context.applicationContext)
            val binDir = File(container.workspacePath, ".kf/system/bin").also { it.mkdirs() }
            val target = File(binDir, "kite-open-desktop")
            if (shouldWrite(target)) {
                target.writeText(proxyScript())
                target.setExecutable(true, false)
            }
        }
    }

    private fun shouldWrite(file: File): Boolean {
        if (!file.exists()) return true
        val head = runCatching { file.readText().take(256) }.getOrDefault("")
        return head.contains(MARKER)
    }

    private fun proxyScript(): String = """
        |#!/usr/bin/env sh
        |$MARKER
        |command="${'$'}*"
        |if [ -z "${'$'}command" ]; then
        |  printf 'usage: kite-open-desktop <command>\n' >&2
        |  exit 2
        |fi
        |
        |endpoint="${'$'}{KITE_OPEN_DESKTOP_ENDPOINT:-$ENDPOINT}"
        |recipe="${'$'}{KITE_RECIPE_ID:-}"
        |instance="${'$'}{KITE_CARD_INSTANCE_ID:-${'$'}{KITE_INSTANCE_ID:-}}"
        |source="${'$'}{KITE_DESKTOP_SOURCE:-${KiteDesktopOpenRequest.SOURCE_UBUNTU_DESKTOP}}"
        |query="recipeId=${'$'}recipe&instanceId=${'$'}instance&cardInstanceId=${'$'}instance&source=${'$'}source"
        |
        |if command -v curl >/dev/null 2>&1; then
        |  response="${'$'}(printf '%s' "${'$'}command" | curl -fsS -X POST \
        |    -H 'Content-Type: text/plain; charset=utf-8' \
        |    --data-binary @- \
        |    "${'$'}endpoint?${'$'}query" 2>/dev/null)" || response=""
        |elif command -v wget >/dev/null 2>&1; then
        |  response="${'$'}(wget -q -O - --post-data="${'$'}command" "${'$'}endpoint?${'$'}query" 2>/dev/null)" || response=""
        |else
        |  printf 'Kite desktop proxy needs curl or wget\n' >&2
        |  exit 127
        |fi
        |
        |display="${'$'}(printf '%s' "${'$'}response" | sed -n 's/.*"display":"\([^"]*\)".*/\1/p')"
        |socket="${'$'}(printf '%s' "${'$'}response" | sed -n 's/.*"socketPath":"\([^"]*\)".*/\1/p')"
        |if [ -z "${'$'}display" ]; then
        |  printf 'Kite desktop proxy could not allocate display for: %s\n' "${'$'}command" >&2
        |  exit 1
        |fi
        |
        |export DISPLAY="${'$'}display"
        |export KITE_X11_DISPLAY="${'$'}display"
        |export KITE_X11_SOCKET="${'$'}socket"
        |sh -lc "${'$'}command"
    """.trimMargin()
}
