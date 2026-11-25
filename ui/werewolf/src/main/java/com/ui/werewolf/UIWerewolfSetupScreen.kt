package com.ui.werewolf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ui.common.UIAvatar
import com.ui.common.pageInsetsBottom
import com.ui.common.pageInsetsTop
import com.ui.common.theme.UIBlobBlue
import com.ui.common.theme.UIActionBrush
import com.ui.common.theme.UIAtelierInk
import com.ui.common.theme.UIAtelierSand
import com.ui.common.theme.UIFieldFill
import com.ui.common.theme.UIFriendBarCreateButtonText
import com.ui.common.theme.UIFriendBarItemSubtitleColor
import com.ui.common.theme.UIFriendBarRed
import com.ui.common.theme.UIFriendBarSubtitleColor
import com.ui.common.theme.UIFriendBarTabUnselectedBg
import com.ui.common.theme.UIFriendBarTabUnselectedText
import com.ui.common.theme.UIFriendBarTagTextBlue
import com.ui.common.theme.UIFriendBarTitleColor
import com.ui.common.theme.UIGlassStroke
import com.ui.common.theme.UIGlassSurface
import com.ui.common.theme.UIGlassSurfaceStrong
import com.ui.common.theme.UIGradientBackground
import com.ui.common.theme.UIRadiusCard
import com.ui.common.theme.UIRadiusControl
import com.ui.common.theme.UIRadiusTab
import com.ui.common.theme.UITopBar
import com.ui.common.theme.uiCard
import com.ui.common.theme.uiControl
import com.ui.werewolf.data.UI_PLAYER_STYLES
import com.ui.werewolf.data.UIWerewolfApiCheck
import com.ui.werewolf.data.UIWerewolfApiState
import com.ui.werewolf.data.UIWerewolfFriend
import com.ui.werewolf.data.UIWerewolfMode
import com.ui.werewolf.data.UIWerewolfPlayerStyle
import com.ui.werewolf.data.UIWerewolfPromptStyle
import com.ui.werewolf.data.UIWerewolfPreset
import com.ui.werewolf.data.UIWerewolfRoleOption
import com.ui.werewolf.data.UIWerewolfSeat
import com.ui.werewolf.data.UIWerewolfSpeechLength
import com.ui.werewolf.data.UIWerewolfTurnLimit

/**
 * 连通标记用的绿。
 *
 * 单独定一个而不是复用主题色：主题里没有「成功」这个语义色，
 * 而且这个绿只在检测结果上出现一次，放进公共主题反而会诱导别处乱用。
 */
private val ConnectedGreen = Color(0xFF1F7A5A)

/**
 * 开局配置页。
 *
 * 和「新建聊天室」的区别在于这里要交代的不是一个名字，而是**这一局有几个位置、还差几个人**。
 * 所以主视觉是座位条而不是表单：一眼看清「8 个位置填了 5 个」比读「已选 5 位」直观。
 */
