package com.feature.werewolf.prompt

/** 座位号列表，写成「1 号、2 号」，不带结尾标点。 */
internal fun seatsText(list: List<Int>): String =
    list.sorted().joinToString("、") { "$it 号" }
