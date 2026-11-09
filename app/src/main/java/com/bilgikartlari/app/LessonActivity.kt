package com.bilgikartlari.app

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Ders ekranı: üniteler listesi + tüm üniteleri karışık çalışma / test. */
class LessonActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_LESSON = "lessonId"
    }

    private var lesson: Lesson? = null
    private lateinit var adapter: RowAdapter

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val l = lesson
        if (uri == null || l == null) return@registerForActivityResult
        val text = readUri(uri)
        if (text.isNullOrBlank()) toast("Dosya okunamadı") else ImportActivity.start(this, text, l.id, null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val l = Repo.lesson(intent.getLongExtra(EXTRA_LESSON, -1L))
        if (l == null) {
            finish()
            return
        }
        lesson = l
        setContentView(R.layout.activity_lesson)
        setupToolbar("", true)

        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = RowAdapter(onClick = { openUnit(it as Unite) }, onLong = { unitMenu(it as Unite) })
        rv.adapter = adapter

        findViewById<View>(R.id.btnMixStudy).setOnClickListener { study(wrongOnly = false) }
        findViewById<View>(R.id.btnMixWrong).setOnClickListener { study(wrongOnly = true) }
        findViewById<View>(R.id.btnMixQuiz).setOnClickListener {
            val cur = lesson ?: return@setOnClickListener
            if (cur.cards().size < 4) toast("Test için bu derste en az 4 kart olmalı")
            else startActivity(Intent(this, QuizActivity::class.java).putExtra(EXTRA_LESSON, cur.id).putExtra(UnitActivity.EXTRA_UNIT, -1L))
        }
        findViewById<View>(R.id.btnImport).setOnClickListener { importMenu() }
        findViewById<View>(R.id.fab).setOnClickListener { newUnit() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val l = lesson ?: return
        if (Repo.lesson(l.id) == null) {
            finish()
            return
        }
        val c = l.colorInt()
        val cards = l.cards()
        val p = l.successPercent()
        val wrong = l.wrongCards().size

        findViewById<LinearLayout>(R.id.header).background = gradient(c, dpf(26f))
        findViewById<TextView>(R.id.hEmoji).text = l.emoji
        findViewById<TextView>(R.id.hName).text = l.name
        findViewById<TextView>(R.id.hInfo).text =
            "${l.units.size} ünite · ${cards.size} kart" + (if (p != null) " · %$p başarı" else "")
        findViewById<LinearProgressIndicator>(R.id.hBar).setProgressCompat(p ?: 0, true)

        val hasCards = cards.isNotEmpty()
        findViewById<View>(R.id.mixRow).visibility = if (hasCards) View.VISIBLE else View.GONE
        tonal(findViewById(R.id.btnMixStudy), c)
        tonal(findViewById(R.id.btnMixQuiz), c)
        val bw = findViewById<MaterialButton>(R.id.btnMixWrong)
        bw.visibility = if (wrong > 0) View.VISIBLE else View.GONE
        bw.text = "Bu dersin tekrarları ($wrong)"
        tonal(bw, col(R.color.unknown))

        findViewById<TextView>(R.id.unitsTitle).text = "Üniteler (${l.units.size})"
        adapter.submit(l.units.map { Row.of(it) })
        findViewById<View>(R.id.empty).visibility = if (l.units.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun tonal(b: MaterialButton, c: Int) {
        b.backgroundTintList = ColorStateList.valueOf(soft(c, 0.85f))
        b.setTextColor(darker(c, 0.15f))
        b.iconTint = ColorStateList.valueOf(darker(c, 0.15f))
    }

    private fun study(wrongOnly: Boolean) {
        val l = lesson ?: return
        startActivity(
            Intent(this, StudyActivity::class.java)
                .putExtra(EXTRA_LESSON, l.id)
                .putExtra(UnitActivity.EXTRA_UNIT, -1L)
                .putExtra(StudyActivity.EXTRA_WRONG_ONLY, wrongOnly)
        )
    }

    private fun openUnit(u: Unite) {
        startActivity(Intent(this, UnitActivity::class.java).putExtra(UnitActivity.EXTRA_UNIT, u.id))
    }

    private fun newUnit() {
        val l = lesson ?: return
        itemDialog("Yeni ünite", true, null, null, l.color, l.id) { name, emoji, color, lid ->
            val target = Repo.lesson(lid ?: l.id) ?: l
            val u = Repo.addUnit(target, name, emoji, color)
            openUnit(u)
        }
    }

    private fun unitMenu(u: Unite) {
        sheet(
            "${u.emoji}  ${u.name}", listOf(
                SheetItem("✏️", "Düzenle", "Ad, ders, simge ve renk") { editUnit(u) },
                SheetItem("📤", "Paylaş", "Kartları metin olarak gönder") { shareUnit(this, u) },
                SheetItem("🗑️", "Sil", "${u.cards.size} kart silinir") {
                    confirm("Ünite silinsin mi?", "\"${u.name}\" ve içindeki ${u.cards.size} kart silinecek.") {
                        Repo.deleteUnit(u)
                        refresh()
                    }
                }
            )
        )
    }

    private fun editUnit(u: Unite) {
        val l = lesson ?: return
        itemDialog("Üniteyi düzenle", true, u.name, u.emoji, u.color, l.id) { name, emoji, color, lid ->
            u.name = name; u.emoji = emoji; u.color = color
            Repo.save()
            Repo.lesson(lid ?: l.id)?.let { Repo.moveUnit(u, it) }
            refresh()
        }
    }

    private fun importMenu() {
        val l = lesson ?: return
        sheet(
            "${l.name} dersine ekle", listOf(
                SheetItem("📋", "Panodan yapıştır", "Claude'dan kopyaladığın listeyi ekle") {
                    val t = clipboardText()
                    if (t.isNullOrBlank()) toast("Pano boş. Önce kart listesini kopyala.")
                    else ImportActivity.start(this, t, l.id, null)
                },
                SheetItem("📂", "Dosyadan", ".json ya da .txt kart dosyası") { openFile.launch(arrayOf("*/*")) },
                SheetItem("➕", "Boş ünite oluştur", "Kartları kendin ekle") { newUnit() }
            )
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.lesson, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val l = lesson ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.action_edit -> editLesson(this, l) { refresh() }
            R.id.action_delete -> confirm("Ders silinsin mi?", "\"${l.name}\" dersi, ${l.units.size} ünitesi ve ${l.cards().size} kartı silinecek.") {
                Repo.deleteLesson(l)
                finish()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
