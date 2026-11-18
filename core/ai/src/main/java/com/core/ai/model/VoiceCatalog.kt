package com.core.ai.model

import com.core.repository.settings.TTSProviderCatalog

/**
 * 建角色页展示用的「TTS 服务」选项。
 *
 * 数据来自 [TTSProviderCatalog]（core:repository），这里只做一层薄 DTO：
 * ui:agent 依赖 core:ai 但不依赖 core:repository，不能直接看目录实体。
 * 多一个「默认」选项让不想指定的角色跟随全局。
 */
data class TTSServiceOption(val id: String?, val label: String)

object TTSServiceCatalog {
    val all: List<TTSServiceOption> = listOf(TTSServiceOption(null, "默认服务")) +
        TTSProviderCatalog.all.map { TTSServiceOption(it.id, it.name) }

    fun labelOf(id: String?): String = all.firstOrNull { it.id == id }?.label ?: "默认服务"
}

/**
 * 音色分类，只用于选择面板分组。
 *
 * 一家三十个音色平铺成一行是找不动的，用户想的永远是「给我个女声」「来个方言」。
 * 方言优先于性别：找粤语的人不会先去翻女声组。
 */
enum class VoiceGroup(val label: String) {
    FEMALE("女声"),
    MALE("男声"),
    CHILD("童声"),
    DIALECT("方言"),
    OTHER("其他"),
}

/**
 * 可选的云端朗读音色。
 *
 * 各家音色 ID 完全不同（MiniMax 是 male-qn-qingse、讯飞是 xiaoyan、
 * 火山是 zh_female_qingxin、腾讯云是数字音色号、阿里是 longanyang），
 * 所以按服务商分组：[forProvider] 只返回该服务能用的音色。
 *
 * 放在 core:ai 而不是 core:tts：ui:agent 在建角色页展示、feature:werewolf 在朗读时
 * 取用，两者都只依赖 core:ai。core:tts 只认 voiceId 字符串，不认识展示名。
 *
 * [id] 为 null 表示「跟随引擎默认音色」，与 Friend.voiceId 的 null 语义一致。
 *
 * **内置列表只放官方文档里能查到的音色**，而且注定不全：各家还有克隆/复刻音色、
 * 国际站音色、需要单独开通的精品音色。所以界面必须允许手填 ID（见 [isBuiltIn]）——
 * 那才是「音色丰富」的真正上限，内置列表只是常用入口。
 *
 * 音色可用性依赖账号权限：腾讯云的精品音色、火山的部分渠道音色都要在控制台开通，
 * 没开通时合成会返回错误。这不是列表写错，用户换一个或用自己的复刻音色即可——
 * 所以选音色的时候必须能当场试听，否则用户会在角色里听不到声音却不知道是音色的问题。
 */
data class VoiceOption(
    val id: String?,
    val label: String,
    /**
     * 分组。不确定性别的（「可靠高管」「电台主持」这类官方只给了风格名的）放
     * [VoiceGroup.OTHER]——猜错比不猜更糟：想找女声的人被塞进一个男声会很困惑。
     */
    val group: VoiceGroup = VoiceGroup.OTHER,
)

object VoiceCatalog {

