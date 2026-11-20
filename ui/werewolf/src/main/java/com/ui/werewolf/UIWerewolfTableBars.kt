package com.ui.werewolf

// 顶栏、自己那一行、状态条、结束条、复盘播放条。

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIRadiusControl
import com.ui.werewolf.data.UIWerewolfLive
import com.ui.werewolf.data.UIWerewolfReplay

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

/**
 * 自绘顶栏而不是用 `UITopBar`：全局那个组件写死了浅色文字，夜里会直接看不见。
 * 为了一个页面去改公共组件不划算，也不该让公共组件背上主题分支。
 */
@Composable
internal fun TableTopBar(
    title: String,
    subtitle: String?,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    spectating: Boolean,
    autoSpeak: Boolean,
    speechReady: Boolean,
    showThinking: Boolean,
    /** 传 null 就不画「思考」开关（观战、复盘演出里没有意义）。 */
    onToggleThinking: (() -> Unit)?,
    /** 传 null 就不画设置入口（复盘演出、已结束的局改了也没用）。 */
    onOpenSettings: (() -> Unit)? = null,
    onToggleSpeak: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = textColor
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = FsName,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (spectating) {
            Tag(text = "上帝视角", night = night, color = dimColor)
            Spacer(Modifier.width(12.dp))
        }
        // 思考开关：参与局默认关着，开了就等于读心。做成一个词而不是图标：
        // 「思考」两个字自己会解释，一个气泡图标得点一次才知道是什么。
        if (onToggleThinking != null) {
            Text(
                text = "思考",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (showThinking) SelfBlue.copy(alpha = if (night) 0.34f else 0.16f)
                        else if (night) NightStroke else UIFieldFill
                    )
                    .clickable(onClick = onToggleThinking)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = FsMeta,
                fontWeight = if (showThinking) FontWeight.SemiBold else FontWeight.Normal,
                color = if (showThinking) (if (night) Color(0xFFB9C8FF) else SelfBlue) else dimColor,
            )
            Spacer(Modifier.width(4.dp))
        }
        // 对局设置：思考时限、每个人的用时都在这里。
        // 用齿轮而不是塞进「思考」那个胶囊——那个是「看不看别人的心里话」，
        // 这个是「让它们想多久」，两件事放一个入口会互相误导。
        if (onOpenSettings != null) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "对局设置",
                    tint = textColor,
                )
            }
        }
        IconButton(
            onClick = onToggleSpeak,
            // 不可用时不禁用：点一下会重试绑定引擎（国产 ROM 的 init 会偶发失败，
            // 一旦失败不重建就永远灰着）。禁用反而断了唯一的恢复入口。
        ) {
            Icon(
                imageVector = if (autoSpeak) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = if (!speechReady) {
                    "语音暂不可用，点此重试"
                } else if (autoSpeak) {
                    "关闭自动朗读"
                } else {
                    "开启自动朗读"
                },
                // 不可用时用暗色，但仍可点——点它会重试，而不是没反应。
                tint = if (speechReady) textColor else dimColor,
            )
        }
    }
}

/**
 * 我是谁。
 *
 * 这一行必须常驻：狼人杀里「我几号、我什么身份」是读每一句话的前提，
 * 藏进抽屉或弹窗都会让人反复去翻。观战则换成一句「不参与，只看」。
 */
