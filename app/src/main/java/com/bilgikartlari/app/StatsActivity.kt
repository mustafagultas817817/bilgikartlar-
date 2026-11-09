package com.bilgikartlari.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Calendar
import java.util.Locale

class StatsActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        setupToolbar("İstatistikler", true)
        root = findViewById(R.id.content)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        root.removeAllViews()
        val allCards = Repo.allCards()
        val studied = allCards.count { it.answered > 0 }
        val success = Repo.overallPercent()?.let { "%$it" } ?: "–"
        val toRepeat = allCards.count { it.last == 2 }
        val lastDate = Repo.sessions.maxOfOrNull { it.date }
        val op = Repo.overallPercent()
        val line = when {
            op == null -> "Henüz çalışma yok. İlk kartı birlikte çevirelim!"
            op >= 80 -> "%$op başarı! Sen tam bir bilgi kuşusun 🦉"
            op >= 50 -> "%$op başarı. Tekrarlarla daha da yükselir!"
            else -> "%$op başarı. Tekrar ettikçe göreceksin, artacak!"
        }
        root.addView(pofiSays(if (op != null && op >= 70) Mood.CHEER else Mood.HAPPY, line))

        row(tile("⭐", success, "Genel başarı"), tile("🔥", "${Repo.streak()} gün", "Çalışma serisi"))
        row(tile("📚", "$studied/${allCards.size}", "Çalışılan kart"), tile("🔁", toRepeat.toString(), "Tekrar bekleyen"))
        row(tile("📝", Repo.todayCount().toString(), "Bugün çalışılan"), tile("🗓️", Repo.sessions.size.toString(), "Toplam çalışma"))
        if (lastDate != null) note("Son çalışma: ${formatDate(lastDate)}")

        header("Son 7 gün")
        val chartBox = cardBox()
        chartBox.addView(WeekChart(this).apply { values = Repo.lastSevenDays() })

        header("Derslere göre")
        if (Repo.lessons.isEmpty()) note("Henüz ders yok.")
        Repo.lessons.forEach { l ->
            val p = l.successPercent()
            val c = l.colorInt()
            val count = Repo.sessions.count { it.lessonId == l.id }
            val box = cardBox()
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(tv(l.emoji, 20f, R.color.ink).apply {
                gravity = Gravity.CENTER
                background = circle(soft(c))
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            texts.addView(tv(l.name, 15f, R.color.ink, bold = true))
            texts.addView(tv(
                "${l.units.size} ünite · ${l.cards().size} kart" + (if (count > 0) " · $count çalışma" else "") +
                        (if (l.wrongCards().isNotEmpty()) " · ${l.wrongCards().size} tekrar" else ""),
                12.5f, R.color.muted
            ))
            head.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(tv(if (p == null) "–" else "%$p", 17f, R.color.ink, bold = true).apply { setTextColor(c) })
            box.addView(head)
            // Ünite çubukları
            l.units.forEach { u ->
                val up = u.successPercent()
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, 0)
                }
                line.addView(tv("${u.emoji} ${u.name}", 12.5f, R.color.muted).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val bar = LinearProgressIndicator(this).apply {
                    max = 100
                    trackThickness = dp(6)
                    trackCornerRadius = dp(3)
                    setIndicatorColor(c)
                    trackColor = soft(c)
                    setProgressCompat(up ?: 0, false)
                }
                line.addView(bar, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
                line.addView(tv(if (up == null) "–" else "%$up", 12.5f, R.color.ink, bold = true).apply {
                    gravity = Gravity.END
                }, LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT))
                box.addView(line)
            }
        }

        header("En çok zorlandığın kartlar")
        val unitOf = HashMap<Long, Unite>()
        Repo.allUnits().forEach { u -> u.cards.forEach { unitOf[it.id] = u } }
        val hard = allCards.filter { it.unknown > 0 }
            .sortedWith(compareByDescending<Card> { it.unknown.toFloat() / it.answered }.thenByDescending { it.unknown })
            .take(10)
        if (hard.isEmpty()) note("Henüz yanlış bilinen kart yok. 👍")
        hard.forEach { c ->
            val box = cardBox()
            box.addView(tv(c.q, 14.5f, R.color.ink, bold = true))
            box.addView(tv(c.a, 13f, R.color.muted).apply { setPadding(0, dp(2), 0, dp(6)) })
            val u = unitOf[c.id]
            val l = u?.let { Repo.lessonOf(it) }
            box.addView(tv("✗ ${c.unknown}   ✓ ${c.known}   ·   ${l?.name ?: ""} › ${u?.name ?: ""}", 12f, R.color.unknown, bold = true))
        }

        header("Son çalışmalar")
        val recent = Repo.sessions.sortedByDescending { it.date }.take(15)
        if (recent.isEmpty()) note("Henüz çalışma yapılmadı.")
        recent.forEach { s ->
            val total = s.known + s.unknown
            val p = if (total == 0) 0 else Math.round(s.known * 100f / total)
            val box = cardBox()
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(tv((if (s.mode == "test") "❓ Test · " else "🃏 Kart · ") + s.name, 14.5f, R.color.ink, bold = true))
            texts.addView(tv((Repo.lesson(s.lessonId)?.let { it.name + "  ·  " } ?: "") + "${formatDate(s.date)}  ·  ✓ ${s.known}  ✗ ${s.unknown}", 12.5f, R.color.muted))
            line.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val pc = if (p >= 70) R.color.known else if (p >= 40) R.color.ink else R.color.unknown
            line.addView(tv("%$p", 16f, pc, bold = true))
            box.addView(line)
        }
    }

    private fun tv(s: String, size: Float, colorId: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(col(colorId))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun header(s: String) {
        root.addView(tv(s, 18f, R.color.ink, bold = true).apply { setPadding(dp(8), dp(22), dp(8), dp(6)) })
    }

    private fun note(s: String) {
        root.addView(tv(s, 13.5f, R.color.muted).apply { setPadding(dp(8), dp(6), dp(8), dp(4)) })
    }

    private fun cardBox(): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = dpf(18f)
            cardElevation = dpf(1f)
            strokeWidth = 0
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        card.addView(inner)
        root.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(4), dp(5), dp(4), dp(5))
        })
        return inner
    }

    private fun tile(icon: String, value: String, label: String): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dpf(18f)
            cardElevation = dpf(1f)
            strokeWidth = 0
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        inner.addView(tv(icon, 20f, R.color.ink))
        inner.addView(tv(value, 22f, R.color.primary, bold = true).apply {
            maxLines = 1
            setPadding(0, dp(4), 0, 0)
        })
        inner.addView(tv(label, 12f, R.color.muted))
        card.addView(inner)
        return card
    }

    private fun row(a: View, b: View) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(a, b).forEach {
            r.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
        }
        root.addView(r)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.stats, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_reset) {
            confirm(
                "Tüm istatistikler sıfırlansın mı?",
                "Çalışma geçmişi ve Bildim/Bilemedim sayıları silinecek. Kartlar silinmez.",
                "Sıfırla"
            ) {
                Repo.resetStats()
                build()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

/** Son 7 günde çalışılan kart sayısını gösteren basit sütun grafik. */
class WeekChart(context: Context) : View(context) {
    var values: IntArray = IntArray(7)
        set(v) {
            field = v
            invalidate()
        }

    private val days: Array<String> = run {
        val names = arrayOf("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt")
        val cal = Calendar.getInstance()
        val out = Array(7) { "" }
        for (i in 6 downTo 0) {
            out[i] = if (i == 6) "Bugün" else names[cal.get(Calendar.DAY_OF_WEEK) - 1]
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        out
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.poppins_medium)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (170 * resources.displayMetrics.density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val sp = d * resources.configuration.fontScale
        val labelH = 24 * d
        val valueH = 20 * d
        val bottom = height - labelH
        val top = valueH
        var maxV = 1
        for (v in values) if (v > maxV) maxV = v
        val slot = width / 7f
        val bw = slot * 0.46f

        for (i in 0 until 7) {
            val cx = slot * i + slot / 2
            val h = if (values[i] == 0) 4 * d else maxOf(6 * d, (bottom - top) * values[i] / maxV)
            barPaint.color = when {
                values[i] == 0 -> context.col(R.color.outline)
                i == 6 -> context.col(R.color.accent)
                else -> context.col(R.color.primary)
            }
            canvas.drawRoundRect(cx - bw / 2, bottom - h, cx + bw / 2, bottom, 8 * d, 8 * d, barPaint)

            textPaint.textSize = 11 * sp
            textPaint.color = context.col(R.color.muted)
            canvas.drawText(days[i], cx, height - 6 * d, textPaint)
            if (values[i] > 0) {
                textPaint.color = context.col(R.color.ink)
                textPaint.textSize = 12 * sp
                canvas.drawText(String.format(Locale.ROOT, "%d", values[i]), cx, bottom - h - 6 * d, textPaint)
            }
        }
    }
}
