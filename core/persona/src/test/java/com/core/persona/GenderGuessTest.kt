package com.core.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 性别推断的测试。
 *
 * 这个判断只用来挑音色，判断错了就是「大小姐配一把男声」——
 * 用户一进语音就出戏，而列表上完全看不出哪里不对。所以宁可判不出来，
 * 也不能把明确的女角色判成男。
 */
class GenderGuessTest {

    @Test
    fun `描述里的称谓最准`() {
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("大小姐", "Claude 拟人·名门千金"))
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("凛", "三无少女·一针见血"))
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("雪音", "神社巫女·驱邪却怕鬼"))
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("小傲", "傲娇学姐·口是心非"))
        assertEquals(CharacterGender.MALE, GenderGuess.of("半仙", "赛博算命先生"))
        assertEquals(CharacterGender.MALE, GenderGuess.of("审哥", "代码审查员"))
    }

    @Test
    fun `名字后缀比用字可靠`() {
        // 「龙娘」里「娘」比「龙」更能说明性别
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("龙娘"))
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("小妹"))
        assertEquals(CharacterGender.MALE, GenderGuess.of("铁哥"))
        assertEquals(CharacterGender.MALE, GenderGuess.of("字叔"))
    }

    @Test
    fun `英文名认整体不认单个字母`() {
        assertEquals(CharacterGender.FEMALE, GenderGuess.of("Mia"))
        assertEquals(CharacterGender.MALE, GenderGuess.of("Leo"))
    }

    @Test
    fun `判断不出来就说不知道`() {
        assertEquals(CharacterGender.UNKNOWN, GenderGuess.of("账本", "记账管家"))
        assertEquals(CharacterGender.UNKNOWN, GenderGuess.of("分镜", "短视频编剧"))
        assertEquals(CharacterGender.UNKNOWN, GenderGuess.of("刺头", "毒舌损友"))
    }

    @Test
    fun `内置模板里性别明确的那些都判得对`() {
        // 不要求所有模板都判得出来：工具型角色（记账管家、面试官、广告文案）
        // 本来就没有性别，硬猜只会配错。这里只钉住**写明了性别的**那些。
        val expected = mapOf(
            "大小姐" to CharacterGender.FEMALE,
            "龙娘" to CharacterGender.FEMALE,
            "小咪" to CharacterGender.FEMALE,
            "大肥鱼" to CharacterGender.FEMALE,
            "凛" to CharacterGender.FEMALE,
            "小傲" to CharacterGender.FEMALE,
            "雪音" to CharacterGender.FEMALE,
            "星梨" to CharacterGender.FEMALE,
            "澜汐" to CharacterGender.FEMALE,
            "夜澄" to CharacterGender.FEMALE,
            "文姐" to CharacterGender.FEMALE,
            "数姐" to CharacterGender.FEMALE,
            "Mia" to CharacterGender.FEMALE,
            "审哥" to CharacterGender.MALE,
            "铁哥" to CharacterGender.MALE,
            "字叔" to CharacterGender.MALE,
            "长文兄" to CharacterGender.MALE,
            "半仙" to CharacterGender.MALE,
        )
        val catalog = PersonaCatalog.all.associateBy { it.name }
        expected.forEach { (name, gender) ->
            val persona = catalog[name]
            assertTrue("模板里没有 $name", persona != null)
            assertEquals(name, gender, GenderGuess.of(persona!!.name, persona.descriptionText))
        }
    }

    @Test
    fun `明确写着女性的角色不会判成男`() {
        // 这一条是「宁可不判也不能判错」的具体化
        PersonaCatalog.all.forEach { persona ->
            val gender = GenderGuess.of(persona.name, persona.descriptionText)
            val text = persona.descriptionText
            if (gender == CharacterGender.MALE) {
                listOf("少女", "千金", "大小姐", "学姐", "巫女", "魔女", "娘").forEach { word ->
                    assertTrue(
                        "${persona.name} 是女性角色，不能判成男",
                        !text.contains(word),
                    )
                }
            }
        }
    }
}
