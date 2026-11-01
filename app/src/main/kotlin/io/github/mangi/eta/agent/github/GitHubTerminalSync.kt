package io.github.mangi.eta.agent.github

import android.content.Context
import io.github.mangi.eta.agent.device.BoundedRootCommandExecutor
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.core.AndroidAgentLogger
import java.io.File
import java.util.Base64

/**
 * 把 GitHub 凭据同步进 Linux 环境，让终端里的 git / gh 直接可用。
 *
 * 写两个位置，按需：
 *  - `~/.git-credentials` + `.gitconfig` 的 credential.helper = store：不依赖 gh 也能 clone/push
 *  - `~/.config/gh/hosts.yml`：装了 gh 时让 gh 命令与 `gh auth git-credential` 一并可用
 *
 * 必须用 root 写：Linux 环境里的 `/root` 归 chroot 的 root 所有（700），
 * App 自身的 uid 写不进去（会 EACCES）。所以这里把整段落盘逻辑拼成固定脚本，
 * 交给 su 执行；脚本内容只由本文件的常量与 base64 编码后的凭据组成。
 */
internal object GitHubTerminalSync {
    data class Result(
        val synced: List<String> = emptyList(),
        val skipped: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val ok: Boolean get() = error == null && synced.isNotEmpty()
    }

    private val executor = BoundedRootCommandExecutor(AndroidAgentLogger)

    fun sync(context: Context, login: String, token: String): Result {
        val ready = readyEnvironments(context)
        if (ready.isEmpty()) {
            return Result(skipped = LinuxDistribution.entries.map { it.wireName })
        }
        val credentials = encode("https://x-access-token:$token@github.com\n")
        val hosts = encode(
            buildString {
                appendLine("github.com:")
                appendLine("    user: $login")
                appendLine("    oauth_token: $token")
                appendLine("    git_protocol: https")
            },
        )
        val script = buildString {
            append("set -e\n")
            ready.forEach { (_, rootfs) ->
                val home = File(rootfs, "root").absolutePath
                val gitConfig = "$home/.gitconfig"
                append("mkdir -p '$home/.config/gh'\n")
                append("printf %s '$credentials' | base64 -d > '$home/.git-credentials'\n")
                append("chmod 600 '$home/.git-credentials'\n")
                append("[ -f '$gitConfig' ] || : > '$gitConfig'\n")
                append("grep -q 'credential \"https://github.com\"' '$gitConfig' 2>/dev/null || ")
                append("printf '\\n[credential \"https://github.com\"]\\n\\thelper = store\\n' >> '$gitConfig'\n")
                append("grep -q '^[[:space:]]*email' '$gitConfig' 2>/dev/null || ")
                append("printf '\\n[user]\\n\\tname = $login\\n\\temail = ${login}@users.noreply.github.com\\n' >> '$gitConfig'\n")
                append("chmod 600 '$gitConfig'\n")
                append("if [ -x '$rootfs/usr/bin/gh' ] || [ -x '$rootfs/usr/local/bin/gh' ]; then ")
                append("printf %s '$hosts' | base64 -d > '$home/.config/gh/hosts.yml'; ")
                append("chmod 600 '$home/.config/gh/hosts.yml'; fi\n")
            }
        }
        val result = runCatching { executor.execute(script, timeoutMillis = 20_000) }
            .getOrElse { return Result(skipped = ready.map { it.first.wireName }, error = it.message ?: "写入失败") }
        if (!result.ok) {
            return Result(
                skipped = ready.map { it.first.wireName },
                error = describe(result.errorCode, result.stderr),
            )
        }
        return Result(synced = ready.map { it.first.wireName })
    }

    fun clear(context: Context): Result {
        val ready = readyEnvironments(context)
        if (ready.isEmpty()) return Result(skipped = LinuxDistribution.entries.map { it.wireName })
        val script = buildString {
            append("set -e\n")
            ready.forEach { (_, rootfs) ->
                val home = File(rootfs, "root").absolutePath
                append("rm -f '$home/.git-credentials' '$home/.config/gh/hosts.yml'\n")
            }
        }
        val result = runCatching { executor.execute(script, timeoutMillis = 20_000) }
            .getOrElse { return Result(error = it.message ?: "清除失败") }
        return if (result.ok) Result(synced = ready.map { it.first.wireName }) else {
            Result(error = describe(result.errorCode, result.stderr))
        }
    }

    private fun readyEnvironments(context: Context): List<Pair<LinuxDistribution, File>> =
        LinuxDistribution.entries.mapNotNull { distribution ->
            val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution)
            if (LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) distribution to rootfs else null
        }

    private fun describe(code: String, stderr: String): String = when (code) {
        "ROOT_REQUIRED" -> "需要 Root 权限才能写入 Linux 环境"
        "ROOT_UNAVAILABLE" -> "拿不到 Root（su 不可用）"
        "ROOT_INPUT_LIMIT", "ROOT_EXECUTOR_CLOSED" -> "写入被拒绝（$code）"
        else -> stderr.take(160).ifBlank { "写入失败（$code）" }
    }

    private fun encode(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
