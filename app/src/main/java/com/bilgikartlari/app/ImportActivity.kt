package com.bilgikartlari.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.core.content.IntentCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/** Panodan, dosyadan ya da başka uygulamadan gelen kartları önizler ve kaydeder. */
class ImportActivity : AppCompatActivity() {
    companion object {
        private const val EXTRA_TEXT_IN = "importText"
        private const val EXTRA_LESSON = "targetLesson"
        private const val EXTRA_UNIT = "targetUnit"

        /** lessonId: kartlar bu derse gider. unitId: kartlar doğrudan bu üniteye gider. */
        fun start(ctx: Context, text: String, lessonId: Long?, unitId: Long?) {
            val i = Intent(ctx, ImportActivity::class.java).putExtra(EXTRA_TEXT_IN, text)
            if (lessonId != null) i.putExtra(EXTRA_LESSON, lessonId)
            if (unitId != null) i.putExtra(EXTRA_UNIT, unitId)
            ctx.startActivity(i)
        }
    }

    private var units: List<ImportUnit> = emptyList()
    private var targetUnit: Unite? = null
    private var targetLesson: Lesson? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        setupToolbar("Kartları içe aktar", true)

        targetUnit = if (intent.hasExtra(EXTRA_UNIT)) Repo.unit(intent.getLongExtra(EXTRA_UNIT, -1L)) else null
        targetLesson = if (intent.hasExtra(EXTRA_LESSON)) Repo.lesson(intent.getLongExtra(EXTRA_LESSON, -1L)) else null

        val text = incomingText()
        units = if (text.isNullOrBlank()) emptyList() else Importer.parse(text)

