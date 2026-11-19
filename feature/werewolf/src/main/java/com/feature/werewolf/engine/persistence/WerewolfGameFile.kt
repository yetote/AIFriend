package com.feature.werewolf.engine.persistence

import com.feature.werewolf.engine.WerewolfTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 「对局文件」：一局的完整存档，能在另一台手机上导回来重看、重演。
 *
 * 为什么不是直接分享复盘 Markdown：那份文档是给人读的，从它反推不出任何一局的局面——
 * 谁在第几夜被刀、谁在什么时候验了谁、每句话是谁说的，到了 Markdown 里已经揉成自然语言。
 * 想「导进来还能演一遍」，只能带走整份快照。
 *
 * 为什么再包一层信封而不是直接给快照 JSON：导进来的东西得先能认出来。
 * 用户从文件管理器里挑错一个 `.json`（聊天记录、模型配置……），直接当快照解会读出一堆
 * 缺字段的默认值，最后变成一局没有玩家的空牌桌，比拒绝更难解释。信封只多几十个字节，
 * 换来「这不是对局文件」这句话能说清楚。
 *
 * 但**不校验也不签名**：这是玩家自己搬自己的存档，不是分发渠道。文件被手改坏了，
 * 能读就接着读，读不出来就报「内容不完整」——不存在需要防的第三方。
 */
object WerewolfGameFile {

    /** 信封标识。改这个词等于换一种文件，旧文件会被当成「不是对局文件」。 */
    const val FORMAT = "aifriend.werewolf.game"

    /** 信封版本。加字段不用改它；改了 [GameFileDto] 的语义才加一。 */
    const val VERSION = 1

    /** 分享出去的文件后缀。系统文件管理器里一眼能认出这是「一局」而不是「一份文档」。 */
    const val EXTENSION = "wwg"

    /** 分享时用的 MIME。写 octet-stream 而不是 json：多数接收方不认自定义后缀 + json 的组合。 */
    const val MIME = "application/octet-stream"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /**
     * 文件里除了局面，还有「这是哪一局、什么时候开的」——列表排序和续档都靠它。
     *
     * [customPrompt] 是那一局生效的玩家设定：复盘页会显示它，
     * 换台手机导回来之后不该平白少一块。旧文件没有这一段，为 null。
     */
    data class Game(
        val id: String,
        val createdAt: Long,
        val table: WerewolfTable,
        val customPrompt: String? = null,
    )

    /** 读文件的结果。失败必须带原因：用户手里只有一个文件，不知道该怪文件还是怪 App。 */
    sealed interface Read {
        data class Ok(val game: Game) : Read
        data class Rejected(val reason: String) : Read
    }

    fun encode(
        table: WerewolfTable,
        id: String,
        createdAt: Long,
        customPrompt: String? = null,
    ): String =
        json.encodeToString(
            GameFileDto(
                format = FORMAT,
                fileVersion = VERSION,
                gameId = id,
                createdAt = createdAt,
                table = table.toSnapshot(customPrompt),
            )
        )

    /**
     * 读一个文件的内容。
     *
     * 除了标准信封，还**接受裸快照**：早期把快照直接贴进聊天框传给别人的玩家，
     * 手里就是一段 `{"version":10,...}`。认出来就收，认不出再说「不是对局文件」——
     * 拒绝一个内容完全正确的文件，比多写十几行兼容代码更让人难受。
     */
    fun read(text: String): Read {
        val body = text.trim().removePrefix("\uFEFF").trim()
        if (body.isEmpty()) return Read.Rejected("这个文件是空的")
        if (body.length < MIN_LENGTH) return Read.Rejected("这个文件不像是对局文件（内容太短）")

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return Read.Rejected("这个文件不是对局文件（读不出 JSON）")
        val format = root.stringOrNull("format")

        // 没信封：可能是一份裸快照。判据是「有 snapshot 版本号和玩家列表」，
        // 而不是「有 version 字段」——别的应用也爱用 version。
        if (format == null) {
            if (root["players"] == null || root["version"] == null) {
                return Read.Rejected("这个文件不是对局文件")
            }
            val dto = runCatching { json.decodeFromString<TableSnapshot>(body) }.getOrNull()
                ?: return Read.Rejected("这个文件不是对局文件（内容不完整）")
            val table = restore(dto) ?: return Read.Rejected(futureOrBroken(dto))
            return Read.Ok(
                Game(
                    id = "",
                    createdAt = 0L,
                    table = table,
                    customPrompt = dto.customPrompt?.takeIf { it.isNotBlank() },
                )
            )
        }

        if (format != FORMAT) {
            return Read.Rejected("这不是 AIFriend 的狼人杀对局文件")
        }
        val fileVersion = root.intOrNull("fileVersion") ?: VERSION
        if (fileVersion > VERSION) {
            return Read.Rejected("文件来自更新版本的 App（v$fileVersion），升级之后再导入")
        }

        val dto = runCatching { json.decodeFromString<GameFileDto>(body) }.getOrNull()
            ?: return Read.Rejected("对局文件内容不完整，可能传坏了")
        val table = restore(dto.table) ?: return Read.Rejected(futureOrBroken(dto.table))

        return Read.Ok(
            Game(
                // 早期版本可能没写 gameId：空的交给上层重新生成，不能拿空串当 id 落库
                id = dto.gameId.takeIf { it.isNotBlank() } ?: "",
                createdAt = dto.createdAt.coerceAtLeast(0L),
                table = table,
                customPrompt = dto.table.customPrompt?.takeIf { it.isNotBlank() },
            )
        )
    }

    private fun restore(dto: TableSnapshot): WerewolfTable? = WerewolfSnapshot.decode(dto)

    /** 同一份文件，两种坏法要对用户说两句话：一个是「去升级」，一个是「文件坏了」。 */
    private fun futureOrBroken(dto: TableSnapshot): String =
        if (WerewolfSnapshot.isFromFuture(dto)) {
            "这份存档来自更新版本的 App（存档 v${dto.version}），升级之后再导入"
        } else {
            "对局文件内容不完整或已损坏"
        }

    /** 短到不可能是对局文件的内容，不值得先解析一遍 JSON。 */
    private const val MIN_LENGTH = 40

    private fun JsonObject.stringOrNull(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

    private fun JsonObject.intOrNull(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.content?.toIntOrNull() }.getOrNull()
}

/**
 * 信封本身。`table` 用内部 DTO 而不是领域对象：领域类随便重构，
 * 这个文件的字段名不该跟着一起动（否则旧文件就再也读不出来了）。
 */
@Serializable
internal data class GameFileDto(
    val format: String = WerewolfGameFile.FORMAT,
    val fileVersion: Int = WerewolfGameFile.VERSION,
    val gameId: String = "",
    val createdAt: Long = 0L,
    val table: TableSnapshot,
)
