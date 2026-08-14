package io.legado.app.ui.main.explore.modern

/**
 * 现代发现布局专用的文本显示规则。
 *
 * 分类名称只移除首尾标点和装饰符，名称中间的符号保持不变。
 */
internal object ModernDiscoveryText {

    private val wrapSymbolRegex = Regex("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$")

    fun stripWrapSymbols(text: String): String {
        val cleaned = wrapSymbolRegex.replace(text.trim(), "").trim()
        return cleaned.ifEmpty { text }
    }
}
