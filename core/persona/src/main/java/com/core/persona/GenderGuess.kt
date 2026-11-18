package com.core.persona

/**
 * 角色的性别。
 *
 * 只用来挑音色（音色是按性别分组的），所以 UNKNOWN 是一等公民：
 * 判断不出来就保持原来的随机分配，比猜错好——把「阿毒」硬判成男声，
 * 和把「大小姐」配成男声一样是错的，只是错得没那么明显。
 */
enum class CharacterGender {
    FEMALE,
    MALE,
    UNKNOWN,
}

/**
 * 从人设里判断角色性别。
 *
 * 为什么需要它：音色目录是按性别分组的（女声/男声/童声），
 * 批量创建时如果不管性别，「大小姐」会配一把男声——用户一进语音就发现不对，
 * 而这种错误在列表里完全看不出来。
 *
 * 判断顺序：
 * 1. **描述文本里的性别称谓**（role + prompt + tags）。这一层最准：
 *    「三无少女」「机械少女」「名门千金」这类设定里性别是写明的。
 * 2. **名字里的用字**。模板名往往只是昵称（「星梨」「阿雪」），没有称谓，
 *    只能靠中文名常用字判断，属于弱信号。
 *
 * 两层都判断不出就 UNKNOWN，不硬猜。
 */
object GenderGuess {

    /**
     * 明确的性别称谓。这些词出现就说明角色性别是写死的。
     *
     * 宁可漏也不要误伤：「侍女」「师兄」「仙子」这类有歧义的词一律不收，
     * 收错了比不收更糟——「侍女」会被判成女声，但那个角色可能是个男的。
     */
    private val FEMALE_TERMS = listOf(
        "少女", "千金", "大小姐", "小姐", "姐姐", "姑娘", "女孩", "女生",
        "女神", "公主", "女王", "夫人", "太太", "娘娘", "淑女", "才女", "御姐",
        "萝莉", "辣妹", "圣女", "魔女", "女巫", "女侠", "妈妈", "母亲", "女儿",
        "母后", "女仆", "天后", "娘", "学姐", "班长", "巫女", "妹妹", "孙女", "学妹",
    )

    private val MALE_TERMS = listOf(
        "少年", "公子", "少爷", "王子", "陛下", "大哥", "哥哥", "老爷", "大叔", "伯伯",
        "爷爷", "书生", "和尚", "道士", "道长", "汉子", "男人",
        "父亲", "儿子", "爸爸", "兄长", "男爵", "男神", "绅士",
        "先生", "学长", "侄子", "师兄",
    )

    /** 名字里常见的女性用字。命中数多于男性用字时判女。 */
    private val FEMALE_CHARS = setOf('娜', '妮', '丽', '芳', '娟', '敏', '秀', '燕', '琳', '婷', '雪', '佳', '琪', '颖', '慧', '洁', '萍', '兰', '凤', '琴', '瑶', '怡', '欣', '露', '楠', '薇', '柔', '婉', '姝', '玥', '芮', '涵', '萱', '梦', '静', '澜', '依', '念', '悠', '芷', '若', '绮', '珊', '媛', '妤', '湘', '语', '姝', '婳', '玥', '黛', '妍', '筝', '萤', '汐', '夭')

    /** 名字里常见的男性用字。 */
    private val MALE_CHARS = setOf('伟', '强', '军', '磊', '涛', '杰', '斌', '鹏', '宇', '浩', '辰', '阳', '明', '刚', '平', '辉', '健', '龙', '飞', '峰', '鑫', '雷', '骏', '泽', '睿', '皓', '宸', '轩', '朗', '毅', '钧', '铭', '淳', '越', '骁', '珩', '昀', '戬')

    /** 常见的英文女名。中文用字表管不到，得单独列一小份。 */
    private val FEMALE_LATIN = setOf(
        "mia", "emma", "anna", "lily", "sophia", "olivia", "ava", "chloe", "ella",
        "grace", "ruby", "alice", "clara", "iris", "nora", "zoe",
    )

    /** 常见的英文男名。 */
    private val MALE_LATIN = setOf(
        "leo", "jack", "james", "mark", "alan", "tom", "lucas", "ryan", "noah",
        "ethan", "kai", "yuri", "harry", "ivan", "oscar",
    )

    /**
     * 判断一个角色的性别。
     *
     * @param name 角色名（「大小姐」「星梨」）。
     * @param description 角色设定文本：身份、详细描述、标签拼一起。
     */
    fun of(name: String, description: String = ""): CharacterGender {
        val text = description
        val term = firstTerm(text)
        if (term != null) return term

        // 名字的后缀比用字可靠：「龙娘」里「娘」比「龙」更能说明性别
        val suffix = suffixGender(name)
        if (suffix != null) return suffix

        // 英文名先看整体（「Mia」不是一个字能判断的）
        val latin = name.trim().lowercase()
        if (latin in FEMALE_LATIN) return CharacterGender.FEMALE
        if (latin in MALE_LATIN) return CharacterGender.MALE

        // 没有称谓，就看名字用字
        var female = 0
        var male = 0
        name.forEach { ch ->
            if (ch in FEMALE_CHARS) female++
            if (ch in MALE_CHARS) male++
        }
        return when {
            female > male -> CharacterGender.FEMALE
            male > female -> CharacterGender.MALE
            else -> CharacterGender.UNKNOWN
        }
    }

    /** 名字后缀：「娘」「妹」「姐」一定是女的，「哥」「弟」「郎」一定是男的。 */
    private fun suffixGender(name: String): CharacterGender? {
        val last = name.trim().lastOrNull() ?: return null
        return when (last) {
            '娘', '妹', '姐', '姬' -> CharacterGender.FEMALE
            '哥', '弟', '叔', '伯', '郎', '兄' -> CharacterGender.MALE
            else -> null
        }
    }

    /**
     * 找出描述里**最先出现**的性别称谓。
     *
     * 取最先出现的而不是「女性词优先」：人设里可能同时出现
     * 「他小时候被当成女孩子养」这种描述，先出现的那个才是主设定。
     */
    private fun firstTerm(text: String): CharacterGender? {
        var bestIndex = Int.MAX_VALUE
        var best: CharacterGender? = null
        FEMALE_TERMS.forEach { term ->
            val index = text.indexOf(term)
            if (index >= 0 && index < bestIndex) {
                bestIndex = index
                best = CharacterGender.FEMALE
            }
        }
        MALE_TERMS.forEach { term ->
            val index = text.indexOf(term)
            if (index >= 0 && index < bestIndex) {
                bestIndex = index
                best = CharacterGender.MALE
            }
        }
        return best
    }
}
