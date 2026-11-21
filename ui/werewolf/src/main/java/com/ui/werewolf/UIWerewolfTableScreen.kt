package com.ui.werewolf

// 牌桌骨架：昼夜背景 + 顶栏 + 座位环 + 气泡流 + 底部操作区。其余部件在同名 Table* 文件里。

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIRadiusControl
import com.ui.werewolf.data.UIWerewolfLive
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay

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
 * 牌桌页。
 *
 * 一屏之内要同时交代四件事，优先级从上到下：
 * 我是谁（顶栏下一行）→ 场上有谁（左右两列）→ 刚刚发生了什么（中间对话）→ 现在要我做什么（底部）。
 * 没有 tab、没有二级页：狼人杀的信息是连续的，切走再切回来就读不懂局势了。
 *
 * 座位从环形改成左右两列：环形在 10 人以上会缩成一圈看不清的小头像，中央只剩一条窄缝，
 * 而左右两侧的空白完全用不上。竖排两列把宽度用满，头像和名字都能大一号，
 * 中间那条留给对话——真正需要读的东西。
 */
@Composable
fun UIWerewolfTableScreen(
    live: UIWerewolfLive,
    onExit: () -> Unit,
    onSpeech: (String) -> Unit = {},
    onVote: (Int?) -> Unit = {},
    onSeat: (Int) -> Unit = {},
    onWitch: (heal: Boolean, poison: Int?) -> Unit = { _, _ -> },
    onDirection: (clockwise: Boolean) -> Unit = {},
    onReview: (() -> Unit)? = null,
    onToggleSpeak: () -> Unit = {},
    /**
     * 切换「思考气泡」。
     *
     * 传 null 就不画这个开关：观战（本来就能看）和复盘演出（放的是已经结束的对局）不需要。
     */
    onToggleThinking: (() -> Unit)? = null,
    /** 打开对局设置（思考时限、每人的用时）。传 null 就不给入口。 */
    onOpenSettings: (() -> Unit)? = null,
    /** 长按思考气泡：本局隐藏这个座位的思考气泡。不传就不响应长按。 */
    onHideThinkingBubble: ((seat: Int) -> Unit)? = null,
    /** 复盘演出：播放 / 暂停。 */
    onReplayToggle: () -> Unit = {},
    onReplayPrev: () -> Unit = {},
    onReplayNext: () -> Unit = {},
    /** 循环切换 1x / 1.5x / 2x。 */
    onReplaySpeed: () -> Unit = {},
    /** 演出页里切上帝视角：时间线重摊、从头再放。 */
    onReplayGodView: (Boolean) -> Unit = {},
) {
    val replay = live.replay
    val night = live.night && !live.finished
    val textColor by animateColorAsState(
        targetValue = if (night) NightText else UIFriendBarTitleColor,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "text"
    )
    val dimColor by animateColorAsState(
        targetValue = if (night) NightDim else UIFriendBarItemSubtitleColor,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "dim"
    )
    val surface by animateColorAsState(
        targetValue = if (night) NightSurface else DaySurface,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "surface"
    )

    // 背景整体按昼夜插值：白天图层乘 dayness、夜里图层乘 (1-dayness)。
    // 用的是和文字、卡片同一段时长，天一黑是整片一起黑，不会出现
    // 「字已经白了、天还亮着」那种半截状态。
    val dayness by animateFloatAsState(
        targetValue = if (night) 0f else 1f,
        animationSpec = tween(DAY_NIGHT_MS / 2),
        label = "dayness"
    )

    // 座位头像在窗口里的位置。思考气泡要挂在头像头顶，得知道它在哪。
    val seatAnchors = remember { mutableStateMapOf<Int, Offset>() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景铺满整屏（含状态栏后面），内容再按安全区往里缩
        TableBackdrop(dayness = dayness)

        Column(modifier = Modifier.fillMaxSize().pageInsetsTop()) {
            TableTopBar(
                // 标题报「在哪一天」，副标题报「这一步走到哪」——同一件事只在一处说。
                // 原来底下还有一条状态条也写着「第 N 天 · 某阶段 · 等 N 号」，
                // 三行说同一句话，白占了对话流的地。
                //
                // 演出时标题换成「复盘演出」：播放条上有播放/暂停，很容易被当成
                // 「这一局还在打」。写清楚是回放，用户才不会去等一个不会来的回合。
                title = when {
                    replay != null -> "复盘演出"
                    // 结束时胜负最重要：原来它挤在底下那条状态条里，现在直接当标题
                    live.finished -> live.winnerLabel ?: "对局结束"
                    night -> "第 ${live.day} 夜"
                    else -> "第 ${live.day} 天"
                },
                subtitle = when {
                    replay != null -> "第 ${live.day} ${if (night) "夜" else "天"} · ${live.phaseLabel}"
                    live.finished -> null
                    // 进度还没轮到（准备中、或是舞台还停在上一段朗读里）就只报阶段，
                    // 不编一个「0/8」出来
                    else -> live.stageLabel ?: live.phaseLabel
                },
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                spectating = live.spectating,
                autoSpeak = live.autoSpeak,
                speechReady = live.speechReady,
                showThinking = live.showThinking,
                // 观战和演出都不给这个开关：观战本来就能看思考（上帝视角），
                // 演出放的是已经打完的局，开着它只会让人以为还能操作什么
                onToggleThinking = onToggleThinking
                    ?.takeIf { !live.spectating && replay == null && !live.finished },
                // 观战也允许调时限：等多久是所有人的事，和看不看思考无关。
                // 但演出和已结束的局不给：改一个不会再跑的档位只会让人以为能重打
                onOpenSettings = onOpenSettings?.takeIf { replay == null && !live.finished },
                onToggleSpeak = onToggleSpeak,
                onBack = onExit
            )

            SelfBar(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor
            )

            // 开着思考就是作弊：这一条必须一直挂在桌上，不能只在点开那一下闪一句。
            // 人一旦看惯了别人的心里话，会忘记自己手里的信息比 AI 还多。
            if (live.showThinking && !live.spectating && live.running) {
                CheatStrip(night = night)
            }

            if (live.error != null) {
                ErrorStrip(message = live.error, night = night)
            }

            // 超时提示只停留几秒：它不是状态，是「刚才发生了什么」。
            // 挂成常驻会让牌桌越来越挤，而真正要长期看的是设置面板里那份账本。
            var timeoutShown by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(live.timeoutAt) {
                val notice = live.timeoutNotice
                if (notice != null) {
                    timeoutShown = notice
                    delay(4600)
                    timeoutShown = null
                }
            }
            timeoutShown?.let { notice ->
                TimeoutStrip(text = notice, night = night)
            }

            // 左右两列玩家夹住中间的对话流。中间拿 weight，屏幕再窄也优先保住说话的地方——
            // 身份和出局状态挤一挤还能读，话被裁掉就完全看不懂局势了。
            SeatColumns(
                live = live,
                night = night,
                textColor = textColor,
                dimColor = dimColor,
                surface = surface,
                anchors = seatAnchors,
                modifier = Modifier.weight(1f)
            )

            val turn = live.turn
            when {
                // 演出：底下是播放条。这一条必须排在 finished 之前——演出时
                // `finished` 是 false，但哪怕将来变了，也不该把「看复盘」按钮顶上来。
                replay != null -> ReplayBar(
                    replay = replay,
                    surface = surface,
                    textColor = textColor,
                    dimColor = dimColor,
                    onToggle = onReplayToggle,
                    onPrev = onReplayPrev,
                    onNext = onReplayNext,
                    onSpeed = onReplaySpeed,
                    onGodView = onReplayGodView,
                    modifier = Modifier.pageInsetsBottom()
                )

                // 结束了就只留一个出口。原本这里是输入区，输入区已经没有任何意义，
                // 但空着会让人以为界面坏了——空位必须被「下一步做什么」填上。
                live.finished && onReview != null -> ReviewBar(
                    surface = surface,
                    textColor = textColor,
                    dimColor = dimColor,
                    onReview = onReview,
                    modifier = Modifier.pageInsetsBottom()
                )

                turn != null -> TurnPanel(
                    turn = turn,
                    night = night,
                    textColor = textColor,
                    dimColor = dimColor,
                    surface = surface,
                    onSpeech = onSpeech,
                    onVote = onVote,
                    onSeat = onSeat,
                    onWitch = onWitch,
                    onDirection = onDirection,
                    modifier = Modifier.pageInsetsBottom()
                )

                else -> {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.pageInsetsBottom())
                }
            }
        }

        // 思考气泡浮在最上层：它要盖在座位和对话流上面，
        // 画在别处会被对话流压住。
        val stream = live.streaming
        val anchor = stream?.let { seatAnchors[it.seat] }
        if (stream != null && anchor != null && stream.reasoning.isNotBlank()) {
            ThinkingBubble(
                reasoning = stream.reasoning,
                anchor = anchor,
                night = night,
                seat = stream.seat,
                // 时限口径：气泡里是还没说出口的部分，按思考时限掐；
                // 正文开始进发言列表后这段算发言，时钟换成发言时限（秒表重新起算）。
                // 决策步全程没有正文，永远停在思考时限上。
                speechStarted = stream.text.isNotEmpty(),
                limitSeconds = if (stream.text.isEmpty()) {
                    live.thinkingDecisionSeconds
                } else {
                    live.thinkingSpeechSeconds
                },
                onHide = onHideThinkingBubble?.let { callback -> { callback(stream.seat) } },
            )
        }

        // 过场画在最上层，但不吃触摸：1.4 秒里用户可能正要点「发言」，
        // 拦一下只会让人觉得界面卡了
        DayNightTransition(
            night = live.night,
            finished = live.finished
        )
    }
}