@Composable
fun UIWerewolfSetupScreen(
    mode: UIWerewolfMode,
    presets: List<UIWerewolfPreset>,
    presetKey: String,
    friends: List<UIWerewolfFriend>,
    selectedIds: Set<String>,
    seats: List<UIWerewolfSeat>,
    error: String?,
    apiChecks: Map<String, UIWerewolfApiCheck>,
    checking: Boolean,
    /** 这一局每步最多等多久。 */
    turnLimit: UIWerewolfTurnLimit,
    onTurnLimitChange: (UIWerewolfTurnLimit) -> Unit,
    /** 提示词风格：引导式给内置打法，自由式让模型自己想。 */
    promptStyle: UIWerewolfPromptStyle,
    onPromptStyleChange: (UIWerewolfPromptStyle) -> Unit,
    /** 要不要给每个 AI 注入牌手风格画像（决策气质 + 读人习惯）。 */
    playerStyleOn: Boolean,
    onPlayerStyleOnChange: (Boolean) -> Unit,
    /** 好友 id → 手动指定的风格 id；没指定的自动分配。 */
    styleByFriend: Map<String, String>,
    onAssignStyle: (friendId: String, styleId: String?) -> Unit,
    /** 这一局发言的目标长度。 */
    speechLength: UIWerewolfSpeechLength,
    onSpeechLengthChange: (UIWerewolfSpeechLength) -> Unit,
    /** 参与局里「我」的身份 key（和 [roleOptions] 同构，含头狼伪 key）；null = 随机。 */
    myRole: String?,
    onMyRoleChange: (String?) -> Unit,
    /** 纯观战局的导演模式：指定每个 AI 拿什么身份，没指定的随机。 */
    assignRoles: Boolean,
    onAssignRolesChange: (Boolean) -> Unit,
    /** 当前板子的身份名额。 */
    roleOptions: List<UIWerewolfRoleOption>,
    /** 好友 id → 已指定的身份 key；没指定的随机补。 */
    roleByFriend: Map<String, String>,
    /** 给某个好友指定身份；roleKey 传 null 表示改回随机。 */
    onAssignRole: (friendId: String, roleKey: String?) -> Unit,
    /** 要不要把 AI 的发言念出来。 */
    speechOn: Boolean,
    /**
     * 语音引擎此刻能不能出声。
     *
     * 不能出声时开关照样可点——点「开」会在 ViewModel 里顺手重建一次引擎，
     * 那正是国产 ROM 上 init 偶发失败后唯一的自愈入口。
     */
    speechReady: Boolean,
    onSpeechChange: (Boolean) -> Unit,
    onModeChange: (UIWerewolfMode) -> Unit,
    onPresetChange: (String) -> Unit,
    onToggleFriend: (String) -> Unit,
    onClearError: () -> Unit,
    onCheckApi: () -> Unit,
    /**
     * 进到「AI 设置」页、或改了打法之后自动验一遍连接。
     *
     * 和 [onCheckApi] 分开：这个不强制，测过且在有效期内的座位会跳过；
     * 但换了打法（尤其切到搜索式）前提就变了，必须重验。
     */
    onAutoCheck: () -> Unit,
    /** 去微调这一局的提示词。 */
    onOpenPromptSettings: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStartAnyway: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    // 0 = 组局（选人搭骨架），1 = AI 设置（调发挥）。旋转屏幕后仍停在这一页。
    var setupTab by rememberSaveable { mutableStateOf(0) }
    // 牌手风格的手动指定列表默认收起。
    var styleListExpanded by rememberSaveable { mutableStateOf(false) }
    val visibleFriends = if (search.isBlank()) {
        friends
    } else {
        friends.filter { friend ->
            friend.name.contains(search, ignoreCase = true) ||
                friend.role.contains(search, ignoreCase = true) ||
                friend.model.contains(search, ignoreCase = true)
        }
    }

    // 需要几位好友：自己参与的局里有一个位置是自己的，不占好友名额。
    val preset = presets.firstOrNull { it.key == presetKey } ?: presets.firstOrNull()
    val needFriends = (preset?.playerCount ?: 0) - if (mode == UIWerewolfMode.PLAY) 1 else 0
    val missing = (needFriends - selectedIds.size).coerceAtLeast(0)
    val canStart = needFriends > 0 && missing == 0

    // 只在「已选且查过」的人里数：没选的人查不查无所谓，不该影响开局按钮
    val failedCount = selectedIds.count { apiChecks[it]?.state == UIWerewolfApiState.FAILED }
    val selectedCheckable = selectedIds.isNotEmpty() && !checking
    val searchStyle = promptStyle == UIWerewolfPromptStyle.SEARCH
    val okCount = selectedIds.count { apiChecks[it]?.state == UIWerewolfApiState.OK }
    // 「没有联网搜索能力」的座位单列：它不是 Key 的问题，重试多少次都没用
    val unsupportedCount = selectedIds.count { apiChecks[it]?.searchUnsupported == true }
    // 第一条失败原因：搜索式下多半就是「这家不支持联网」，
    // 直接摆在自检卡上，比让用户自己去座位列表里找那个红点快
    val firstFailure = selectedIds.firstNotNullOfOrNull { id ->
        apiChecks[id]
            ?.takeIf { it.state == UIWerewolfApiState.FAILED }
            ?.message
            ?.takeIf { it.isNotBlank() }
    }

    // 进「AI 设置」页、或改了打法，就按当前前提重验一遍。
    // 关键在「按当前前提」：搜索式和普通探活对同一个座位可能给出相反结论，
    // 不重验等于拿旧结论骗自己——那正是「开局后才发现不支持联网」的来路
    LaunchedEffect(setupTab, promptStyle) {
        if (setupTab == 1 && selectedIds.isNotEmpty()) onAutoCheck()
    }

    UIGradientBackground(modifier = Modifier.pageInsetsTop()) {
        Column(modifier = Modifier.fillMaxSize()) {
            UITopBar(title = "新开一局", onBack = onBack) {
                Text(
                    text = when {
                        !canStart -> "差 $missing 位"
                        failedCount > 0 -> "$failedCount 个要处理"
                        // 第一页只是搭好了局，开局要到第二页配完打法、验过连接
                        setupTab == 0 -> "人数齐了"
                        else -> "可以开局"
                    },
                    modifier = Modifier.padding(end = 12.dp),
                    fontSize = 13.sp,
                    color = if (failedCount > 0) {
                        UIFriendBarRed
                    } else if (canStart) {
                        UIFriendBarTagTextBlue
                    } else {
                        UIFriendBarItemSubtitleColor
                    }
                )
            }

            SetupTabRow(
                selected = setupTab,
                onSelect = { setupTab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            Text(
                text = if (setupTab == 0) {
                    "谁上桌、坐哪、用什么板子——先把这一局的骨架搭好"
                } else {
                    "想多久、说多少、要不要联网——调 AI 的发挥，顺手验一遍连接"
                },
                fontSize = 11.5.sp,
                color = UIFriendBarItemSubtitleColor,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (error != null) {
                    ErrorBar(text = error, onDismiss = onClearError)
                }
                if (setupTab == 0) {
                    SectionTitle(
                        title = "怎么玩",
                        subtitle = "自己占一个座位，还是纯粹看着 AI 自己打"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UIWerewolfMode.entries.forEach { item ->
                            ModeCard(
                                mode = item,
                                selected = item == mode,
                                modifier = Modifier.weight(1f),
                                onClick = { onModeChange(item) }
                            )
                        }
                    }
                    SectionTitle(
                        title = "板子",
                        subtitle = "身份构成固定，开局时随机发牌。挑一个：人数决定要凑几个好友"
                    )
                    PresetGrid(
                        presets = presets,
                        presetKey = presetKey,
                        onPresetChange = onPresetChange
                    )
                    // 座位条：这是整页的主视觉，把「还差几位」变成几个空位
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "本局 ${seats.size} 个位置",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UIFriendBarTitleColor
                            )
                            Text(
                                text = "座位号开局时随机",
                                fontSize = 11.sp,
                                color = UIFriendBarItemSubtitleColor
                            )
                        }
                        SeatStrip(seats = seats)
                    }
                    // 参与局先定「我是谁」，放在选人之前：先定自己这张牌，再挑对手。
                    // 只改自己那一席，其他座位照常随机——想练狼不必为此开整页导演模式。
                    if (mode == UIWerewolfMode.PLAY) {
                        SectionTitle(
                            title = "我的身份",
                            subtitle = "只影响你自己这一席，其他座位照常随机发"
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                                .padding(vertical = 4.dp)
                        ) {
                            RoleAssignRow(
                                friendName = "你",
                                assignedKey = myRole,
                                options = roleOptions,
                                onPick = onMyRoleChange,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        SectionTitle(
                            modifier = Modifier.weight(1f),
                            title = "选人",
                            subtitle = if (needFriends > 0) {
                                "已选 ${selectedIds.size} / $needFriends 位"
                            } else {
                                "还没有可选的板子"
                            }
                        )
                        // 检测放在这一行的右边而不是另起一块：它测的就是「上面选的这批人」，
                        // 隔开一段距离会让人以为是个全局设置
                        CheckApiButton(
                            checking = checking,
                            enabled = selectedCheckable,
                            hasFailure = failedCount > 0,
                            onClick = onCheckApi
                        )
                    }

                    SearchField(
                        value = search,
                        onValueChange = { search = it }
                    )

                    when {
                        friends.isEmpty() -> Text(
                            text = "还没有 AI 好友。先去首页创建一个，再来开局。",
                            fontSize = 13.sp,
                            color = UIFriendBarSubtitleColor
                        )

                        visibleFriends.isEmpty() -> Text(
                            text = "没有匹配的好友",
                            fontSize = 13.sp,
                            color = UIFriendBarSubtitleColor
                        )
                    }

                    if (visibleFriends.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                                .padding(vertical = 4.dp)
                        ) {
                            visibleFriends.forEach { friend ->
                                val checked = friend.id in selectedIds
                                // 选满之后仍允许取消，否则想换人得先取消一个再找
                                val enabled = checked || selectedIds.size < needFriends
                                FriendRow(
                                    friend = friend,
                                    checked = checked,
                                    enabled = enabled,
                                    // 所有好友都展示已有探活结论（含首页刚探完的共享缓存），
                                    // 选人前一眼就能看清哪些活着、哪些挂了，不必盲选后再撞墙
                                    check = apiChecks[friend.id],
                                    // 联网能力是静态的，选不选他都得说：这行标记不随检测结果消失，
                                    // 所以用户不必等自检跑完才知道自家模型搜不了
                                    showNoSearch = searchStyle && !friend.supportsWebSearch,
                                    onClick = { onToggleFriend(friend.id) }
                                )
                            }
                        }
                    }
                    // 指定身份只在纯观战局开放：参与的局自己挑身份等于作弊。
                    // 放在选人列表后面：先确定谁上桌，再给上桌的人派身份
                    if (mode == UIWerewolfMode.SPECTATE && selectedIds.isNotEmpty()) {
                        SectionTitle(
                            title = "指定身份",
                            subtitle = "给每个座位指定身份，看一场按你剧本演的局；没指定的随机发"
                        )
                        SetupSwitchRow(
                            title = "导演模式",
                            subtitle = if (assignRoles) {
                                "点每个名字旁的身份改派，改回随机也行；座位号开局仍然随机"
                            } else {
                                "打开后可以指定谁当狼、谁当预言家，其余随机"
                            },
                            checked = assignRoles,
                            onCheckedChange = onAssignRolesChange,
                        )
                        if (assignRoles) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                                    .padding(vertical = 4.dp)
                            ) {
                                friends.filter { it.id in selectedIds }.forEach { friend ->
                                    RoleAssignRow(
                                        friendName = friend.name,
                                        assignedKey = roleByFriend[friend.id],
                                        options = roleOptions,
                                        onPick = { key -> onAssignRole(friend.id, key) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (setupTab == 1) {
                    // 自检放在这一页最前面：打法一改，检查的**前提**就变了。
                    // 搜索式要的是「带着联网也能回话」，和普通探活不是同一道题，
                    // 所以每次进这一页、每次换打法都重新验一遍
                    SectionTitle(
                        title = "连接自检",
                        subtitle = if (searchStyle) {
                            "搜索式要求每个座位真的能联网检索；连得上但不会查，同样算不合格"
                        } else {
                            "开局前确认每个人背后的模型能回话"
                        }
                    )
                    SetupCheckCard(
                        searchStyle = searchStyle,
                        checking = checking,
                        total = selectedIds.size,
                        okCount = okCount,
                        failedCount = failedCount,
                        unsupportedCount = unsupportedCount,
                        firstFailure = firstFailure,
                        enabled = selectedCheckable,
                        onCheck = onCheckApi,
                    )
                    // 提示词风格决定 AI「按什么打」：引导式有内置打法兜底，自由式把它放出去，
                    // 搜索式连规则细节都放出去（模型自己回忆或联网查，只留版型与胜负）。
                    // 放在开局而不是设置页，是因为这是「这一局想看什么」的取舍——
                    // 想看小模型也能像样对局就用引导式，想看强模型自己推理就用自由式。
                    SectionTitle(
                        title = "AI 打法",
                        subtitle = "引导式内置读人和战术；自由式只给身份与局势；搜索式连规则都不给，让模型联网自己查"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UIWerewolfPromptStyle.entries.forEach { style ->
                                SetupStyleChip(
                                    style = style,
                                    selected = style == promptStyle,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onPromptStyleChange(style) },
                                )
                            }
                        }
                        Text(
                            text = promptStyle.hint,
                            fontSize = 11.sp,
                            color = UIFriendBarItemSubtitleColor
                        )
                    }
                    // 牌手风格：给每个 AI 一份「在牌桌上是什么牌手」的画像。
                    // 和陪伴人设分工——陪伴人设管怎么说（猫娘、傲娇），这里管怎么想
                    // （多疑、强攻、盘逻辑）。实测只有陪伴人设时，AI 把脑力花在口头禅上，
                    // 满场都是记账式发言，几乎没人去想「对手在打什么算盘」。
                    SectionTitle(
                        title = "牌手风格",
                        subtitle = "给 AI 一个博弈的性格：它信什么、疑谁、怎么下判断"
                    )
                    SetupSwitchRow(
                        title = "启用牌手风格",
                        subtitle = "每个 AI 自动分到不同牌风，同桌才有几种打法碰撞；关掉则只用陪伴人设",
                        checked = playerStyleOn,
                        onCheckedChange = onPlayerStyleOnChange,
                    )
                if (playerStyleOn) {
                    // 手动指定默认折叠：一桌 8 个人就意味着 8 行「自动」，
                    // 全是默认值等于没信息，白白把这一页撑长。真要改的人点开就行。
                    val pinned = styleByFriend.count { it.value.isNotBlank() }
                    SetupFoldRow(
                        title = "手动指定",
                        subtitle = if (pinned > 0) {
                            "已指定 $pinned 位。点名字改风格，改回自动也行"
                        } else {
                            "默认自动分配，同桌尽量不重样。想固定某个人用某种牌风再点这里"
                        },
                        expanded = styleListExpanded,
                        onToggle = { styleListExpanded = !styleListExpanded },
                    )
                    if (styleListExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .uiCard(shape = RoundedCornerShape(UIRadiusCard))
                                .padding(vertical = 4.dp)
                        ) {
                            friends.filter { it.id in selectedIds }.forEach { friend ->
                                StyleAssignRow(
                                    friendName = friend.name,
                                    assignedId = styleByFriend[friend.id],
                                    onPick = { styleId -> onAssignStyle(friend.id, styleId) },
                                )
                            }
                        }
                    }
                }
                    SectionTitle(
                        title = "提示词微调",
                        subtitle = "嫌 AI 打得太套路？给这张板子写几句自己的要求"
                    )
                    PromptEntryCard(
                        onClick = onOpenPromptSettings
                    )
                    // 思考时限放在开局这里而不是藏进设置：它直接决定「这一局要打多久」，
                    // 而用户是带着「我等不等得起」这个问题来开局的
                    SectionTitle(
                        title = "思考时限",
                        subtitle = "每一步最多等多久。超时按兜底继续，并关掉那个座位本局的深度思考"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UIWerewolfTurnLimit.entries.forEach { limit ->
                                SetupLimitChip(
                                    limit = limit,
                                    selected = limit == turnLimit,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onTurnLimitChange(limit) },
                                )
                            }
                        }
                        Text(
                            text = turnLimit.consequence + "。开局后在牌桌上点右上角齿轮也能改。",
                            fontSize = 11.sp,
                            color = UIFriendBarItemSubtitleColor
                        )
                    }
                    // 发言长度决定「AI 说多少」：只影响观感不影响公平，放开局这里
                    // 和朗读、时限同一个理由——一局的体验要在开局前定下来
                    SectionTitle(
                        title = "发言长度",
                        subtitle = "AI 每句公开发言的目标字数。说太长的会被引擎在句末裁掉"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UIWerewolfSpeechLength.entries.forEach { length ->
                                SetupSpeechChip(
                                    length = length,
                                    selected = length == speechLength,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onSpeechLengthChange(length) },
                                )
                            }
                        }
                        Text(
                            text = speechLength.hint,
                            fontSize = 11.sp,
                            color = UIFriendBarItemSubtitleColor
                        )
                    }
                    // 朗读放在开局这里决定：一局可能打半小时，半夜躺在床上不想听、
                    // 通勤路上想听，都得在「开局之前」定下来。
                    // 进牌桌后想改，去齿轮里的对局设置——那里有同一份开关，不再顶栏放喇叭。
                    SectionTitle(
                        title = "朗读",
                        subtitle = "把 AI 的发言念出来。音色属于每个座位自己，不在这里配"
                    )
                    SetupSwitchRow(
                        title = "自动朗读",
                        subtitle = when {
                            !speechReady -> "语音引擎还没就绪。先打开，进牌桌后在设置面板里再拨一次即可重试"
                            speechOn -> "只念说出口的话，思考和内心活动不念"
                            else -> "关着也能随时在牌桌的对局设置里打开"
                        },
                        checked = speechOn,
                        onCheckedChange = onSpeechChange,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            StartBar(
                canStart = canStart,
                missing = missing,
                playMode = mode == UIWerewolfMode.PLAY,
                checking = checking,
                failedCount = failedCount,
                unsupportedCount = unsupportedCount,
                // 第一页只负责搭局，开局必须发生在第二页：打法是开局条件的一部分，
                // 而连接自检要按打法验——两件事不能分在两道门后面
                nextStep = setupTab == 0,
                onNext = { setupTab = 1 },
                onStart = onStart,
                onStartAnyway = onStartAnyway
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 各区块
// ---------------------------------------------------------------------------

/** 「去微调提示词」的入口卡。 */
@Composable
private fun PromptEntryCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIGlassSurfaceStrong)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "给这一局写点要求",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Text(
                text = "狼人更凶一点、预言家每天报两个验人…只影响 AI 怎么打",
                fontSize = 11.sp,
                color = UIFriendBarItemSubtitleColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = UIFriendBarTagTextBlue,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 一行带开关的设置项。
 *
 * 整行可点，而不是只有右边那个开关：Switch 的肉丸只有几十像素宽，
 * 手指落在文字上没反应的话，会让人以为这个设置改不了。
 */
@Composable
private fun SetupSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(shape = RoundedCornerShape(UIRadiusControl))
            .clickable { onCheckedChange(!checked) }
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = UIFriendBarTagTextBlue
            )
        )
    }
}

/**
 * 可展开/收起的一行。
 *
 * 用在「默认没什么要改、但偶尔要改」的设置上：牌手风格的手动指定、身份指派
 * 都是这种。平时收起只占一行，需要时点开才铺开内容。
 */
@Composable
private fun SetupFoldRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(shape = RoundedCornerShape(UIRadiusControl))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = UIFriendBarTitleColor
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = UIFriendBarItemSubtitleColor
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "收起" else "展开",
            tint = UIFriendBarItemSubtitleColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 开局页顶部的两页切换：组局 / AI 设置。
 *
 * 配置太多时把「怎么搭局」和「AI 怎么发挥」塞进同一个滚动里，用户得滑很久
 * 才能改一个开关。拆成两页后每页都短到一屏多，进页面先看组局，调 AI 再切过去。
 */
@Composable
private fun SetupTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf("组局", "AI 设置")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusTab))
            .background(UIAtelierSand)
            .border(1.dp, UIGlassStroke, RoundedCornerShape(UIRadiusTab))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(UIRadiusTab - 3.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(UIAtelierInk)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) UIGlassSurfaceStrong else UIFriendBarTabUnselectedText,
                    )
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = UIFriendBarTitleColor
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

