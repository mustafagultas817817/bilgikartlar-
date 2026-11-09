package com.bilgikartlari.app

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}

/** last: 0 = hiç çalışılmadı, 1 = son seferde bildi, 2 = son seferde bilemedi */
class Card(
    val id: Long,
    var q: String,
    var a: String,
    var known: Int = 0,
    var unknown: Int = 0,
    var last: Int = 0,
    var lastSeen: Long = 0L
) {
    val answered: Int get() = known + unknown
}

fun percentOf(cards: List<Card>): Int? {
    val total = cards.sumOf { it.answered }
    if (total == 0) return null
    return Math.round(cards.sumOf { it.known } * 100f / total)
}

class Unite(
    val id: Long,
    var name: String,
    var emoji: String = "📘",
    var color: Int = 0,
    val cards: MutableList<Card> = mutableListOf()
) {
    fun successPercent(): Int? = percentOf(cards)
    fun wrongCards(): List<Card> = cards.filter { it.last == 2 }
    fun colorInt(): Int = Palette.color(color)
}

/** Ders: altında üniteler bulunur (ör. Sosyal Bilgiler → Millî Mücadele). */
class Lesson(
    val id: Long,
    var name: String,
    var emoji: String = "📚",
    var color: Int = 0,
    val units: MutableList<Unite> = mutableListOf()
) {
    fun cards(): List<Card> = units.flatMap { it.cards }
    fun successPercent(): Int? = percentOf(cards())
    fun wrongCards(): List<Card> = cards().filter { it.last == 2 }
    fun colorInt(): Int = Palette.color(color)
}

/**
 * unitId = -1 ise dersin tüm ünitelerinden karışık çalışılmıştır.
 * mode: "kart" (bilgi kartı) ya da "test" (çoktan seçmeli)
 */
class Session(
    val lessonId: Long,
    val unitId: Long,
    val name: String,
    val date: Long,
    val known: Int,
    val unknown: Int,
    val mode: String = "kart"
)

object Palette {
    val COLORS = intArrayOf(
        0xFF5B4CDB.toInt(), // 1 mor
        0xFF2F80ED.toInt(), // 2 mavi
        0xFF12A594.toInt(), // 3 turkuaz
        0xFF2EA043.toInt(), // 4 yeşil
        0xFFF2994A.toInt(), // 5 turuncu
        0xFFE5484D.toInt(), // 6 kırmızı
        0xFFD6409F.toInt(), // 7 pembe
        0xFF8E4EC6.toInt()  // 8 eflatun
    )
    val EMOJIS = listOf(
        "📘", "📖", "➗", "🔬", "🌍", "🇹🇷", "🇬🇧", "🕌", "🧪", "🧮", "🏛️", "📜",
        "🎨", "🎵", "⚽", "🌱", "🦁", "💡", "🧠", "✏️", "🗣️", "🚀", "📗", "📙"
    )

    fun color(index: Int): Int = COLORS[((index % COLORS.size) + COLORS.size) % COLORS.size]
}

