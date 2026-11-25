package com.ui.werewolf

// 牌桌共用的字号、配色与气泡尺寸：暖羊皮白昼 × 烛影曜石黑夜，与全站宣纸朱砂视觉统一。

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

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

/** 身份/死因徽标。 */
internal val FsBadge = 9.5.sp

/** 座位卡最底下的模型名。 */
internal val FsModel = 8.sp

internal val NightText = Color(0xFFF3ECE2)

internal val NightDim = Color(0xFFB3A595)

internal val NightStroke = Color(0x28F2E6D8)

internal val DayStroke = Color(0x1E241C14)

internal val DaySurface = Color(0xFFFFFDF9)

internal val NightSurface = Color(0xFF1A1513)

/** 悬浮顶栏 / 底部 Dock 用的暖雾磨砂底，让昼夜插画隐约透出但不抢字。 */
internal val DayGlassSurface = Color(0xF2FAF6EE)

internal val NightGlassSurface = Color(0xED171311)

/** 昼夜输入框与次级按钮填底：夜里用暖白微透，白天用浅暖砂。 */
internal val DayControlFill = Color(0x11241C14)

internal val NightControlFill = Color(0x1AF5E8D8)

internal val DayControlBorder = Color(0x1C241C14)

internal val NightControlBorder = Color(0x2BF5E8D8)

/** 自己与好人阵营蓝染强调色：与狼人赤红、警徽古铜金清晰拉开。 */
internal val SelfBlue = Color(0xFF2F5DA8)

internal val SelfBlueNight = Color(0xFF8AB4F8)

/** 出局与狼队赤印红。颜色 + 字双编码，色弱也分得清「刀」和「票」。 */
internal val FateRed = Color(0xFFC83C36)

internal val VoteRed = Color(0xFFD1483E)

/** 警徽古铜金。 */
internal val SheriffGold = Color(0xFFD0982E)

/** 女巫解药（松石绿）与毒药（绛紫）语义色。 */
internal val HealGreen = Color(0xFF1F8763)

internal val PoisonPurple = Color(0xFF8C4FD6)

/** 查验结论的松石青绿。 */
internal val SeerTeal = Color(0xFF1E7E6C)

internal val SeerTealNight = Color(0xFF60D6BE)