/**
 * 「检测连接」按钮。
 *
 * 三种状态三套文案：没测过是「检测连接」，测出问题变「重试」，跑起来变「检测中」。
 * 一个永远写着「检测」的按钮，用户既不知道点下去会发生什么，也不知道点完没有。
 *
 * 颜色跟着状态走：有失败时整个按钮转红，用户在列表里一眼能找回这个入口。
 */
@Composable
private fun CheckApiButton(
    checking: Boolean,
    enabled: Boolean,
    hasFailure: Boolean,
    onClick: () -> Unit,
) {
    val color = when {
        checking -> UIFriendBarCreateButtonText
        hasFailure -> UIFriendBarRed
        enabled -> UIFriendBarCreateButtonText
        else -> UIFriendBarItemSubtitleColor
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = color.copy(alpha = if (enabled || checking) 0.4f else 0.2f),
                shape = RoundedCornerShape(50)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (checking) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
                color = color
            )
        }
        Text(
            text = when {
                checking -> "检测中"
                hasFailure -> "重试"
                else -> "检测连接"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * 「连接自检」卡。
 *
 * 和选人列表里那枚小按钮是同一件事，但这一页回答的是另一个问题：不是「这个人连不连得上」，
 * 而是「按这一档打法，它够不够用」。搜索式下这两者会分叉——模型连得上，
 * 但它的对话接口没有联网搜索，开局后才会发现「说的是自己查，其实一次都没查」。
 */
@Composable
private fun SetupCheckCard(
    searchStyle: Boolean,
    checking: Boolean,
    total: Int,
    okCount: Int,
    failedCount: Int,
    unsupportedCount: Int,
    firstFailure: String?,
    enabled: Boolean,
    onCheck: () -> Unit,
) {
    val statusText = when {
        checking -> if (searchStyle) "正在按搜索式检测…" else "正在检测…"
        failedCount > 0 -> "$failedCount 个座位过不了这一档"
        total == 0 -> "还没选人"
        okCount == 0 -> "还没检测"
        okCount >= total -> "全部连通"
        else -> "已连通 $okCount / $total"
    }
    val statusColor = when {
        failedCount > 0 -> UIFriendBarRed
        okCount > 0 && okCount >= total -> ConnectedGreen
        else -> UIFriendBarItemSubtitleColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .uiCard(shape = RoundedCornerShape(UIRadiusCard))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = statusText,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
            CheckApiButton(
                checking = checking,
                enabled = enabled,
                hasFailure = failedCount > 0,
                onClick = onCheck
            )
        }
        if (!firstFailure.isNullOrBlank()) {
            Text(
                text = "「$firstFailure」",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = UIFriendBarRed
            )
        }
        if (unsupportedCount > 0) {
            // 这一句是给「重试」按钮的反面教材：能力不够，重试一万次也还是不行。
            // 说清楚只能换厂商或者换个打法，用户才不会一直点重试
            Text(
                text = "$unsupportedCount 个座位没有联网搜索能力：换厂商，或者改用引导式/自由式",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = UIFriendBarRed
            )
        }
    }
}

@Composable
private fun ErrorBar(text: String, onDismiss: () -> Unit) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(UIFriendBarRed.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = UIFriendBarRed
        )
        Text(
            text = "知道了",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onDismiss() }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarRed
        )
    }
}

