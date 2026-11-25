package dev.chesscrab.app.ui.board

/**
 * Öğretici tehdit katmanı: bir tarafın "asılı" ya da kötü korunan taşları.
 * Bir taş tehdit altında sayılır: rakip ona saldırıyorsa ve (hiç korunmuyorsa
 * ya da kendisinden değersiz bir taşla saldırılıyorsa). Şah ayrıca şah sonarıyla gösterilir.
 */
object Threats {
    private val VALUE = mapOf('p' to 1, 'n' to 3, 'b' to 3, 'r' to 5, 'q' to 9, 'k' to 100)

    private fun at(b: Map<String, Char>, f: Int, r: Int): Char? = if (f in 0..7 && r in 0..7) b["${'a' + f}${r + 1}"] else null

    /** `byWhite` tarafının (f, r) karesine saldıran taşların değerleri */
    private fun attackers(b: Map<String, Char>, f: Int, r: Int, byWhite: Boolean): List<Int> {
        val out = mutableListOf<Int>()
        fun own(c: Char?) = c != null && c.isUpperCase() == byWhite
        // piyon
        val pr = if (byWhite) r - 1 else r + 1
        for (df in listOf(-1, 1)) at(b, f + df, pr)?.let { if (own(it) && it.lowercaseChar() == 'p') out += 1 }
        // at
        for ((df, dr) in listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1))
            at(b, f + df, r + dr)?.let { if (own(it) && it.lowercaseChar() == 'n') out += 3 }
        // şah
        for (df in -1..1) for (dr in -1..1) if (df != 0 || dr != 0)
            at(b, f + df, r + dr)?.let { if (own(it) && it.lowercaseChar() == 'k') out += 100 }
        // kayan taşlar
        val diag = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        val line = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        for ((dirs, kinds) in listOf(diag to "bq", line to "rq")) for ((df, dr) in dirs) {
            var x = f + df; var y = r + dr
            while (x in 0..7 && y in 0..7) {
                val c = at(b, x, y)
                if (c != null) { if (own(c) && c.lowercaseChar() in kinds) out += VALUE.getValue(c.lowercaseChar()); break }
                x += df; y += dr
            }
        }
        return out
    }

    /** `whiteSide` tarafının tehdit altındaki taşlarının kareleri */
    fun of(fen: String, whiteSide: Boolean): Set<String> {
        val b = parseBoard(fen)
        val out = HashSet<String>()
        for ((sq, c) in b) {
            if (c.isUpperCase() != whiteSide || c.lowercaseChar() == 'k') continue
            val f = sq[0] - 'a'; val r = sq[1] - '1'
            val att = attackers(b, f, r, !whiteSide)
            if (att.isEmpty()) continue
            val def = attackers(b, f, r, whiteSide)
            if (def.isEmpty() || att.min() < VALUE.getValue(c.lowercaseChar())) out += sq
        }
        return out
    }
}
