package com.ui.werewolf

// 牌桌共用的字号、配色与气泡尺寸。集中一处，改观感只动这个文件。

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 字号
//
// 这页是要连着盯十几分钟的：一屏里同时有人物、发言、倒计时和操作按钮，
// 以前为了塞下 12 人的座位表，正文压到 12sp、名字压到 9sp——字是塞下了，
// 但一屏字全是一样细的小灰块，读两轮眼睛就累。
//
// 所以这里整体往上抬一档：正文 14sp、名字 11sp、身份徽标 9.5sp，
// 座位列从 62dp 放宽到 72dp 给头像留位置，中间那列宁可一行少两个字。
// 唯一的硬约束还是 12 人局一列 6 个人（高度），所以头像只到 34dp。
// ---------------------------------------------------------------------------

/** 发言正文。整页最该被读的东西，字号也最大。 */
internal val FsSpeech = 14.sp

internal val FsSpeechLine = 21.sp

/** 人名、按钮、面板标题。 */
internal val FsName = 12.5.sp

internal val FsAction = 14.sp

/** 座位号、天数、辅助说明这类「看一眼就够」的字。 */
internal val FsMeta = 11.5.sp

/** 座位上那一行「3 阿明」，受列宽限制，只能比正文小一档。 */
internal val FsSeatName = 11.sp

/** 身份/死因徽标。压在名字下面，再小就认不出「刀」「票」两个字了。 */
internal val FsBadge = 9.5.sp

/**
 * 座位卡最底下的模型名。比徽标再小一档：它是排查「这个 AI 是谁」时才看的东西，
 * 不该抢走名字和身份的注意力；窄列里也只放得下这么多。
 */
internal val FsModel = 8.sp

internal val NightText = Color(0xFFE9EEF9)

internal val NightDim = Color(0xFF9BA8C2)

internal val NightStroke = Color(0x26FFFFFF)

internal val DayStroke = Color(0x161B2B45)

internal val DaySurface = Color(0xFFFDFDFF)

internal val NightSurface = Color(0xFF151E30)

/** 悬浮顶栏 / 底部 Dock 用的半透明玻璃底，让昼夜插画隐约透出但不抢字。 */
internal val DayGlassSurface = Color(0xEBFFFFFF)

internal val NightGlassSurface = Color(0xE6131C2E)

/** 昼夜输入框与次级按钮填底：夜里用浅白半透明，白天用极淡灰蓝。 */
internal val DayControlFill = Color(0x0F1B2B45)

internal val NightControlFill = Color(0x1CFFFFFF)

internal val DayControlBorder = Color(0x141B2B45)

internal val NightControlBorder = Color(0x29FFFFFF)

/** 自己：座位环上的描边、身份胶囊。 */
internal val SelfBlue = Color(0xFF2B6BFF)

internal val SelfBlueNight = Color(0xFF7FA8FF)

/** 出局：一律降饱和，再叠一个原因字。颜色 + 字双编码，色弱也分得清「刀」和「票」。 */
internal val FateRed = Color(0xFFD9534F)

internal val VoteRed = Color(0xFFE0564F)

/** 警徽的金色。和票数的红、自己的蓝都拉开，扫一眼不会认错。 */
internal val SheriffGold = Color(0xFFD4A236)

/** 女巫解药（翠绿）与毒药（绛紫）语义色。 */
internal val HealGreen = Color(0xFF1D9A72)

internal val PoisonPurple = Color(0xFF8C4BE8)

/**
 * 查验结论的青绿。和记票的青蓝（偏蓝）、投票的琥珀、密谈的紫都拉得开。
 *
 * 刻意不用红绿：狼人杀里「红 = 狼」已经是约定，气泡用红绿会让人
 * 把气泡颜色当成验人结论本身。
 */
internal val SeerTeal = Color(0xFF1E8C7F)

/**
 * 夜里的查验结论色。
 *
 * 白天那个青绿压到夜空的深底上就发闷，字要往上提一档才看得清——
 * 和查验结论气泡的夜间强调色是同一个值（`UIWerewolfBubbleKind.SEER_RESULT`）。
 */
internal val SeerTealNight = Color(0xFF5FD9C6)