@Composable
private fun ModeCard(
    mode: UIWerewolfMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.08f) else UIGlassSurfaceStrong)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.45f) else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = mode.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) UIFriendBarCreateButtonText else UIFriendBarTitleColor
        )
        Text(
            text = mode.hint,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

/**
 * 板子网格。
 *
 * 六套板子竖着排要占两屏，会把「座位条」和「选人」挤到看不见的地方——而这两块
 * 才是开局前真正要盯的东西（还差几个人）。两列既能一屏放完，也仍然读得出人数递增的顺序。
 */
@Composable
private fun PresetGrid(
    presets: List<UIWerewolfPreset>,
    presetKey: String,
    onPresetChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    PresetCard(
                        preset = item,
                        selected = item.key == presetKey,
                        modifier = Modifier.weight(1f),
                        onClick = { onPresetChange(item.key) }
                    )
                }
                // 奇数个时补一个空权重，否则最后一张卡会拉满整行，看起来像另一类东西
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: UIWerewolfPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            // 固定高度而不是 wrap：同一行的两张卡要一样高，否则「12 人白痴局」那行
            // 会比「6 人快局」那行高一截，读起来像两种东西
            .height(76.dp)
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.07f) else UIGlassSurfaceStrong)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) UIFriendBarCreateButtonText.copy(alpha = 0.4f) else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = preset.title,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) UIFriendBarCreateButtonText else UIFriendBarTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(UIActionBrush)
                        } else {
                            Modifier.border(1.2.dp, UIGlassStroke, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
        Text(
            text = preset.composition,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = UIFriendBarItemSubtitleColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 座位条。
 *
 * 空位用虚线圆而不是浅灰实心圆：实心会被误读成「已有一个没头像的人」，
 * 虚线才有「这里还空着，等人来」的意思。
 *
 * 12 人局的圆要缩小：一行 12 个 30dp 的圆圈加上间距会超出屏宽，
 * 而这条是整页主视觉，挤掉一两个人比缩小几个像素糟糕得多。
 */
@Composable
private fun SeatStrip(seats: List<UIWerewolfSeat>) {
    val diameter = if (seats.size >= 11) 22.dp else if (seats.size >= 10) 25.dp else 30.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        seats.forEach { seat ->
            if (seat.name == null) {
                Box(
                    modifier = Modifier
                        .size(diameter)
                        .drawBehind {
                            drawCircle(
                                color = UIFriendBarItemSubtitleColor.copy(alpha = 0.35f),
                                radius = this.size.minDimension / 2f - 1.dp.toPx(),
                                style = Stroke(
                                    width = 1.2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                                    )
                                )
                            )
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(diameter)
                        .then(
                            if (seat.isSelf) {
                                Modifier.border(2.dp, UIFriendBarCreateButtonText, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    UIAvatar(
                        name = seat.name,
                        avatarPath = seat.avatarPath,
                        size = if (seat.isSelf) diameter - 4.dp else diameter,
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .uiControl(shape = RoundedCornerShape(50.dp))
            .background(UIFieldFill)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = "搜索昵称 / 角色 / 模型",
                fontSize = 13.sp,
                color = UIFriendBarItemSubtitleColor.copy(alpha = 0.7f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = UIFriendBarTitleColor
            ),
            cursorBrush = SolidColor(UIFriendBarCreateButtonText)
        )
    }
}

@Composable
private fun FriendRow(
    friend: UIWerewolfFriend,
    checked: Boolean,
    enabled: Boolean,
    check: UIWerewolfApiCheck?,
    /** 选搜索式时这家没有内置联网搜索，得当场说清楚。 */
    showNoSearch: Boolean,
    onClick: () -> Unit,
) {
    val active = enabled || checked
    val dim = if (active) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        UIAvatar(
            name = friend.name,
            avatarPath = friend.avatarPath,
            size = 40.dp,
            shape = CircleShape,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 名字和状态标签同一行：标签压在名字这一行，就不会被下面那行长的模型名挤走。
            // 名字 weight 占满、标签贴右，各行标签自然对齐成一条竖线，扫一眼就知道哪几个通。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friend.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) {
                        UIFriendBarTitleColor
                    } else {
                        UIFriendBarTitleColor.copy(alpha = 0.4f)
                    }
                )
                if (check != null && check.state != UIWerewolfApiState.IDLE) {
                    Spacer(Modifier.width(6.dp))
                    CheckTag(state = check.state, elapsedMs = check.elapsedMs)
                }
            }

            // 人设、模型各占一行。原来拼成「人设 · 模型」一行，模型名动辄二十多个字符，
            // 换行后把同一个 Row 里的状态标签压成「已/连通」两字竖排，读起来像乱码
            if (friend.role.isNotBlank()) {
                Text(
                    text = friend.role,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = UIFriendBarItemSubtitleColor.copy(alpha = dim)
                )
            }
            if (friend.model.isNotBlank()) {
                Text(
                    text = friend.model,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 再淡一档：模型名是排查问题时才看的东西，不该和人设抢注意力
                    color = UIFriendBarItemSubtitleColor.copy(alpha = 0.75f * dim)
                )
            }
            // 「这个模型搜不了」要说在这一行下面，而不是等到自检失败才冒出来：
            // 这是静态能力，不用花任何一次请求就能知道
            if (showNoSearch) {
                Text(
                    text = "没有联网搜索能力，搜索式下只能凭记忆答",
                    fontSize = 11.sp,
                    maxLines = 2,
                    color = UIFriendBarRed.copy(alpha = 0.9f * dim)
                )
            }

            // 失败原因单独一行：塞进副标题会被截断，而「Key 无效」和「超时」
            // 对应的处理办法完全不同，看不全等于没提示
            if (check?.state == UIWerewolfApiState.FAILED && !check.message.isNullOrBlank()) {
                Text(
                    text = check.message,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = UIFriendBarRed.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // 圈和名字行（15sp）顶部对齐即可：圈 24dp、名字行约 20dp，中心只差 2dp，看不出来；
        // 往下挪反而会和第一行副标题错位
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    when {
                        checked -> Modifier.background(UIActionBrush)
                        else -> Modifier.border(1.5.dp, UIGlassStroke, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * 连通状态的小标签。
 *
 * `softWrap = false` 是关键：默认允许换行时，宽度被挤到十几个像素就会一个汉字一行地
 * 竖着排，屏幕上看到的是「已 / 连 / 通」三个字叠起来。宁可省略号，也不要竖排。
 */
@Composable
private fun CheckTag(state: UIWerewolfApiState, elapsedMs: Long? = null) {
    val label = when (state) {
        UIWerewolfApiState.OK -> {
            if (elapsedMs != null) {
                val safe = elapsedMs.coerceAtLeast(0L)
                val time = if (safe < 1_000L) "${safe}ms" else String.format(java.util.Locale.US, "%.1fs", safe / 1000.0)
                "已连通 · $time"
            } else {
                "已连通"
            }
        }
        UIWerewolfApiState.CHECKING -> "检测中"
        else -> "连不上"
    }
    val color = when (state) {
        UIWerewolfApiState.OK -> ConnectedGreen
        UIWerewolfApiState.CHECKING -> UIFriendBarItemSubtitleColor
        else -> UIFriendBarRed
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 底部开局条。
 *
 * 检测失败时不把按钮按死，而是改成「忽略问题，仍然开局」，并在上面加一行红字说明后果。
 * 按死的话，一次偶发超时就能让人永远开不了局，而他手上没有任何别的理由——
 * 模型明明是可用的，只是刚才慢了半拍。把代价写清楚，选择权留给用户。
 */
@Composable
private fun StartBar(
    canStart: Boolean,
    missing: Int,
    playMode: Boolean,
    checking: Boolean,
    failedCount: Int,
    /** 其中有几个是「没有联网搜索能力」而不是「连不上」。 */
    unsupportedCount: Int,
    /** 还在第一页：这一下只负责「去配打法」，不开局。 */
    nextStep: Boolean,
    onNext: () -> Unit,
    onStart: () -> Unit,
    onStartAnyway: () -> Unit,
) {
    val hasFailure = failedCount > 0
    // 第一页不受检测影响：正在测的是上一页那批人，挡着切页只会让人以为卡住了
    val clickable = canStart && (nextStep || !checking)
    // 检测中只有第二页要等——第二页的按钮才真的会开局
    val highlighted = clickable && (nextStep || !hasFailure)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .uiCard(shape = RoundedCornerShape(UIRadiusCard), elevation = 12.dp)
            .pageInsetsBottom()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasFailure && canStart && !nextStep) {
                Text(
                    text = when {
                        // 「不会联网」和「连不上」要分开说：前者不会卡住，但会拿记忆当查证结果
                        unsupportedCount > 0 ->
                            "$unsupportedCount 个座位不会联网检索，搜索式下它们只能凭记忆答"
                        else -> "$failedCount 个座位过不了这一档，开局后第一回合就可能卡住"
                    },
                    modifier = Modifier.padding(horizontal = 6.dp),
                    fontSize = 11.sp,
                    color = UIFriendBarRed
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .shadow(
                        elevation = if (highlighted) 10.dp else 0.dp,
                        shape = RoundedCornerShape(UIRadiusControl),
                        clip = false,
                        ambientColor = UIBlobBlue,
                        spotColor = UIBlobBlue
                    )
                    .clip(RoundedCornerShape(UIRadiusControl))
                    .then(
                        if (highlighted) {
                            Modifier.background(UIActionBrush)
                        } else {
                            Modifier.background(UIGlassSurface)
                        }
                    )
                    .clickable(enabled = clickable) {
                        when {
                            nextStep -> onNext()
                            hasFailure -> onStartAnyway()
                            else -> onStart()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        // 缺人比「正在检测」更该先说：检测跑完也开不了局，用户需要知道的是还差几位
                        !canStart -> "还差 $missing 位好友"
                        nextStep -> "下一步 · AI 设置"
                        checking -> "检查连接中…"
                        hasFailure -> "忽略问题，仍然开局"
                        playMode -> "开局，我参与"
                        else -> "开局，看它们打"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !canStart -> UIFriendBarItemSubtitleColor
                        nextStep -> Color.White
                        checking -> UIFriendBarItemSubtitleColor
                        hasFailure -> UIFriendBarRed
                        else -> Color.White
                    }
                )
            }
        }
    }
}

/**
 * 开局页的提示词风格选择块。视觉和 [SetupLimitChip] 一致——同一页的两个取舍，
 * 不该长得像两种控件。
 */
@Composable
private fun SetupStyleChip(
    style: UIWerewolfPromptStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarTagTextBlue.copy(alpha = 0.14f) else UIFieldFill)
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = if (selected) UIFriendBarTagTextBlue else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = style.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor
        )
        Text(
            text = style.name,
            fontSize = 9.5.sp,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

/**
 * 开局页的时限选择块。
 *
 * 和牌桌上那个面板同一套四档，但这里是「开局前」的取舍：
 * 用户还没开始等，关心的是「这一局大概要多久」，所以副标题写的是毫秒数而不是耗时账本。
 */
@Composable
private fun SetupLimitChip(
    limit: UIWerewolfTurnLimit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarTagTextBlue.copy(alpha = 0.14f) else UIFieldFill)
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = if (selected) UIFriendBarTagTextBlue else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = limit.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor
        )
        Text(
            text = limit.hint,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

/** 发言长度的三档选择，样式和思考时限的 chips 一致。 */
@Composable
private fun SetupSpeechChip(
    length: UIWerewolfSpeechLength,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(UIRadiusControl))
            .background(if (selected) UIFriendBarTagTextBlue.copy(alpha = 0.14f) else UIFieldFill)
            .border(
                width = if (selected) 1.2.dp else 1.dp,
                color = if (selected) UIFriendBarTagTextBlue else UIGlassStroke,
                shape = RoundedCornerShape(UIRadiusControl)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = length.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) UIFriendBarTagTextBlue else UIFriendBarTitleColor
        )
        Text(
            text = length.hint,
            fontSize = 9.5.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = UIFriendBarItemSubtitleColor
        )
    }
}

/**
 * 导演模式里的一行：名字 + 身份选择器。
 *
 * 点右侧的胶囊弹选单。名额用 route 算好的 [UIWerewolfRoleOption.remaining]
 * （头狼和狼人共用狼名额这件事在 route 那边已经算进去了），这里只把
 * 「自己这行占掉的那一个」加回来——自己改派回原身份是合法的。
 */
@Composable
private fun RoleAssignRow(
    friendName: String,
    assignedKey: String?,
    options: List<UIWerewolfRoleOption>,
    onPick: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val assigned = options.firstOrNull { it.key == assignedKey }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = friendName,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarTitleColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = assigned?.label ?: "随机",
            fontSize = 12.sp,
            fontWeight = if (assigned != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (assigned != null) UIFriendBarTagTextBlue else UIFriendBarItemSubtitleColor,
            modifier = Modifier
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(if (assigned != null) UIFriendBarTagTextBlue.copy(alpha = 0.14f) else UIAtelierSand)
                .border(
                    width = 1.dp,
                    color = if (assigned != null) UIFriendBarTagTextBlue else UIGlassStroke,
                    shape = RoundedCornerShape(UIRadiusControl)
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
    if (expanded) {
        com.ui.common.theme.UIIosDialog(
            title = "指定身份 · $friendName",
            subtitle = "选择这一席想拿的牌，也可改回随机发牌",
            onDismiss = { expanded = false },
            actions = {
                com.ui.common.theme.UIDialogDismissButton(
                    text = "取消",
                    onClick = { expanded = false }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SetupPickerOptionRow(
                    title = "随机（不指定）",
                    subtitle = "开局时从剩余身份里随机分配",
                    selected = assignedKey == null,
                    enabled = true,
                    onClick = {
                        expanded = false
                        onPick(null)
                    }
                )
                options.forEach { option ->
                    val left = if (option.key == assignedKey) option.remaining + 1 else option.remaining
                    val selectable = left > 0 || option.key == assignedKey
                    SetupPickerOptionRow(
                        title = option.label,
                        subtitle = if (left == option.total) {
                            "${option.total} 个名额"
                        } else {
                            "剩 $left 个名额"
                        },
                        selected = option.key == assignedKey,
                        enabled = selectable,
                        onClick = {
                            expanded = false
                            onPick(option.key)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleAssignRow(
    friendName: String,
    assignedId: String?,
    onPick: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val assigned = UI_PLAYER_STYLES.firstOrNull { it.id == assignedId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = friendName,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = UIFriendBarTitleColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = assigned?.label ?: "自动",
            fontSize = 12.sp,
            fontWeight = if (assigned != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (assigned != null) UIFriendBarTagTextBlue else UIFriendBarItemSubtitleColor,
            modifier = Modifier
                .clip(RoundedCornerShape(UIRadiusControl))
                .background(if (assigned != null) UIFriendBarTagTextBlue.copy(alpha = 0.14f) else UIAtelierSand)
                .border(
                    width = 1.dp,
                    color = if (assigned != null) UIFriendBarTagTextBlue else UIGlassStroke,
                    shape = RoundedCornerShape(UIRadiusControl)
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
    if (expanded) {
        com.ui.common.theme.UIIosDialog(
            title = "牌手风格 · $friendName",
            subtitle = "决定这个 AI 的发言与博弈气质",
            onDismiss = { expanded = false },
            actions = {
                com.ui.common.theme.UIDialogDismissButton(
                    text = "取消",
                    onClick = { expanded = false }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SetupPickerOptionRow(
                    title = "自动分配（推荐）",
                    subtitle = "按座位自动分配不同风格，同桌尽量不重样",
                    selected = assignedId == null,
                    enabled = true,
                    onClick = {
                        expanded = false
                        onPick(null)
                    }
                )
                UI_PLAYER_STYLES.forEach { style ->
                    SetupPickerOptionRow(
                        title = style.label,
                        subtitle = style.tagline,
                        selected = style.id == assignedId,
                        enabled = true,
                        onClick = {
                            expanded = false
                            onPick(style.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupPickerOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(UIRadiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> UIFriendBarTagTextBlue.copy(alpha = 0.12f)
                    enabled -> UIAtelierSand.copy(alpha = 0.65f)
                    else -> UIAtelierSand.copy(alpha = 0.30f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) UIFriendBarTagTextBlue else UIGlassStroke,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (enabled) UIFriendBarTitleColor else UIFriendBarItemSubtitleColor.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = if (enabled) UIFriendBarItemSubtitleColor else UIFriendBarItemSubtitleColor.copy(alpha = 0.45f)
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(UIActionBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
