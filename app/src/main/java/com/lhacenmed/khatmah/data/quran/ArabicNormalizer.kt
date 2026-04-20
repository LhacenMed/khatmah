package com.lhacenmed.khatmah.data.quran

/**
 * Normalizes Arabic text for search by unifying visually/semantically equivalent
 * characters and stripping diacritics (harakat).
 *
 * Applied to BOTH the search query and the stored arabic_text values so matching
 * is symmetric regardless of how either side was encoded.
 *
 *   أ / إ / آ / ٱ (U+0671) → ا   all alef variants → bare alef
 *   ؤ             → و              waw with hamza
 *   ئ             → ي              yeh with hamza above
 *   ة             → ه              teh marbuta → heh
 *   ى             → ي              alef maqsura → yeh
 *   U+064B–U+065F  stripped        Arabic combining diacritics (harakat)
 *   U+0640         stripped        tatweel / kashida
 */
fun String.normalizeArabic(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        if (c in '\u064B'..'\u065F' || c == '\u0640') continue  // strip harakat + tatweel
        sb.append(
            when (c) {
                'أ', 'إ', 'آ', '\u0671' -> 'ا'  // all alef forms → bare alef
                'ؤ'                      -> 'و'
                'ئ'                      -> 'ي'
                'ة'                      -> 'ه'
                'ى'                      -> 'ي'
                else                     -> c
            }
        )
    }
    return sb.toString()
}
