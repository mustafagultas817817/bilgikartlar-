package com.bilgikartlari.app

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ImportCard(
    val q: String,
    val a: String,
    val known: Int = 0,
    val unknown: Int = 0,
    val last: Int = 0,
    val lastSeen: Long = 0L
)

/**
 * lessonName: metindeki @ders değeri.
 * lessonId: önizlemede seçilen ders (null ve newLesson=true ise lessonName adıyla yeni ders açılır).
 */
class ImportUnit(
    var name: String,
    var lessonName: String? = null,
    var emoji: String? = null,
    var color: Int? = null,
    val cards: MutableList<ImportCard> = mutableListOf(),
    val hadHeader: Boolean = true,
    var lessonEmoji: String? = null,
    var lessonColor: Int? = null
) {
    var lessonId: Long? = null
    var newLesson: Boolean = false
}

/**
 * Desteklenen biçimler:
 *
 * 1) Metin (Claude'un hazırladığı biçim):
 *      @ders: Sosyal Bilgiler
 *      @ünite: Kültürel Mirasımız
 *      @simge: 🏛️
 *      @renk: 3
 *      Soru | Cevap
 *    Bir metinde birden çok @ders / @ünite olabilir. "# Başlık" satırı da ünite başlığı sayılır.
 *    Markdown tablosu (| Soru | Cevap |) ve "S: ... / C: ..." satırları da okunur.
 *
 * 2) JSON (yedek dosyası ya da .json kart dosyası):
 *      {"lessons":[{"name":"...","units":[{"name":"...","cards":[{"q":"...","a":"..."}]}]}]}
 *      ya da eski biçim {"units":[...]}
 */
object Importer {

    fun parse(raw: String): List<ImportUnit> {
        val cleaned = raw.replace("﻿", "").trim()
        val noFence = cleaned.lines().filterNot { it.trim().startsWith("```") }.joinToString("\n").trim()
        if (noFence.startsWith("{") || noFence.startsWith("[")) {
            try {
                val r = parseJson(noFence)
                if (r.isNotEmpty()) return r
            } catch (_: Exception) {
            }
        }
        return parseText(noFence)
    }

    fun norm(s: String): String =
        s.lowercase(Locale.ROOT)
            .replace("i̇", "i").replace("ı", "i").replace("ü", "u").replace("ö", "o")
            .replace("ş", "s").replace("ç", "c").replace("ğ", "g").trim()

