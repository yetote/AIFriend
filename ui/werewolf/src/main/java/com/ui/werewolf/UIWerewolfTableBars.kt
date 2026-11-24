package com.ui.werewolf

// 顶栏、自己那一行、状态条、结束条、复盘播放条。

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIShadowSoft
import com.ui.common.theme.uiHairlineBottom
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
 * 预言家的验人本。
 *
 * 夜里那条「查验 3 号：狼人」留在记录流里，可到了第三天记录流几十条，
 * 要回看第一晚验的人得一路往上翻。预言家白天报验人、对账，全靠这一行——
 * 它是这个身份最趁手的东西，所以常驻在身份条下面，不藏进任何菜单。
 *
 * 只有**预言家本人**看得到（映射层只在他自己是预言家时才给这份数据），
 * 所以这里不需要再判视角。
 */
@Composable
internal fun SeerCheckBar(
    live: UIWerewolfLive,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
) {
    val checks = live.selfChecks
    if (checks.isEmpty()) return
    val seerAccent = if (night) SeerTealNight else SeerTeal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "验人本",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(seerAccent.copy(alpha = if (night) 0.22f else 0.12f))
                .border(0.6.dp, seerAccent.copy(alpha = if (night) 0.42f else 0.28f), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 3.dp),
            fontSize = FsMeta,
            fontWeight = FontWeight.SemiBold,
            color = seerAccent,
        )
        checks.forEach { check ->
            val verdictColor = if (check.isWerewolf) VoteRed else seerAccent
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (night) NightControlFill else Color(0xCCFFFFFF))
                    .border(0.6.dp, verdictColor.copy(alpha = if (night) 0.45f else 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(verdictColor)
                )
                Text(
                    text = "${check.seat}号",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Text(
                    text = if (check.isWerewolf) "狼" else "好人",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.Medium,
                    color = verdictColor,
                )
            }
        }
    }
}

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
    /**
     * 传 null 就不画「自爆」。
     *
     * 只有「白天发言环节 + 活着 + 是狼」才传得进来（判定在状态机里），
     * 所以它出现就代表此刻真能爆。用红字而不是图标：这一步不可撤销，
     * 得让人在按下之前就先看到「这是危险动作」。
     */
    onExplode: (() -> Unit)? = null,
    /**
     * 传 null 就不画喇叭。
     * 正常对局不画——朗读开关在对局设置面板里，顶栏再放一个是重复入口；
     * 复盘演出没有设置面板，保留它作为唯一的朗读入口。
     */
    onToggleSpeak: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (night) Color(0x52090F1D) else Color(0x7AFFFFFF))
            .uiHairlineBottom(if (night) NightStroke else DayStroke)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = FsMeta,
                    color = dimColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (spectating) {
            Tag(text = "上帝视角", night = night, color = if (night) SelfBlueNight else SelfBlue)
            Spacer(Modifier.width(8.dp))
        }
        // 自爆：白天发言环节才出现，一旦出现就是真能爆。放在「思考」左边，
        // 因为它是这一屏唯一不可撤销的动作，不该和开关们混在一起看。
        if (onExplode != null) {
            val explodeColor = if (night) Color(0xFFFF8E8A) else UIFriendBarRed
            Text(
                text = "自爆",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(UIFriendBarRed.copy(alpha = if (night) 0.28f else 0.11f))
                    .border(0.8.dp, UIFriendBarRed.copy(alpha = if (night) 0.55f else 0.30f), RoundedCornerShape(50))
                    .clickable(onClick = onExplode)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = FsMeta,
                fontWeight = FontWeight.SemiBold,
                color = explodeColor,
            )
            Spacer(Modifier.width(6.dp))
        }
        // 思考开关：参与局默认关着，开了就等于读心。做成一个词而不是图标：
        // 「思考」两个字自己会解释，一个气泡图标得点一次才知道是什么。
        if (onToggleThinking != null) {
            val thinkingTint = if (night) SelfBlueNight else SelfBlue
            Text(
                text = "思考",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (showThinking) SelfBlue.copy(alpha = if (night) 0.32f else 0.15f)
                        else if (night) NightControlFill else DayControlFill
                    )
                    .border(
                        0.7.dp,
                        if (showThinking) thinkingTint.copy(alpha = 0.45f)
                        else if (night) NightControlBorder else DayControlBorder,
                        RoundedCornerShape(50),
                    )
                    .clickable(onClick = onToggleThinking)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = FsMeta,
                fontWeight = if (showThinking) FontWeight.SemiBold else FontWeight.Medium,
                color = if (showThinking) thinkingTint else dimColor,
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
        if (onToggleSpeak != null) {
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
    if (!live.spectating && live.selfSeat == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (night) Color(0x7016223A) else Color(0xBAFFFFFF))
            .border(0.7.dp, if (night) NightStroke else DayStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (live.spectating) {
            Tag(text = "观战", night = night, color = if (night) SelfBlueNight else SelfBlue)
            // 人数按这一局的实际座位报：8 人、9 人、12 人都走这条分支，
            // 写死「八个人」的话 12 人局一开局就在说假话
            Text(
                text = if (live.seats.isEmpty()) {
                    "各说各话，你只负责看"
                } else {
                    "${live.seats.size} 个人各说各话，你只负责看"
                },
                fontSize = FsName,
                color = dimColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (live.running) ThinkingDot(color = if (night) SelfBlueNight else SelfBlue)
            return@Row
        }

        val seat = live.selfSeat ?: return@Row
        val isWolfRole = live.selfFactionLabel?.contains("狼") == true

        Text(
            text = "$seat 号",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(UIActionBrush)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        live.selfRoleLabel?.let { role ->
            Text(
                text = role,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
        live.selfFactionLabel?.let { faction ->
            val factionColor = if (isWolfRole) FateRed else if (night) SeerTealNight else SeerTeal
            Text(
                text = faction,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(factionColor.copy(alpha = if (night) 0.22f else 0.11f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                fontSize = FsMeta,
                fontWeight = FontWeight.Medium,
                color = factionColor,
            )
        }
        Spacer(Modifier.weight(1f))
        if (live.running) ThinkingDot(color = if (night) SelfBlueNight else SelfBlue)
    }
}

@Composable
internal fun ReviewBar(
    surface: Color,
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockShape = RoundedCornerShape(topStart = UIRadiusCard, topEnd = UIRadiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = dockShape,
                clip = false,
                ambientColor = UIShadowSoft,
                spotColor = UIShadowSoft,
            )
            .clip(dockShape)
            .background(surface)
            .border(0.8.dp, if (night) NightStroke else DayStroke, dockShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "全部身份已公开 · 这一局已经存档",
            fontSize = FsMeta,
            color = dimColor
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(UIRadiusControl),
                    clip = false,
                    ambientColor = UIBlobBlue,
                    spotColor = UIBlobBlue,
                )
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(UIActionBrush)
                .clickable { onReview() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "看复盘",
                fontSize = 15.5.sp,
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
    night: Boolean,
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
    val dockShape = RoundedCornerShape(topStart = UIRadiusCard, topEnd = UIRadiusCard)
    val ctrlFill = if (night) NightControlFill else DayControlFill
    val ctrlBorder = if (night) NightControlBorder else DayControlBorder
    val accent = if (night) SelfBlueNight else SelfBlue

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = dockShape,
                clip = false,
                ambientColor = UIShadowSoft,
                spotColor = UIShadowSoft,
            )
            .clip(dockShape)
            .background(surface)
            .border(0.8.dp, if (night) NightStroke else DayStroke, dockShape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = replay.positionLabel,
                fontSize = FsMeta,
                fontWeight = FontWeight.Medium,
                color = dimColor,
                modifier = Modifier.weight(1f)
            )
            // 上帝视角开关：和复盘页「逐日对话」标题旁那个是同一样式。
            // 放播放条上而不是顶栏——退出去才能改视角的入口没人找得到。
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (replay.godView) accent.copy(alpha = if (night) 0.25f else 0.14f)
                        else ctrlFill
                    )
                    .border(
                        0.7.dp,
                        if (replay.godView) accent.copy(alpha = 0.45f) else ctrlBorder,
                        RoundedCornerShape(50),
                    )
                    .clickable { onGodView(!replay.godView) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (replay.godView) "上帝视角·开" else "上帝视角·关",
                    fontSize = FsMeta,
                    fontWeight = if (replay.godView) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (replay.godView) accent else dimColor
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ctrlFill)
                    .border(0.7.dp, ctrlBorder, RoundedCornerShape(50))
                    .clickable { onSpeed() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${replay.speedLabel} 倍速",
                    fontSize = FsMeta,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // 进度条：一条能看出「演到哪」的线，比百分比数字更省事
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(if (night) Color(0x26FFFFFF) else Color(0x181B2B45))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(replay.progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(UIActionBrush)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReplayButton(
                label = "上一条",
                enabled = replay.canPrev,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                onClick = onPrev,
                modifier = Modifier.weight(1f)
            )
            ReplayButton(
                label = if (replay.playing) "暂停" else "播放",
                enabled = true,
                night = night,
                textColor = Color.White,
                dimColor = dimColor,
                primary = true,
                onClick = onToggle,
                modifier = Modifier.weight(1.25f)
            )
            ReplayButton(
                label = "下一条",
                enabled = replay.canNext,
                night = night,
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
    night: Boolean,
    textColor: Color,
    dimColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(UIActionBrush)
                } else {
                    val fill = if (night) NightControlFill else DayControlFill
                    val border = if (night) NightControlBorder else DayControlBorder
                    Modifier
                        .background(if (enabled) fill else fill.copy(alpha = fill.alpha * 0.5f))
                        .border(0.7.dp, border, shape)
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
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            color = if (primary) Color.White else textColor.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}