    // 「自定义（MiniMax 兼容）」走的是同一套 t2a_v2 协议，音色库也就是 MiniMax 的，
    // 单独指向同一个列表而不是靠 forProvider 的回落——回落是给未知 id 兜底的，
    // 不该被当成一个正常的服务在用。
    private val minimaxVoices: List<VoiceOption> = listOf(
        VoiceOption(null, "默认音色"),
        VoiceOption("male-qn-qingse", "青涩青年", VoiceGroup.MALE),
        VoiceOption("male-qn-jingying", "精英青年", VoiceGroup.MALE),
        VoiceOption("male-qn-badao", "霸道青年", VoiceGroup.MALE),
        VoiceOption("male-qn-daxuesheng", "青年大学生", VoiceGroup.MALE),
        VoiceOption("female-shaonv", "少女", VoiceGroup.FEMALE),
        VoiceOption("female-yujie", "御姐", VoiceGroup.FEMALE),
        VoiceOption("female-chengshu", "成熟女性", VoiceGroup.FEMALE),
        VoiceOption("female-tianmei", "甜美女性", VoiceGroup.FEMALE),
        // 下面这批是多语种大模型音色（speech-2.x）。id 里的语言前缀是官方系统音色表的
        // 原文写法，不是展示名——别「顺手」改成中文，改了服务端就不认了。
        VoiceOption("Chinese (Mandarin)_News_Anchor", "新闻主播（女）", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Warm_Bestie", "温暖闺蜜", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Gentleman", "绅士（男）", VoiceGroup.MALE),
        VoiceOption("Chinese (Mandarin)_Sweet_Lady", "甜美女声", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Gentle_Youth", "温和青年", VoiceGroup.MALE),
        VoiceOption("Chinese (Mandarin)_Cute_Spirit", "灵动少女", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Radio_Host", "电台主持", VoiceGroup.OTHER),
        VoiceOption("Chinese (Mandarin)_Lyrical_Voice", "抒情女声", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Wise_Women", "睿智女声", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Mature_Woman", "成熟女声", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Reliable_Executive", "可靠高管", VoiceGroup.OTHER),
        VoiceOption("Chinese (Mandarin)_Unrestrained_Young_Man", "洒脱青年", VoiceGroup.MALE),
        VoiceOption("Chinese (Mandarin)_HK_Flight_Attendant", "港风空姐", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Humorous_Elder", "幽默长者", VoiceGroup.OTHER),
        VoiceOption("Chinese (Mandarin)_Stubborn_Friend", "倔强朋友", VoiceGroup.OTHER),
        VoiceOption("Chinese (Mandarin)_Crisp_Girl", "清脆少女", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_IntellectualGirl", "知性少女", VoiceGroup.FEMALE),
        VoiceOption("Chinese (Mandarin)_Gentle_Senior", "温和长辈", VoiceGroup.OTHER),
        VoiceOption("Cantonese_ProfessionalHost (F)", "粤语女主播", VoiceGroup.DIALECT),
        VoiceOption("Cantonese_PlayfulMan", "粤语顽皮男", VoiceGroup.DIALECT),
    )

    private val byProvider: Map<String, List<VoiceOption>> = mapOf(
        "minimax" to minimaxVoices,
        "custom" to minimaxVoices,
        // xiaoyan/xiaoyu 是「在线语音合成」最常用的两个普通话发音人，
        // ais* 那几个是同一控制台里的其它发音人（需在页面里勾选开通）。
        //
        // xiaolin 曾经被标成「小林（男）」并放进男声组，是个实打实的事故：
        // 讯飞官方发音人列表里它是**「晓琳，青年女声，台湾普通话」**，属于方言类。
        // 结果就是「给剑道部主将配了个台湾腔女声」，听感上还会被误判成音频坏了。
        // 方言类音色一律进 DIALECT 组（不按性别分）——找粤语的人不会先去翻女声列表。
        "iflytek" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("xiaoyan", "小燕（女）", VoiceGroup.FEMALE),
            VoiceOption("xiaoyu", "小宇（男）", VoiceGroup.MALE),
            VoiceOption("xiaomeng", "小梦（女）", VoiceGroup.FEMALE),
            VoiceOption("aisjiuxu", "许久（男）", VoiceGroup.MALE),
            VoiceOption("aisxping", "小萍（女）", VoiceGroup.FEMALE),
            VoiceOption("aisjinger", "小婧（女）", VoiceGroup.FEMALE),
            VoiceOption("aisbabyxu", "许小宝（童）", VoiceGroup.CHILD),
            VoiceOption("xiaolin", "晓琳（台湾腔女声）", VoiceGroup.DIALECT),
        ),
        // 火山：2.0（`_uranus_bigtts`）走 seed-tts-2.0，音质明显好于 1.0，是现在的默认。
        // 标「2.0 / 1.0」是因为两者计费商品不同、账号要分别开通，混在一起用户分不清为什么
        // 有的音色一点就报错。resource id 由协议层按音色 ID 后缀推断（见 TTSProtocols）。
        "volcengine" to listOf(
            VoiceOption(null, "默认音色"),
            // ---- 2.0 · 通用场景 ----
            // 2.0（`_uranus_bigtts`）走 seed-tts-2.0，音质明显好于 1.0，是现在的默认。
            // 标「2.0 / 1.0」是因为两者是分开计费的商品，账号要分别开通，
            // 混在一起用户分不清为什么有的音色一点就报「音色不存在」。resource id 由协议层
            // 按音色 ID 后缀推断（见 TTSProtocols）。
            VoiceOption("zh_female_xiaohe_uranus_bigtts", "小何 2.0（通用女声）", VoiceGroup.FEMALE),
            VoiceOption("zh_male_m191_uranus_bigtts", "云舟 2.0（通用男声）", VoiceGroup.MALE),
            VoiceOption("zh_female_vv_uranus_bigtts", "Vivi 2.0（多语种女声）", VoiceGroup.FEMALE),
            VoiceOption("zh_male_taocheng_uranus_bigtts", "小天 2.0（年轻男声）", VoiceGroup.MALE),
            VoiceOption("zh_male_liufei_uranus_bigtts", "刘飞 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_sophie_uranus_bigtts", "魅力苏菲 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_qingxinnvsheng_uranus_bigtts", "清新女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_tianmeixiaoyuan_uranus_bigtts", "甜美小源 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_tianmeitaozi_uranus_bigtts", "甜美桃子 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_tianmeiyueyue_uranus_bigtts", "甜美悦悦 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_shuangkuaisisi_uranus_bigtts", "爽快思思 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_linjianvhai_uranus_bigtts", "邻家女孩 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_linjiananhai_uranus_bigtts", "邻家男孩 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_meilinvyou_uranus_bigtts", "魅力女友 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_roumeinvyou_uranus_bigtts", "柔美女友 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_wenroumama_uranus_bigtts", "温柔妈妈 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_wenroushunv_uranus_bigtts", "温柔淑女 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_wenrouxiaoya_uranus_bigtts", "温柔小雅 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_wenrouxiaoge_uranus_bigtts", "温柔小哥 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_qinqienv_uranus_bigtts", "亲切女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_kailangjiejie_uranus_bigtts", "开朗姐姐 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_kailangxuezhang_uranus_bigtts", "开朗学长 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_zhixingnv_uranus_bigtts", "知性女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_ruyaqingnian_uranus_bigtts", "儒雅青年 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_youyoujunzi_uranus_bigtts", "悠悠君子 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_yangguangqingnian_uranus_bigtts", "阳光青年 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_huolixiaoge_uranus_bigtts", "活力小哥 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_kuailexiaodong_uranus_bigtts", "快乐小东 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_qingshuangnanda_uranus_bigtts", "清爽男大 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_fanjuanqingnian_uranus_bigtts", "反卷青年 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_gaolengyujie_uranus_bigtts", "高冷御姐 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_gaolengchenwen_uranus_bigtts", "高冷沉稳 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_aojiaobazong_uranus_bigtts", "傲娇霸总 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_baqiqingshu_uranus_bigtts", "霸气青叔 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_yuanboxiaoshu_uranus_bigtts", "渊博小叔 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_dongfanghaoran_uranus_bigtts", "东方浩然 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_gufengshaoyu_uranus_bigtts", "古风少御 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_popo_uranus_bigtts", "婆婆 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_qiaopinv_uranus_bigtts", "俏皮女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_qingchezizi_uranus_bigtts", "清澈梓梓 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_wenjingmaomao_uranus_bigtts", "文静毛毛 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_xinlingjitang_uranus_bigtts", "心灵鸡汤 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_jitangmei_uranus_bigtts", "鸡汤妹妹 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_tiexinnvsheng_uranus_bigtts", "贴心女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_mengyatou_uranus_bigtts", "萌丫头 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_jiaochuannv_uranus_bigtts", "娇喘女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_lanyinmianbao_uranus_bigtts", "懒音绵宝 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_cixingjieshuonan_uranus_bigtts", "磁性解说男声 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_jieshuoxiaoming_uranus_bigtts", "解说小明 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_xuanyijieshuo_uranus_bigtts", "悬疑解说 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_shengyeboke_uranus_bigtts", "深夜播客 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_yizhipiannan_uranus_bigtts", "译制片男 2.0", VoiceGroup.MALE),
            VoiceOption("zh_female_tvbnv_uranus_bigtts", "TVB 女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_male_naiqimengwa_uranus_bigtts", "奶气萌娃 2.0", VoiceGroup.CHILD),
            VoiceOption("zh_male_tiancaitongsheng_uranus_bigtts", "天才童声 2.0", VoiceGroup.CHILD),
            VoiceOption("zh_male_liangsangmengzai_uranus_bigtts", "亮嗓萌仔 2.0", VoiceGroup.CHILD),

            // ---- 2.0 · 角色扮演 ----
            // 这一组是「有角色感」的声音：熊二、唐僧、猪八戒这类。做 AI 好友比通用音色有意思得多，
            // 但风格强烈，配错人设会出戏——所以没混进上面的通用列表。
            VoiceOption("zh_female_cancan_uranus_bigtts", "知性灿灿 2.0（角色扮演）", VoiceGroup.FEMALE),
            VoiceOption("zh_female_sajiaoxuemei_uranus_bigtts", "撒娇学妹 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_zhishuaiyingzi_uranus_bigtts", "直率英子 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_lingling_uranus_bigtts", "玲玲姐姐 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_linxiao_uranus_bigtts", "林潇 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_chunribu_uranus_bigtts", "春日部姐姐 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_ganmaodianyin_uranus_bigtts", "感冒电音姐姐 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_nvleishen_uranus_bigtts", "女雷神 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_yingtaowanzi_uranus_bigtts", "樱桃丸子 2.0", VoiceGroup.CHILD),
            VoiceOption("zh_male_silang_uranus_bigtts", "四郎 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_qingcang_uranus_bigtts", "擎苍 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_xionger_uranus_bigtts", "熊二 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_tangseng_uranus_bigtts", "唐僧 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_zhuangzhou_uranus_bigtts", "庄周 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_zhubajie_uranus_bigtts", "猪八戒 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_lubanqihao_uranus_bigtts", "鲁班七号 2.0", VoiceGroup.CHILD),

            // ---- 2.0 · 视频配音 ----
            VoiceOption("zh_female_liuchangnv_uranus_bigtts", "流畅女声 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_jitangnv_uranus_bigtts", "鸡汤女 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_mizai_uranus_bigtts", "黑猫侦探社咪仔 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_wuzetian_uranus_bigtts", "武则天 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_gujie_uranus_bigtts", "顾姐 2.0", VoiceGroup.FEMALE),
            VoiceOption("zh_female_shaoergushi_uranus_bigtts", "少儿故事 2.0", VoiceGroup.CHILD),
            VoiceOption("zh_female_peiqi_uranus_bigtts", "佩奇猪 2.0", VoiceGroup.CHILD),
            VoiceOption("zh_male_sunwukong_uranus_bigtts", "猴哥 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_dayi_uranus_bigtts", "大壹 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_ruyayichen_uranus_bigtts", "儒雅逸辰 2.0", VoiceGroup.MALE),
            VoiceOption("zh_male_guanggaojieshuo_uranus_bigtts", "广告解说 2.0", VoiceGroup.MALE),

            // ---- 2.0 · 多语种 ----
            VoiceOption("en_male_tim_uranus_bigtts", "Tim（英文男声）", VoiceGroup.MALE),
            VoiceOption("en_female_dacey_uranus_bigtts", "Dacey（英文女声）", VoiceGroup.FEMALE),
            VoiceOption("en_female_stokie_uranus_bigtts", "Stokie（英文女声）", VoiceGroup.FEMALE),
            VoiceOption("ja_female_bv024_uranus_bigtts", "Bonnie（日语女声）", VoiceGroup.FEMALE),
            VoiceOption("ja_male_bv524_uranus_bigtts", "Ken（日语男声）", VoiceGroup.MALE),
            VoiceOption("ko_male_bv545_uranus_bigtts", "Jay（韩语男声）", VoiceGroup.MALE),
            VoiceOption("ko_female_bv546_uranus_bigtts", "Momo（韩语女声）", VoiceGroup.FEMALE),

            // ---- 1.0 ----
            VoiceOption("zh_female_qingxin", "清新女声（1.0）", VoiceGroup.FEMALE),
            VoiceOption("zh_male_zhixing", "知性男声（1.0）", VoiceGroup.MALE),
            // id 里是 female，官方展示名却是「大叔」——以官方展示名为准，性别存疑就不分。
            VoiceOption("zh_female_wanqudashu_moon_bigtts", "湾湾大叔（1.0）", VoiceGroup.OTHER),
            VoiceOption("zh_male_zhixing_moon_bigtts", "知性男声（1.0 大模型）", VoiceGroup.MALE),
            VoiceOption("zh_female_yueyunv_mars_bigtts", "粤语小溏（1.0）", VoiceGroup.DIALECT),
            VoiceOption("zh_male_shenyeboke_emo_v2_mars_bigtts", "深夜播客（1.0）", VoiceGroup.MALE),
            VoiceOption("zh_male_bv139_audiobook_ummv3_bigtts", "高冷沉稳（1.0）", VoiceGroup.MALE),
            VoiceOption("zh_female_xueayi_saturn_bigtts", "儿童绘本（1.0）", VoiceGroup.CHILD),
        ),
        // CosyVoice-v2 音色，阿里云百炼官方音色表的原文参数值（longxxx_v2 这种）。
        "alibaba" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("longanyang", "龙安洋", VoiceGroup.MALE),
            VoiceOption("longxiaochun_v2", "龙小淳（温柔姐姐）", VoiceGroup.FEMALE),
            VoiceOption("longxiaoxia_v2", "龙小夏（沉稳权威）", VoiceGroup.FEMALE),
            VoiceOption("longwan_v2", "龙婉（普通话女声）", VoiceGroup.FEMALE),
            VoiceOption("longyue_v2", "龙悦（温暖磁性）", VoiceGroup.FEMALE),
            VoiceOption("longmiao_v2", "龙妙（抑扬顿挫）", VoiceGroup.FEMALE),
            VoiceOption("longyumi_v2", "YUMI（正经青年女）", VoiceGroup.FEMALE),
            VoiceOption("longyuan_v2", "龙远（温暖治愈）", VoiceGroup.MALE),
            VoiceOption("longshu_v2", "龙书（新闻男声）", VoiceGroup.MALE),
            VoiceOption("longshuo_v2", "龙硕（博学干练）", VoiceGroup.MALE),
            VoiceOption("longxiu_v2", "龙修（博才说书）", VoiceGroup.MALE),
            VoiceOption("longnan_v2", "龙楠（睿智青年）", VoiceGroup.MALE),
            VoiceOption("longcheng_v2", "龙橙（阳光男声）", VoiceGroup.MALE),
            VoiceOption("longjing_v2", "龙婧（新闻主播）", VoiceGroup.FEMALE),
            VoiceOption("loongbella_v2", "Bella（干练女声）", VoiceGroup.FEMALE),
            VoiceOption("longhua_v2", "龙华（活泼女童）", VoiceGroup.CHILD),
            VoiceOption("longanyun", "龙安昀（居家暖男）", VoiceGroup.MALE),
            VoiceOption("longlaobo", "龙老伯（沧桑）", VoiceGroup.MALE),
            VoiceOption("longlaoyi", "龙老姨（从容）", VoiceGroup.FEMALE),
        ),
        // 腾讯云 TextToVoice 的 VoiceType。101001 起是精品音色（音质更好、单价更高），
        // 100510000 是阅读男声；101019 粤语、101040 四川话、101056 东北话是方言音色。
        "tencent" to listOf(
            VoiceOption(null, "默认音色"),
            VoiceOption("101001", "智瑜（情感女声）", VoiceGroup.FEMALE),
            VoiceOption("101002", "智聆（通用女声）", VoiceGroup.FEMALE),
            VoiceOption("101003", "智美（客服女声）", VoiceGroup.FEMALE),
            VoiceOption("101004", "智云（通用男声）", VoiceGroup.MALE),
            VoiceOption("101005", "智莉（通用女声）", VoiceGroup.FEMALE),
            VoiceOption("101006", "智言（助手女声）", VoiceGroup.FEMALE),
            VoiceOption("101008", "智琪（客服女声）", VoiceGroup.FEMALE),
            VoiceOption("101009", "智芸（知性女声）", VoiceGroup.FEMALE),
            VoiceOption("101010", "智华（通用男声）", VoiceGroup.MALE),
            VoiceOption("101011", "智燕（新闻女声）", VoiceGroup.FEMALE),
            VoiceOption("101012", "智丹（新闻女声）", VoiceGroup.FEMALE),
            VoiceOption("101013", "智辉（新闻男声）", VoiceGroup.MALE),
            VoiceOption("101014", "智宁（新闻男声）", VoiceGroup.MALE),
            VoiceOption("101015", "智萌（男童声）", VoiceGroup.CHILD),
            VoiceOption("101016", "智甜（女童声）", VoiceGroup.CHILD),
            VoiceOption("101017", "智蓉（情感女声）", VoiceGroup.FEMALE),
            VoiceOption("101018", "智靖（情感男声）", VoiceGroup.MALE),
            VoiceOption("101019", "智彤（粤语女声）", VoiceGroup.DIALECT),
            VoiceOption("101040", "智川（四川话）", VoiceGroup.DIALECT),
            VoiceOption("101056", "智林（东北话）", VoiceGroup.DIALECT),
            VoiceOption("100510000", "智逍遥（阅读男声）", VoiceGroup.MALE),
        ),
    )

    /** MiniMax 音色也作为「默认服务 / 没指定服务」时的展示，保证旧角色能回显。 */
    val all: List<VoiceOption> = byProvider.getValue("minimax")

    /** 选中某个 TTS 服务时，界面只展示该服务的音色。 */
    fun forProvider(providerId: String?): List<VoiceOption> =
        byProvider[providerId] ?: all

    /** 面板里单独摆在最上方的「跟随该服务默认音色」，id 为 null，不参与分组。 */
    fun defaultOption(providerId: String?): VoiceOption =
        forProvider(providerId).firstOrNull { it.id == null } ?: VoiceOption(null, "默认音色")

    /**
     * 按 [VoiceGroup] 顺序分组，返回非空分组。
     *
     * 「默认音色」（id=null）不在这里——它由 [defaultOption] 单独展示，
     * 混进「其他」组会让人以为它也是某个具体音色。
     */
    fun grouped(providerId: String?): List<Pair<VoiceGroup, List<VoiceOption>>> =
        VoiceGroup.entries.mapNotNull { group ->
            val options = forProvider(providerId).filter { it.id != null && it.group == group }
            if (options.isEmpty()) null else group to options
        }

    /**
     * 边输边筛：名字和 ID 都匹配，方便用户从官方文档抄了一半的字符串也能找回来。
     * 空关键字返回该服务的全部音色（含默认项），交给界面自己决定怎么摆。
     */
    fun search(providerId: String?, query: String): List<VoiceOption> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return forProvider(providerId)
        return forProvider(providerId).filter { option ->
            option.label.contains(keyword, ignoreCase = true) ||
                (option.id?.contains(keyword, ignoreCase = true) == true)
        }
    }

    /** 界面回显：未知 id（音色下线、自定义填错等）也别崩，落到「默认」。 */
    fun labelOf(id: String?): String = all.firstOrNull { it.id == id }?.label ?: "默认音色"

    /**
     * 界面回显，按服务商找：音色 id 各家不通用，跨服务找会把腾讯的 101001
     * 显示成 MiniMax 的某个名字。
     */
    fun labelOf(providerId: String?, id: String?): String {
        if (id.isNullOrBlank()) return "默认音色"
        return forProvider(providerId).firstOrNull { it.id == id }?.label ?: id
    }

    /** 默认音色 id（引擎侧也有一份，这里给 UI 回填用）。 */
    fun defaultVoiceOf(providerId: String?): String? =
        providerId?.let { TTSProviderCatalog.byId(it)?.defaultVoice }

    /**
     * 这个音色是不是当前服务的内置项。
     *
     * 返回 false 只说明「不是列表里的」，**不代表不能用**——自定义填进来的
     * 复刻音色、国际站音色同样合法。界面据此把它显示成「自定义：xxx」。
     */
    fun isBuiltIn(providerId: String?, voiceId: String?): Boolean {
        if (voiceId.isNullOrBlank()) return true
        return forProvider(providerId).any { it.id == voiceId }
    }

    /**
     * 给一个没指定音色的角色挑一个「这台机器上还没被用过」的内置音色。
     *
     * 只给狼人杀这类「同一时刻很多人说话」的场景用：八个人共用一个默认音色，
     * 听起来就是一个人在自言自语，分不清谁在说话。
     *
     * [used] 是这个服务商下已经被占用的音色，函数只读取、不修改——
     * 由调用方决定什么时候登记（先登记显式指定的，再给其它座位分）。
     */
    fun spareVoice(providerId: String?, used: Set<String>): String? =
        forProvider(providerId)
            .mapNotNull { it.id }
            .firstOrNull { it !in used }
}