        // Bir üniteye yapıştırılıyorsa tüm kartlar o üniteye gider
        val tu = targetUnit
        if (tu != null && units.isNotEmpty()) {
            val all = ImportUnit(tu.name, Repo.lessonOf(tu)?.name, tu.emoji, tu.color)
            units.forEach { all.cards.addAll(it.cards) }
            units = listOf(all)
        }
        Importer.resolveDefaults(units, targetLesson)

        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { finish() }
        val btn = findViewById<MaterialButton>(R.id.btnImport)
        if (units.isEmpty()) {
            showEmpty()
            btn.isEnabled = false
        } else {
            showPreview()
            btn.text = "İçe aktar (${units.sumOf { it.cards.size }})"
            btn.setOnClickListener { doImport() }
        }
    }

    private fun incomingText(): String? {
        intent.getStringExtra(EXTRA_TEXT_IN)?.let { return it }
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { readUri(it) }
            Intent.ACTION_VIEW -> intent.data?.let { readUri(it) }
            else -> null
        }
    }

    private fun content(): LinearLayout = findViewById(R.id.content)

    private fun tv(s: String, size: Float, colorId: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(col(colorId))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun showEmpty() {
        val c = content()
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(pofiView(Mood.OOPS, 110).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(32) })
        c.addView(tv("Kart bulunamadı", 20f, R.color.ink, bold = true))
        c.addView(tv(
            "Metinde \"Soru | Cevap\" biçiminde satır yok.\n\nClaude'dan gelen listeyi baştan sona kopyaladığından emin ol. Örnek:\n\n" +
                    "@ders: Sosyal Bilgiler\n@ünite: Kültürel Mirasımız\nKültürel miras nedir? | Geçmişten kalan değerlerdir.",
            14f, R.color.muted
        ).apply { setPadding(dp(16), dp(12), dp(16), 0) })
    }

    private fun showPreview() {
        val c = content()
        val total = units.sumOf { it.cards.size }
        val msg = if (units.size == 1) "$total kart buldum! Kontrol et, sonra içe aktar." else "${units.size} ünitede $total kart buldum! Kontrol et, sonra içe aktar."
        c.addView(pofiSays(Mood.CHEER, msg, 64))

        val tu = targetUnit
        units.forEachIndexed { index, iu ->
            val card = MaterialCardView(this).apply {
                radius = dpf(20f)
                cardElevation = dpf(1f)
                strokeWidth = 0
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(16))
            }
            card.addView(inner)

            val colorIdx = iu.color ?: iu.lessonId?.let { Repo.lesson(it)?.color } ?: ((Repo.lessons.size + index) % Palette.COLORS.size)
            val color = Palette.color(colorIdx)

            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(tv(iu.emoji ?: "📘", 24f, R.color.ink).apply {
                gravity = Gravity.CENTER
                background = circle(soft(color))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))

            val info = tv("", 13f, R.color.muted)
            fun updateInfo() {
                val lesson = iu.lessonId?.let { Repo.lesson(it) }
                val existing = tu ?: Importer.findUnit(lesson, iu.name)
                val dup = Importer.duplicateCount(iu, existing)
                info.text = when {
                    existing != null && dup > 0 -> "\"${existing.name}\" ünitesine ${iu.cards.size - dup} yeni kart eklenecek ($dup kart zaten var)"
                    existing != null -> "\"${existing.name}\" ünitesine ${iu.cards.size} kart eklenecek"
                    iu.newLesson -> "Yeni \"${iu.lessonName ?: "Derslerim"}\" dersine yeni ünite olarak ${iu.cards.size} kart eklenecek"
                    lesson != null -> "\"${lesson.name}\" dersine yeni ünite olarak ${iu.cards.size} kart eklenecek"
                    else -> "${iu.cards.size} kart eklenecek"
                }
            }

            if (tu != null) {
                head.addView(tv(tu.name, 17f, R.color.ink, bold = true).apply {
                    setPadding(dp(12), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                inner.addView(head)
            } else {
                val nameField = EditText(this).apply {
                    setText(iu.name)
                    textSize = 17f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    hint = "Ünite adı"
                    doAfterTextChanged {
                        iu.name = it?.toString()?.trim().orEmpty()
                        updateInfo()
                    }
                }
                head.addView(nameField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                })
                inner.addView(head)

                // Ders seçimi
                val extra = if (!iu.lessonName.isNullOrBlank() && Importer.findLesson(iu.lessonName) == null) iu.lessonName else null
                val sp = lessonSpinner(if (iu.newLesson) null else iu.lessonId, extra)
                if (targetLesson != null) sp.isEnabled = false
                sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (position < Repo.lessons.size) {
                            iu.lessonId = Repo.lessons[position].id
                            iu.newLesson = false
                        } else {
                            iu.lessonId = null
                            iu.newLesson = true
                        }
                        updateInfo()
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                inner.addView(tv("Ders", 12.5f, R.color.muted).apply { setPadding(dp(4), dp(10), 0, 0) })
                inner.addView(sp)
            }
            updateInfo()
            inner.addView(info.apply { setPadding(0, dp(8), 0, dp(6)) })

            iu.cards.take(3).forEach { k ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(soft(color, 0.92f), dpf(12f))
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
                row.addView(tv(k.q, 14f, R.color.ink, bold = true))
                row.addView(tv(k.a, 13f, R.color.muted))
                inner.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                })
            }
            if (iu.cards.size > 3) {
                inner.addView(tv("+ ${iu.cards.size - 3} kart daha", 13f, R.color.primary, bold = true).apply {
                    setPadding(dp(4), dp(8), 0, 0)
                })
            }

            c.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), dp(6), dp(4), dp(6))
            })
        }
    }

    private fun doImport() {
        val (added, last) = Importer.importAll(units, targetUnit?.id)
        val total = units.sumOf { it.cards.size }
        toast(if (added == total) "$added kart eklendi 🎉" else "$added yeni kart eklendi (${total - added} zaten vardı)")

        val unitIntent = last?.let { Intent(this, UnitActivity::class.java).putExtra(UnitActivity.EXTRA_UNIT, it.id) }
        val external = !intent.hasExtra(EXTRA_TEXT_IN)
        if (external) {
            // Başka bir uygulamadan gelindi: uygulamayı kendi penceresinde aç
            val stack = TaskStackBuilder.create(this).addNextIntent(Intent(this, MainActivity::class.java))
            if (units.size == 1 && unitIntent != null) stack.addNextIntent(unitIntent)
            stack.startActivities()
        } else if (targetUnit == null && units.size == 1 && unitIntent != null) {
            startActivity(unitIntent)
        }
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