object Repo {
    private var file: File? = null
    val lessons = mutableListOf<Lesson>()
    val sessions = mutableListOf<Session>()
    var userName: String = ""
    var askedName: Boolean = false
    private var nextId = 1L

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "data.json")
        file = f
        if (f.exists()) {
            try {
                load(f)
                return
            } catch (e: Exception) {
                f.copyTo(File(ctx.filesDir, "data_bozuk_${System.currentTimeMillis()}.json"), overwrite = true)
                lessons.clear(); sessions.clear(); nextId = 1L
            }
        }
        Seed.fill()
        save()
    }

    fun newId(): Long = nextId++

    // ---------- Arama ----------

    fun allUnits(): List<Unite> = lessons.flatMap { it.units }
    fun allCards(): List<Card> = allUnits().flatMap { it.cards }
    fun lesson(id: Long): Lesson? = lessons.firstOrNull { it.id == id }
    fun unit(id: Long): Unite? = allUnits().firstOrNull { it.id == id }
    fun lessonOf(u: Unite): Lesson? = lessons.firstOrNull { l -> l.units.any { it.id == u.id } }
    fun unitOf(c: Card): Unite? = allUnits().firstOrNull { u -> u.cards.any { it.id == c.id } }

    // ---------- Değişiklikler ----------

    fun addLesson(name: String, emoji: String, color: Int): Lesson {
        val l = Lesson(newId(), name, emoji, color)
        lessons.add(l)
        save()
        return l
    }

    fun deleteLesson(l: Lesson) {
        lessons.remove(l)
        sessions.removeAll { it.lessonId == l.id }
        save()
    }

    fun addUnit(l: Lesson, name: String, emoji: String, color: Int): Unite {
        val u = Unite(newId(), name, emoji, color)
        l.units.add(u)
        save()
        return u
    }

    fun deleteUnit(u: Unite) {
        lessonOf(u)?.units?.remove(u)
        sessions.removeAll { it.unitId == u.id }
        save()
    }

    fun moveUnit(u: Unite, to: Lesson) {
        val from = lessonOf(u) ?: return
        if (from.id == to.id) return
        from.units.remove(u)
        to.units.add(u)
        // Bu ünitenin çalışma geçmişi yeni derse taşınır
        val moved = sessions.filter { it.unitId == u.id }
        sessions.removeAll { it.unitId == u.id }
        moved.forEach { sessions.add(Session(to.id, it.unitId, it.name, it.date, it.known, it.unknown, it.mode)) }
        save()
    }

    fun addCard(u: Unite, q: String, a: String) {
        u.cards.add(Card(newId(), q, a))
        save()
    }

    fun record(c: Card, ok: Boolean) {
        if (ok) c.known++ else c.unknown++
        c.last = if (ok) 1 else 2
        c.lastSeen = System.currentTimeMillis()
        save()
    }

    fun addSession(lessonId: Long, unitId: Long, name: String, known: Int, unknown: Int, mode: String) {
        sessions.add(Session(lessonId, unitId, name, System.currentTimeMillis(), known, unknown, mode))
        save()
    }

    fun resetStats() {
        allCards().forEach { resetCard(it) }
        sessions.clear()
        save()
    }

    fun resetUnitStats(u: Unite) {
        u.cards.forEach { resetCard(it) }
        sessions.removeAll { it.unitId == u.id }
        save()
    }

    private fun resetCard(c: Card) {
        c.known = 0; c.unknown = 0; c.last = 0; c.lastSeen = 0L
    }

    // ---------- İstatistik yardımcıları ----------

    fun dayKey(t: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    /** Bugün (ya da dün) dahil, arka arkaya çalışılan gün sayısı. */
    fun streak(): Int {
        val days = sessions.map { dayKey(it.date) }.toSet()
        if (days.isEmpty()) return 0
        val cal = Calendar.getInstance()
        if (!days.contains(dayKey(cal.timeInMillis))) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (days.contains(dayKey(cal.timeInMillis))) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    fun todayCount(): Int {
        val today = dayKey(System.currentTimeMillis())
        return sessions.filter { dayKey(it.date) == today }.sumOf { it.known + it.unknown }
    }

    /** Son 7 günün (bugün en sonda) çalışılan kart sayıları. */
    fun lastSevenDays(): IntArray {
        val out = IntArray(7)
        val cal = Calendar.getInstance()
        for (i in 6 downTo 0) {
            val key = dayKey(cal.timeInMillis)
            out[i] = sessions.filter { dayKey(it.date) == key }.sumOf { it.known + it.unknown }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return out
    }

    fun overallPercent(): Int? = percentOf(allCards())

    // ---------- Kaydetme / yükleme ----------

    private fun cardJson(c: Card) = JSONObject().put("id", c.id).put("q", c.q).put("a", c.a)
        .put("k", c.known).put("u", c.unknown).put("l", c.last).put("t", c.lastSeen)

    private fun unitJson(u: Unite): JSONObject {
        val ca = JSONArray()
        u.cards.forEach { ca.put(cardJson(it)) }
        return JSONObject().put("id", u.id).put("name", u.name).put("emoji", u.emoji).put("color", u.color).put("cards", ca)
    }

    fun save() {
        val f = file ?: return
        val o = JSONObject()
        o.put("version", 3)
        o.put("nextId", nextId)
        o.put("userName", userName)
        o.put("askedName", askedName)
        val la = JSONArray()
        lessons.forEach { l ->
            val ua = JSONArray()
            l.units.forEach { ua.put(unitJson(it)) }
            la.put(JSONObject().put("id", l.id).put("name", l.name).put("emoji", l.emoji).put("color", l.color).put("units", ua))
        }
        o.put("lessons", la)
        val sa = JSONArray()
        sessions.forEach { s ->
            sa.put(
                JSONObject().put("lid", s.lessonId).put("uid", s.unitId).put("un", s.name).put("d", s.date)
                    .put("k", s.known).put("w", s.unknown).put("m", s.mode)
            )
        }
        o.put("sessions", sa)
        val tmp = File(f.parentFile, "data.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(o.toString())
            tmp.delete()
        }
    }

    private fun readUnit(uo: JSONObject, fallbackColor: Int): Unite {
        val u = Unite(
            uo.getLong("id"), uo.getString("name"),
            uo.optString("emoji", "📘").ifBlank { "📘" }, uo.optInt("color", fallbackColor)
        )
        val ca = uo.optJSONArray("cards") ?: JSONArray()
        for (j in 0 until ca.length()) {
            val c = ca.getJSONObject(j)
            u.cards.add(
                Card(
                    c.getLong("id"), c.getString("q"), c.getString("a"),
                    c.optInt("k"), c.optInt("u"), c.optInt("l"), c.optLong("t")
                )
            )
        }
        return u
    }

    private fun load(f: File) {
        val o = JSONObject(f.readText())
        nextId = o.optLong("nextId", 1L)
        userName = o.optString("userName", "")
        askedName = o.optBoolean("askedName", false)
        lessons.clear()
        sessions.clear()

        val la = o.optJSONArray("lessons")
        if (la != null) {
            for (i in 0 until la.length()) {
                val lo = la.getJSONObject(i)
                val l = Lesson(lo.getLong("id"), lo.getString("name"), lo.optString("emoji", "📚").ifBlank { "📚" }, lo.optInt("color", i))
                val ua = lo.optJSONArray("units") ?: JSONArray()
                for (j in 0 until ua.length()) l.units.add(readUnit(ua.getJSONObject(j), j))
                lessons.add(l)
            }
        } else {
            // Sürüm 2'den geçiş: eski üniteler "Derslerim" adlı bir dersin altına alınır
            val ua = o.optJSONArray("units") ?: JSONArray()
            val l = Lesson(nextId++, "Derslerim", "📚", 0)
            for (j in 0 until ua.length()) l.units.add(readUnit(ua.getJSONObject(j), j))
            if (l.units.isNotEmpty()) lessons.add(l)
        }

        val defaultLesson = lessons.firstOrNull()?.id ?: -1L
        val sa = o.optJSONArray("sessions") ?: JSONArray()
        for (i in 0 until sa.length()) {
            val s = sa.getJSONObject(i)
            sessions.add(
                Session(
                    s.optLong("lid", defaultLesson), s.optLong("uid", -1L), s.getString("un"), s.getLong("d"),
                    s.optInt("k"), s.optInt("w"), s.optString("m", "kart")
                )
            )
        }
    }
}

/** İlk açılışta hazır gelen dersler ve örnek ünite. */
object Seed {
    fun fill() {
        val names = listOf(
            Triple("Türkçe", "📖", 1),
            Triple("Matematik", "➗", 4),
            Triple("Fen Bilimleri", "🔬", 2),
            Triple("Sosyal Bilgiler", "🌍", 5),
            Triple("İngilizce", "🇬🇧", 6)
        )
        names.forEach { (n, e, c) -> Repo.lessons.add(Lesson(Repo.newId(), n, e, c)) }
        val sosyal = Repo.lessons[3]
        val u = Unite(Repo.newId(), "Millî Mücadele", "🇹🇷", 5)
        DATA.forEach { u.cards.add(Card(Repo.newId(), it.first, it.second)) }
        sosyal.units.add(u)
    }

    private val DATA = listOf(
        "Mustafa Kemal ne zaman ve nerede doğdu?" to "1881 yılında Selanik'te doğdu.",
        "Mustafa Kemal ilk olarak hangi okullarda okudu?" to "Mahalle Mektebi ve Şemsi Efendi İlkokulu'nda.",
        "Mustafa Kemal'in askerî eğitim aldığı okullar hangileridir?" to "Selanik Askerî Rüştiyesi, Manastır Askerî İdadisi, İstanbul Harp Okulu ve İstanbul Harp Akademisi.",
        "Mustafa Kemal askerî eğitimini bitirip Osmanlı ordusuna ne zaman katıldı?" to "1905 yılında.",
        "Eski adıyla “Mektep” bugün neye karşılık gelir?" to "Okul",
        "Eski adıyla “Rüştiye” bugün neye karşılık gelir?" to "Ortaokul",
        "Eski adıyla “İdadi” bugün neye karşılık gelir?" to "Lise",
        "Eski adıyla “Akademi” bugün neye karşılık gelir?" to "Üniversite (yüksekokul)",
        "Mustafa Kemal ilk olarak nerede ve kime karşı savaştı?" to "1911'de Trablusgarp'ta İtalyanlara karşı.",
        "I. Dünya Savaşı hangi yıllar arasında oldu?" to "1914 – 1918 yılları arasında.",
        "Halk Mustafa Kemal'e neden “Anafartalar Kahramanı” dedi?" to "Çanakkale Savaşı'nda kazandığı zaferler yüzünden.",
        "I. Dünya Savaşı'nda Osmanlı Devleti'nin ortağı kimdi?" to "Almanya. Almanya kaybedince Osmanlı da savaşı kaybetmiş sayıldı.",
        "Sömürgeci Avrupa devletleri o yıllarda ne karar almıştı?" to "Osmanlı topraklarını aralarında paylaşma kararı.",
        "Mondros Ateşkes Antlaşması ne zaman imzalandı?" to "30 Ekim 1918'de.",
        "Mondros Antlaşması'na göre neler oldu?" to "Bağımsızlığımız elimizden alındı, ordumuz dağıtıldı (terhis edildi) ve topraklarımız işgal edildi.",
        "Topraklarımızı kimler işgal etti?" to "İngilizler, İtalyanlar, Fransızlar, Yunanlar ve Ermeniler.",
        "Mustafa Kemal işgaller olunca hangi sözü söyledi?" to "“Geldikleri gibi giderler!” Bu söz, umudunu kaybetmediğini ve kararlı olduğunu gösterir.",
        "Millî Mücadele nasıl ve ne zaman başladı?" to "19 Mayıs 1919'da Mustafa Kemal'in Bandırma Vapuru ile Samsun'a çıkmasıyla.",
        "Millî Mücadele ne ile fiilen sona erdi?" to "11 Ekim 1922'de imzalanan Mudanya Ateşkes Antlaşması (Mütarekesi) ile.",
        "Millî Mücadele sonunda ne kuruldu?" to "Halkın büyük fedakârlıklarıyla Türkiye Cumhuriyeti kuruldu."
    )
}
