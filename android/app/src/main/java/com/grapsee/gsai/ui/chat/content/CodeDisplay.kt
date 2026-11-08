package com.grapsee.gsai.ui.chat.content

/** Display/JSON editor representation only; Copy and execution keep source. */
internal object CodeDisplay {
    fun escape(source: String): String = buildString {
        var offset = 0
        while (offset < source.length) {
            val cp = source.codePointAt(offset)
            val forbidden = cp > 0x7f && (
                android.icu.lang.UCharacter.hasBinaryProperty(cp, android.icu.lang.UProperty.EMOJI) ||
                cp in 0x1f000..0x1faff || cp == 0x200d || cp == 0x20e3 || cp == 0xfe0e || cp == 0xfe0f || cp in 0xe0020..0xe007f)
            val chars = String(Character.toChars(cp))
            if (forbidden) chars.forEach { append("\\u"); append(it.code.toString(16).padStart(4, '0')) }
            else append(chars)
            offset += Character.charCount(cp)
        }
    }
    fun requiresEncoding(source: String): Boolean = escape(source) != source
    fun encodeEditor(source: String): String = escape(org.json.JSONObject.quote(source))
    private val jsonString = Regex("""\A"(?:[^"\\\u0000-\u001f]|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*"\z""")
    fun decodeEditor(draft: String): String {
        val encoded = draft.trim(' ', '	', '', '
')
        require(jsonString.matches(encoded)) { "Enter one valid JSON-encoded source string." }
        return org.json.JSONTokener(encoded).nextValue() as String
    }
}
