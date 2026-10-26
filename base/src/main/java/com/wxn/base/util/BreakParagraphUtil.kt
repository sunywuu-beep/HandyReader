package com.wxn.base.util

import com.wxn.base.bean.Locator
import com.wxn.base.bean.Locator.Companion.getUtteranceId
import com.wxn.base.bean.SpeakSentence
import java.util.Locale

object BreakParagraphUtil {

    /***
     * 将一个自然段落分割成 多个语句并返回
     */
    fun breakParagraph(paragraph: String,
                       language: Locale,
                       chapterIndex: Int,
                       paragraphIndex: Int): List<SpeakSentence> {
        val sentences = arrayListOf<SpeakSentence>()
        BreakSentenceUtil.breakSentence(paragraph, language).forEach { triple ->
            sentences.add(
                SpeakSentence(
                    triple.first,
                    Locator(
                        id = getUtteranceId(chapterIndex, paragraphIndex, triple.second, paragraphIndex, triple.third),
                        chapterIndex = chapterIndex,
                        startParagraphIndex = paragraphIndex,
                        endParagraphIndex = paragraphIndex,
                        startTextOffset = triple.second,
                        endTextOffset = triple.third,
                        progression = 0.0)
                )
            )
        }
        return sentences
    }

    private val nonProsodicLeadingChars = setOf(
        '~', '*', '/', '\\', '|', '#', '_', '-', '^', '`', ' ', '\t', '\n', '\r',
        ':', '：', ';', '；'
    )

    private val nonProsodicTrailingChars = setOf(
        '~', '*', '/', '\\', '|', '#', '_', '-', '^', '`', ' ', '\t', '\n', '\r'
    )

    private fun stripExtraneousSymbols(text: String): String {
        var start = 0
        var end = text.length
        while (start < end && text[start] in nonProsodicLeadingChars) {
            start++
        }
        while (end > start && text[end - 1] in nonProsodicTrailingChars) {
            end--
        }
        return if (start == 0 && end == text.length) text else text.substring(start, end)
    }

    /**
     * 为 TTS 引擎预处理文本：
     * 1. 保留关键韵律与停顿标点（。！？.!?，,；;:…），以供声学模型生成自然的句尾降调、升调与停顿
     * 2. 删除无声的书名号、引号（避免 TTS 发出噪音或杂音）
     * 3. 将句中的括号替换为空格（插入语微停顿）
     * 4. 将中文双破折号替换为冒号（TTS 停顿）
     * 5. 折叠多余连续空格，并清理首尾无韵律意义的符号（如星号、斜杠、杂质符号）
     */
    fun normalizeForTts(text: String): String {
        if (text.isBlank()) return ""
        val cleaned = buildString(text.length) {
            var lastWasSpace = false
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c == '\u2014' && i + 1 < text.length && text[i + 1] == '\u2014' -> {
                        append(':')
                        lastWasSpace = false
                        i += 2
                    }
                    c == '\u201C' || c == '\u201D' || c == '\u300A' || c == '\u300B' ||
                    c == '\u300C' || c == '\u300D' || c == '\u300E' || c == '\u300F' ||
                    c == '\u00AB' || c == '\u00BB' || c == '"' -> {
                        i++
                    }
                    c == '(' || c == ')' || c == '\uFF08' || c == '\uFF09' ||
                    c == '\uFF3B' || c == '\uFF3D' || c == '\u3010' || c == '\u3011' -> {
                        if (!lastWasSpace) {
                            append(' ')
                            lastWasSpace = true
                        }
                        i++
                    }
                    c == ' ' -> {
                        if (!lastWasSpace) {
                            append(' ')
                            lastWasSpace = true
                        }
                        i++
                    }
                    else -> {
                        append(c)
                        lastWasSpace = false
                        i++
                    }
                }
            }
        }.trim()

        return stripExtraneousSymbols(cleaned)
    }

    private val apostropheChars = setOf(
        '\u0027',
        '\u2018',
        '\u2019',
    )

    fun isAbbreviationApostrophe(chars: List<String>, index: Int): Boolean {
        val c = chars.getOrNull(index)?.firstOrNull() ?: return false
        if (c !in apostropheChars) return false
        val prevChar = chars.getOrNull(index - 1)?.firstOrNull()
        val nextChar = chars.getOrNull(index + 1)?.firstOrNull()
        return prevChar?.isLetter() == true && nextChar?.isLetter() == true
    }
}