@Composable
internal fun SelfBar(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (live.spectating) {
            Tag(text = "观战", night = night, color = dimColor)
            // 人数按这一局的实际座位报：8 人、9 人、12 人都走这条分支，
            // 写死「八个人」的话 12 人局一开局就在说假话
            Text(
                text = if (live.seats.isEmpty()) {
                    "各说各话，你只负责看"
                } else {
                    "${live.seats.size} 个人各说各话，你只负责看"
                },
                fontSize = FsName,
                color = dimColor
            )
            return@Row
        }

        val seat = live.selfSeat
        if (seat == null) return@Row

        Text(
            text = "$seat 号",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SelfBlue)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        live.selfRoleLabel?.let { role ->
            Text(
                text = role,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
        live.selfFactionLabel?.let { faction ->
            Text(
                text = faction,
                fontSize = FsName,
                color = dimColor
            )
        }
        Spacer(Modifier.weight(1f))
        if (live.running) ThinkingDot(color = if (night) NightText else SelfBlue)
    }
}

@Composable
internal fun ReviewBar(
    surface: Color,
    textColor: Color,
    dimColor: Color,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "全部身份已公开 · 这一局已经存档",
            fontSize = FsMeta,
            color = dimColor
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIActionBrush)
                .clickable { onReview() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "看复盘",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/**
 * 结束后的底部条。
 *
 * 只放一个动作：看复盘。不做「再来一局」——刚被投出去的人未必想立刻重开，
 * 而重开的入口在配置页，从复盘页回去比在这里塞两个按钮清楚。
 */
/**
 * 复盘演出的播放条。
 *
 * 位置和输入区一样在最底下：演出一场可能几十条，手要一直按在同一个地方。
 * 上面那行字（第几条 · 倍速）和进度条是给「还要看多久」用的——
 * 没有进度条的话，一条一条往下演会让人不知道自己在哪。
 *
 * 不用图标而用文字：播放/暂停在圆形按钮里放一个三角和两条竖线很容易认错，
 * 而「播放 / 暂停」两个字不会有歧义；上一条、下一条同理。
 */
@Composable
internal fun ReplayBar(
    replay: UIWerewolfReplay,
    surface: Color,
    textColor: Color,
    dimColor: Color,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSpeed: () -> Unit,
    /** 切上帝视角。切了时间线重摊、从头再放——两条视角的消息条数不一样，续着放对不上。 */
    onGodView: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = replay.positionLabel,
                fontSize = FsMeta,
                color = dimColor,
                modifier = Modifier.weight(1f)
            )
            // 上帝视角开关：和复盘页「逐日对话」标题旁那个是同一样式。
            // 放播放条上而不是顶栏——退出去才能改视角的入口没人找得到。
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(
                        if (replay.godView) textColor.copy(alpha = 0.14f)
                        else dimColor.copy(alpha = 0.12f)
                    )
                    .clickable { onGodView(!replay.godView) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (replay.godView) "上帝视角·开" else "上帝视角·关",
                    fontSize = FsMeta,
                    color = if (replay.godView) textColor else dimColor
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .background(dimColor.copy(alpha = 0.12f))
                    .clickable { onSpeed() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${replay.speedLabel} 倍速",
                    fontSize = FsMeta,
                    color = textColor
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 进度条：一条能看出「演到哪」的线，比百分比数字更省事
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(dimColor.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(replay.progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(textColor.copy(alpha = 0.5f))
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReplayButton(
                label = "上一条",
                enabled = replay.canPrev,
                textColor = textColor,
                dimColor = dimColor,
                onClick = onPrev,
                modifier = Modifier.weight(1f)
            )
            ReplayButton(
                label = if (replay.playing) "暂停" else "播放",
                enabled = true,
                textColor = Color.White,
                dimColor = dimColor,
                primary = true,
                onClick = onToggle,
                modifier = Modifier.weight(1.2f)
            )
            ReplayButton(
                label = "下一条",
                enabled = replay.canNext,
                textColor = textColor,
                dimColor = dimColor,
                onClick = onNext,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun ReplayButton(
    label: String,
    enabled: Boolean,
    textColor: Color,
    dimColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .then(
                if (primary) {
                    Modifier.background(UIActionBrush)
                } else {
                    Modifier.background(dimColor.copy(alpha = if (enabled) 0.12f else 0.06f))
                }
            )
            // 不 disable 点击：禁用态只有「上一条在第一条」这种边界，
            // 点了没反应比按不动更容易让人以为界面坏了——统一在 VM 里夹到合法范围
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            color = if (primary) Color.White else textColor.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