    private fun parseText(text: String): List<ImportUnit> {
        val units = mutableListOf<ImportUnit>()
        var cur: ImportUnit? = null
        var lesson: String? = null
        var pendingQ: String? = null

        fun current(): ImportUnit {
            val c = cur
            if (c != null) return c
            val n = ImportUnit("", lessonName = lesson, hadHeader = false)
            units.add(n)
            cur = n
            return n
        }

        fun addCard(q: String, a: String) {
            val qq = q.trim().trim('*').trim()
            val aa = a.trim().trim('*').trim()
            if (qq.isEmpty() || aa.isEmpty()) return
            if (norm(qq) == "soru" && norm(aa) == "cevap") return // tablo başlığı
            current().cards.add(ImportCard(qq, aa))
        }

        for (raw in text.lines()) {
            var line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("@") && line.contains(":")) {
                val key = norm(line.substringBefore(":").removePrefix("@"))
                val value = line.substringAfter(":").trim()
                when (key) {
                    "ders", "brans" -> {
                        lesson = value.ifEmpty { null }
                        cur = null
                    }
                    "unite", "unit", "konu", "baslik" -> {
                        val u = ImportUnit(value.ifEmpty { "Yeni ünite" }, lessonName = lesson)
                        units.add(u)
                        cur = u
                    }
                    "simge", "emoji", "ikon" -> if (value.isNotEmpty()) current().emoji = value
                    "renk", "color" -> value.filter { it.isDigit() }.toIntOrNull()?.let { current().color = it - 1 }
                }
                pendingQ = null
                continue
            }

            if (line.startsWith("#")) {
                val title = line.trimStart('#').trim()
                if (title.isNotEmpty()) {
                    val u = ImportUnit(title, lessonName = lesson)
                    units.add(u)
                    cur = u
                }
                pendingQ = null
                continue
            }

            if (line.startsWith("|") && line.endsWith("|") && line.length > 2) {
                val cells = line.substring(1, line.length - 1).split("|").map { it.trim() }
                if (cells.all { c -> c.all { it == '-' || it == ':' || it == ' ' } }) continue
                if (cells.size >= 2) {
                    val start = if (cells.size >= 3 && cells[0].trimEnd('.').all { it.isDigit() }) 1 else 0
                    addCard(cells[start], cells[start + 1])
                }
                continue
            }

            line = line.removePrefix("•").removePrefix("- ").removePrefix("* ").trim()

            val lower = norm(line)
            if (lower.startsWith("s:") || lower.startsWith("soru:")) {
                pendingQ = line.substringAfter(":").trim()
                continue
            }
            if ((lower.startsWith("c:") || lower.startsWith("cevap:")) && pendingQ != null) {
                addCard(pendingQ, line.substringAfter(":").trim())
                pendingQ = null
                continue
            }

            var idx = line.indexOf('|')
            if (idx < 0) idx = line.indexOf('\t')
            if (idx > 0) addCard(line.substring(0, idx), line.substring(idx + 1))
        }
        return units.filter { it.cards.isNotEmpty() }
    }

    private fun parseJson(t: String): List<ImportUnit> {
        val out = mutableListOf<ImportUnit>()
        if (t.startsWith("[")) {
            val arr = JSONArray(t)
            if (arr.length() > 0 && arr.optJSONObject(0)?.has("cards") == true) {
                for (i in 0 until arr.length()) jsonUnit(arr.getJSONObject(i), null)?.let { out.add(it) }
            } else {
                val u = ImportUnit("", hadHeader = false)
                readCards(arr, u)
                if (u.cards.isNotEmpty()) out.add(u)
            }
            return out
        }
        val o = JSONObject(t)
        val lessons = o.optJSONArray("lessons") ?: o.optJSONArray("dersler")
        if (lessons != null) {
            for (i in 0 until lessons.length()) {
                val lo = lessons.getJSONObject(i)
                val ln = lo.optString("name").ifBlank { lo.optString("ad") }.ifBlank { null }
                val le = lo.optString("emoji").ifBlank { null }
                val lc = if (lo.has("color")) lo.optInt("color") else null
                val ua = lo.optJSONArray("units") ?: lo.optJSONArray("uniteler") ?: JSONArray()
                for (j in 0 until ua.length()) jsonUnit(ua.getJSONObject(j), ln)?.let {
                    it.lessonEmoji = le
                    it.lessonColor = lc
                    out.add(it)
                }
            }
            return out
        }
        val units = o.optJSONArray("units") ?: o.optJSONArray("uniteler")
        if (units != null) {
            for (i in 0 until units.length()) jsonUnit(units.getJSONObject(i), null)?.let { out.add(it) }
        } else {
            jsonUnit(o, null)?.let { out.add(it) }
        }
        return out
    }

    private fun jsonUnit(o: JSONObject, lessonName: String?): ImportUnit? {
        val name = o.optString("name").ifBlank { o.optString("unite").ifBlank { o.optString("ad") } }
        val emoji = o.optString("emoji").ifBlank { o.optString("simge") }.ifBlank { null }
        val color = if (o.has("color")) o.optInt("color") else null
        val ln = lessonName ?: o.optString("lesson").ifBlank { o.optString("ders") }.ifBlank { null }
        val u = ImportUnit(name, ln, emoji, color, hadHeader = name.isNotBlank())
        readCards(o.optJSONArray("cards") ?: o.optJSONArray("kartlar") ?: JSONArray(), u)
        return if (u.cards.isEmpty()) null else u
    }

    private fun readCards(arr: JSONArray, u: ImportUnit) {
        for (j in 0 until arr.length()) {
            val c = arr.optJSONObject(j) ?: continue
            val q = c.optString("q").ifBlank { c.optString("soru").ifBlank { c.optString("question") } }.trim()
            val a = c.optString("a").ifBlank { c.optString("cevap").ifBlank { c.optString("answer") } }.trim()
            if (q.isNotEmpty() && a.isNotEmpty()) {
                u.cards.add(ImportCard(q, a, c.optInt("k"), c.optInt("u"), c.optInt("l"), c.optLong("t")))
            }
        }
    }

    private fun key(q: String) = norm(q).replace(Regex("\\s+"), " ")

    fun findLesson(name: String?): Lesson? {
        if (name.isNullOrBlank()) return null
        return Repo.lessons.firstOrNull { norm(it.name) == norm(name) }
    }

    fun findUnit(lesson: Lesson?, name: String): Unite? =
        lesson?.units?.firstOrNull { norm(it.name) == norm(name) }

    /** Önizleme açılırken her ünite için başlangıç dersini belirler. */
    fun resolveDefaults(list: List<ImportUnit>, fixedLesson: Lesson?) {
        list.forEach { iu ->
            if (iu.name.isBlank()) iu.name = "Yeni ünite"
            val found = fixedLesson ?: findLesson(iu.lessonName)
            when {
                found != null -> { iu.lessonId = found.id; iu.newLesson = false }
                !iu.lessonName.isNullOrBlank() -> { iu.lessonId = null; iu.newLesson = true }
                else -> { iu.lessonId = Repo.lessons.firstOrNull()?.id; iu.newLesson = Repo.lessons.isEmpty() }
            }
        }
    }

    /** Var olan ünitede zaten bulunan kart sayısı. */
    fun duplicateCount(iu: ImportUnit, target: Unite?): Int {
        if (target == null) return 0
        val keys = target.cards.map { key(it.q) }.toSet()
        return iu.cards.count { key(it.q) in keys }
    }

    /**
     * Kartları kaydeder. targetUnitId verilirse tüm kartlar o üniteye eklenir.
     * Yoksa her ünite seçilen derse eklenir; aynı adlı ünite varsa kartlar ona eklenir.
     * Aynı soru iki kez eklenmez. Dönüş: (eklenen kart sayısı, son işlenen ünite)
     */
    fun importAll(list: List<ImportUnit>, targetUnitId: Long?): Pair<Int, Unite?> {
        var added = 0
        var last: Unite? = null
        for (iu in list) {
            val target: Unite = targetUnitId?.let { Repo.unit(it) } ?: run {
                val lesson = iu.lessonId?.let { Repo.lesson(it) }
                    ?: findLesson(iu.lessonName)
                    ?: Lesson(
                        Repo.newId(),
                        iu.lessonName?.ifBlank { null } ?: "Derslerim",
                        iu.lessonEmoji ?: "📚",
                        iu.lessonColor ?: (Repo.lessons.size % Palette.COLORS.size)
                    ).also { Repo.lessons.add(it) }
                val unitName = iu.name.ifBlank { "Yeni ünite" }
                findUnit(lesson, unitName)
                    ?: Unite(Repo.newId(), unitName, iu.emoji ?: "📘", iu.color ?: lesson.color).also { lesson.units.add(it) }
            }
            val keys = target.cards.map { key(it.q) }.toMutableSet()
            for (c in iu.cards) {
                val k = key(c.q)
                if (k in keys) continue
                keys.add(k)
                target.cards.add(Card(Repo.newId(), c.q, c.a, c.known, c.unknown, c.last, c.lastSeen))
                added++
            }
            last = target
        }
        Repo.save()
        return added to last
    }

    // ---------- Dışa aktarma ----------

    fun toText(u: Unite): String = buildString {
        Repo.lessonOf(u)?.let { append("@ders: ").append(it.name).append('\n') }
        append("@ünite: ").append(u.name).append('\n')
        append("@simge: ").append(u.emoji).append('\n')
        append("@renk: ").append(u.color + 1).append('\n')
        u.cards.forEach { c ->
            append(c.q.replace("|", "/").replace("\n", " "))
            append(" | ")
            append(c.a.replace("|", "/").replace("\n", " "))
            append('\n')
        }
    }

    fun toJson(withStats: Boolean): JSONObject {
        val la = JSONArray()
        Repo.lessons.forEach { l ->
            val ua = JSONArray()
            l.units.forEach { u ->
                val ca = JSONArray()
                u.cards.forEach { c ->
                    val co = JSONObject().put("q", c.q).put("a", c.a)
                    if (withStats) co.put("k", c.known).put("u", c.unknown).put("l", c.last).put("t", c.lastSeen)
                    ca.put(co)
                }
                ua.put(JSONObject().put("name", u.name).put("emoji", u.emoji).put("color", u.color).put("cards", ca))
            }
            la.put(JSONObject().put("name", l.name).put("emoji", l.emoji).put("color", l.color).put("units", ua))
        }
        return JSONObject().put("format", "bilgikartlari").put("version", 3).put("lessons", la)
    }
}