// ---------------------------------------------------------------------------
// 顶栏与身份
// ---------------------------------------------------------------------------

/** 出错条：局面还在，只是这一步没走通。不要做成弹窗，弹窗会挡住牌桌。 */
@Composable
internal fun ErrorStrip(message: String, night: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .background(
                UIFriendBarRed.copy(alpha = if (night) 0.24f else 0.08f),
                RoundedCornerShape(UIRadiusControl)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = "$message\n这一局的进度已经存档，下次进来可以接着打。",
            fontSize = FsName,
            lineHeight = 19.sp,
            color = if (night) Color(0xFFFFB4B0) else UIFriendBarRed
        )
    }
}

/**
 * 「某一步超时了」的短提示。
 *
 * 用红色系但比 [ErrorStrip] 淡：这不是错误，牌局在正常往前走，
 * 只是有一位没在时限内答上来。写成红色报错会让人以为整局出了问题。
 */
@Composable
private fun TimeoutStrip(text: String, night: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp)
            .background(
                UIFriendBarRed.copy(alpha = if (night) 0.20f else 0.07f),
                RoundedCornerShape(UIRadiusControl)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = if (night) Color(0xFFFFB4B0) else UIFriendBarRed
        )
    }
}

/**
 * 「你在读心」提示条。
 *
 * 顶栏那个「思考」一开，桌上就会浮出 AI 的心里话。这件事必须**一直**挂在牌桌上，
 * 不能只在点开时闪一句提示：作弊是个持续状态，人会很快看惯，
 * 然后忘了自己手里的信息比牌桌上任何人都多。
 */
@Composable
private fun CheatStrip(night: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 6.dp)
            .background(
                SelfBlue.copy(alpha = if (night) 0.22f else 0.08f),
                RoundedCornerShape(UIRadiusControl)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = "思考气泡已打开：桌上浮的是 AI 的心里话，这等于读心，打出来的结果别当真。",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = if (night) Color(0xFFB9C8FF) else SelfBlue
        )
    }
}

// ---------------------------------------------------------------------------
// 昼夜过场
//
// 原来只是整页颜色渐变：上一秒还是浅蓝，一秒后全黑，中间的 650ms 里
// 界面既不像白天也不像夜晚，看着像卡了一下。加一段天体升落的过场，
// 变化就有了「原因」——太阳落下去、月亮升起来，而不是屏幕自己变黑。
//
// 长度定在 1.4 秒：短于这个数看不清月亮是月牙；长于这个数，
// 每轮都要等一次，十几轮下来非常烦。
// ---------------------------------------------------------------------------
