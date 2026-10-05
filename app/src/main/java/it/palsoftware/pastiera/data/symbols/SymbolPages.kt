package it.palsoftware.pastiera.data.symbols

/**
 * The symbols page's pages after the recent symbols, when it shows pages (Q back, P on): 24 symbols
 * each, one per key from W along the rows. Text symbols only, none that phones draw as emoji.
 */
object SymbolPages {
    val PAGES: List<List<String>> = listOf(
        // Common
        "@ # & * % ^ _ ~ | \\ / < > { } [ ] = + - ! ? : ;",
        // Arrows
        "← → ↑ ↓ ↔ ↕ ⇐ ⇒ ⇑ ⇓ ⇔ ↖ ↗ ↘ ↙ ↩ ↪ ⟵ ⟶ ➜ ➔ ↻ ↺ ⇄",
        // Maths
        "± × ÷ ≈ ≠ ≤ ≥ ∞ √ ∑ ∏ ∫ ∂ ∆ π µ ° ‰ ¹ ² ³ ½ ¼ ¾",
        // Currency and units
        "€ £ ¥ ¢ $ ₹ ₽ ₩ ₺ ₿ ₴ ₫ ₪ ₦ ₱ ₣ ℃ ℉ № ℓ ㎏ ㎝ ㎡ ™",
        // Punctuation and typography
        "“ ” ‘ ’ « » ‹ › „ ‚ … – — · • ¡ ¿ § ¶ † ‡ © ® ‽",
        // Shapes, stars and marks
        "★ ☆ ✦ ✧ ✩ ● ○ ◆ ◇ ■ □ ▲ △ ▼ ▽ ♥ ♡ ♦ ♣ ♠ ✓ ✗ ✔ ✘",
        // Music, keys and odds and ends
        "♪ ♫ ♬ ♩ ☾ ✎ ✐ ⌘ ⌥ ⇧ ⌫ ⏎ ⎋ ⇥ ☐ ☑ ☒ ✱ ✳ ❖ ∴ ∵ ※ ⁂",
    ).map { it.split(' ') }
}
