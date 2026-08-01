package com.kite.app.agent.session.opencode

import com.kite.app.agent.contract.AgentOperationResult
import com.kite.app.agent.contract.AgentSessionRenameRequest
import com.kite.app.agent.session.AgentSessionAdministrationAdapter
import com.kite.app.agent.session.AgentSessionCommand
import com.kite.app.agent.session.AgentSessionCommandExecutor

/** OpenCode 的产品差异只在该适配器内，公共 Runtime 不拼接产品命令。 */
class OpenCodeAgentSessionAdministrationAdapter(
    private val executor: AgentSessionCommandExecutor
) : AgentSessionAdministrationAdapter {
    override val adapterId: String = ADAPTER_ID
    override val supportsRename: Boolean = true

    override suspend fun deleteSession(sessionId: String, cwd: String): AgentOperationResult<Unit> {
        val normalized = validSessionId(sessionId)
            ?: return AgentOperationResult.Failure("OpenCode 会话 ID 无效")
        return executor.execute(
            AgentSessionCommand(
                argv = listOf("opencode", "session", "delete", normalized),
                cwd = cwd,
                operationLabel = "删除 OpenCode 会话",
            )
        )
    }

    override suspend fun renameSession(
        request: AgentSessionRenameRequest,
        cwd: String,
    ): AgentOperationResult<Unit> {
        val sessionId = validSessionId(request.sessionId)
            ?: return AgentOperationResult.Failure("OpenCode 会话 ID 无效")
        val title = request.title.trim()
        if (title.isBlank() || title.any(Char::isISOControl)) {
            return AgentOperationResult.Failure("会话名称不能为空或包含控制字符")
        }
        val payload = "{\"title\":\"${jsonString(title)}\"}"
        return executor.execute(
            AgentSessionCommand(
                argv = listOf("sh", "-c", RENAME_SCRIPT, "kite-opencode-rename", sessionId),
                cwd = cwd,
                stdinLine = payload,
                operationLabel = "重命名 OpenCode 会话",
            )
        )
    }

    private fun validSessionId(value: String): String? = value.trim()
        .takeIf { SESSION_ID.matches(it) }

    private fun jsonString(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(character)
            }
        }
    }

    companion object {
        const val ADAPTER_ID = "opencode"
        private val SESSION_ID = Regex("[A-Za-z0-9._-]+")

        /**
         * OpenCode CLI 没有 rename 子命令；这里启动短时本地 server，调用官方
         * `PATCH /session/{id}` API，由 OpenCode 自己更新会话存储与事件。
         */
        private val RENAME_SCRIPT = """
            set -eu
            session_id="${'$'}1"
            port=${'$'}((20000 + (${'$'}${'$'} % 30000)))
            log_file=${'$'}(mktemp)
            payload_file=${'$'}(mktemp)
            server_pid=""
            cleanup() {
              if [ -n "${'$'}server_pid" ]; then kill "${'$'}server_pid" 2>/dev/null || true; fi
              rm -f "${'$'}log_file" "${'$'}payload_file"
            }
            trap cleanup EXIT INT TERM
            IFS= read -r payload
            printf '%s' "${'$'}payload" > "${'$'}payload_file"
            opencode serve --hostname 127.0.0.1 --port "${'$'}port" >"${'$'}log_file" 2>&1 &
            server_pid=${'$'}!
            attempt=0
            until curl -fsS "http://127.0.0.1:${'$'}port/path" >/dev/null 2>&1; do
              if ! kill -0 "${'$'}server_pid" 2>/dev/null; then cat "${'$'}log_file" >&2; exit 1; fi
              attempt=${'$'}((attempt + 1))
              if [ "${'$'}attempt" -ge 100 ]; then echo 'OpenCode server 启动超时' >&2; exit 1; fi
              sleep 0.1
            done
            curl -fsS -X PATCH -H 'Content-Type: application/json' \
              --data-binary "@${'$'}payload_file" \
              "http://127.0.0.1:${'$'}port/session/${'$'}session_id" >/dev/null
        """.trimIndent()
    }
}
