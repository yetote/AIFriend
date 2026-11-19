package com.feature.werewolf.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.feature.werewolf.engine.WerewolfTable
import com.feature.werewolf.engine.persistence.WerewolfGameFile
import com.ui.werewolf.data.UIWerewolfReview
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把复盘导出成一个 `.md` 文件，交给系统分享。
 *
 * 为什么是「文件」而不是像聊天记录那样直接 `EXTRA_TEXT`：Markdown 的价值在于它的**标记**，
 * 一贴进聊天框就退化成带 `**` 和 `|` 的乱码。发文件对方才能用编辑器打开、导入笔记软件。
 *
 * 写 `cacheDir/export/` 而不是外部存储：导出只是把内容递给别的应用，
 * 递完就没用了，还省掉一路存储权限。真要留档，接收方那一步再存。
 */
object WerewolfExport {

    /** 分享用的 MIME。写 `text/plain` 而不是 `text/markdown`：后者很多接收方不认，会直接失败。 */
    private const val MIME = "text/plain"

    fun share(context: Context, review: UIWerewolfReview) {
        val markdown = WerewolfMarkdown.of(review)
        val uri = runCatching {
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(dir, fileName())
            file.writeText(markdown)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

        val intent = if (uri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "狼人杀复盘")
                // 不授予读权限的话，接收方拿到的是一个打不开的 content:// 地址
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            // FileProvider 没配好的兜底：退成纯文本，至少内容不会丢
            Intent(Intent.ACTION_SEND).apply {
                type = MIME
                putExtra(Intent.EXTRA_SUBJECT, "狼人杀复盘")
                putExtra(Intent.EXTRA_TEXT, markdown)
            }
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "导出复盘"))
        }
    }

    /**
     * 分享「对局文件」：一局的完整存档，对方（或换台手机的自己）导进来能重看、能重演。
     *
     * 和 [share] 的分工：Markdown 是给**人**看的阅读材料，这个文件是给 **App** 读的数据。
     * 两者都要有，不能合并——把 JSON 塞进 Markdown 会让那份文档彻底没法读，
     * 而把 Markdown 当导入源又还原不出任何一局的局面。
     *
     * 同样走 `cacheDir/export/`：递完就没用了，不值得让用户去文件管理器里清理。
     */
    fun shareGame(
        context: Context,
        table: WerewolfTable,
        gameId: String,
        createdAt: Long,
        customPrompt: String? = null,
    ) {
        val text = WerewolfGameFile.encode(table, gameId, createdAt, customPrompt)
        val uri = runCatching {
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(dir, gameFileName())
            file.writeText(text)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = WerewolfGameFile.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "狼人杀对局文件")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享对局文件"))
        }
    }

    /**
     * 文件名带时间：同一局反复导出不会互相覆盖，接收方也能一眼排出顺序。
     * 用英文和数字，避免个别接收方对中文文件名处理出乱码。
     */
    private fun fileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "werewolf-review-$stamp.md"
    }

    /**
     * `werewolf-game-20261119-1930.wwg`。
     *
     * 后缀跟着时间走：同一天导两次不会互相覆盖，对方收到一串文件也能排出先后。
     */
    private fun gameFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "werewolf-game-$stamp.${WerewolfGameFile.EXTENSION}"
    }
}
