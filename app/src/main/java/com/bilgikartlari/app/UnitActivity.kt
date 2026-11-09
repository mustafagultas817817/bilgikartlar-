package com.bilgikartlari.app

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class UnitActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_UNIT = "unitId"
    }

    private var unit: Unite? = null
    private lateinit var adapter: CardAdapter
    private lateinit var btnStudy: MaterialButton
    private lateinit var btnQuiz: MaterialButton
    private lateinit var btnWrong: MaterialButton

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val u = unit
        if (uri == null || u == null) return@registerForActivityResult
        val text = readUri(uri)
        if (text.isNullOrBlank()) toast("Dosya okunamadı") else ImportActivity.start(this, text, null, u.id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val u = Repo.unit(intent.getLongExtra(EXTRA_UNIT, -1L))
        if (u == null) {
            finish()
            return
        }
        unit = u
        setContentView(R.layout.activity_unit)
        setupToolbar("", true)

        btnStudy = findViewById(R.id.btnStudy)
        btnQuiz = findViewById(R.id.btnQuiz)
        btnWrong = findViewById(R.id.btnWrong)

        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = CardAdapter { editCard(it) }
        rv.adapter = adapter

        btnStudy.setOnClickListener { open(StudyActivity::class.java, wrongOnly = false) }
        btnWrong.setOnClickListener { open(StudyActivity::class.java, wrongOnly = true) }
        btnQuiz.setOnClickListener {
            if ((unit?.cards?.size ?: 0) < 4) toast("Test için bu ünitede en az 4 kart olmalı")
            else open(QuizActivity::class.java, wrongOnly = false)
        }
        findViewById<View>(R.id.btnAdd).setOnClickListener { addCard() }
        findViewById<View>(R.id.btnBulk).setOnClickListener { importMenu() }
        findViewById<View>(R.id.crumb).setOnClickListener { openLesson() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val u = unit ?: return
        if (Repo.unit(u.id) == null) {
            finish()
            return
        }
        val l = Repo.lessonOf(u)
        val c = u.colorInt()
        val p = u.successPercent()
        val wrong = u.wrongCards().size

        findViewById<LinearLayout>(R.id.header).background = gradient(c, dpf(26f))
        findViewById<TextView>(R.id.crumb).text = if (l != null) "${l.emoji}  ${l.name}  ›" else ""
        findViewById<TextView>(R.id.hEmoji).text = u.emoji
        findViewById<TextView>(R.id.hName).text = u.name
        val studied = u.cards.count { it.answered > 0 }
        findViewById<TextView>(R.id.hInfo).text =
            "${u.cards.size} kart · $studied çalışıldı" + (if (p != null) " · %$p başarı" else "")
        findViewById<LinearProgressIndicator>(R.id.hBar).setProgressCompat(p ?: 0, true)

        btnStudy.backgroundTintList = ColorStateList.valueOf(c)
        btnStudy.isEnabled = u.cards.isNotEmpty()
        tonal(btnQuiz, c)
        btnQuiz.isEnabled = u.cards.isNotEmpty()
        btnWrong.text = if (wrong > 0) "Tekrar ($wrong)" else "Tekrar"
        btnWrong.isEnabled = wrong > 0
        tonal(btnWrong, if (wrong > 0) col(R.color.unknown) else col(R.color.muted))

        findViewById<TextView>(R.id.cardsTitle).text = "Kartlar (${u.cards.size})"
        adapter.submit(u.cards.toList(), c)
        findViewById<View>(R.id.empty).visibility = if (u.cards.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun tonal(b: MaterialButton, c: Int) {
        b.backgroundTintList = ColorStateList.valueOf(soft(c, 0.85f))
        b.setTextColor(darker(c, 0.15f))
        b.iconTint = ColorStateList.valueOf(darker(c, 0.15f))
    }

    private fun openLesson() {
        val u = unit ?: return
        val l = Repo.lessonOf(u) ?: return
        startActivity(
            Intent(this, LessonActivity::class.java)
                .putExtra(LessonActivity.EXTRA_LESSON, l.id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    private fun open(cls: Class<*>, wrongOnly: Boolean) {
        val u = unit ?: return
        if (u.cards.isEmpty()) return
        startActivity(
            Intent(this, cls)
                .putExtra(EXTRA_UNIT, u.id)
                .putExtra(LessonActivity.EXTRA_LESSON, Repo.lessonOf(u)?.id ?: -1L)
                .putExtra(StudyActivity.EXTRA_WRONG_ONLY, wrongOnly)
        )
    }

    private fun addCard() {
        val u = unit ?: return
        cardDialog("Yeni kart") { q, a ->
            Repo.addCard(u, q, a)
            refresh()
        }
    }

    private fun editCard(card: Card) {
        val u = unit ?: return
        cardDialog("Kartı düzenle", card.q, card.a, onDelete = {
            u.cards.remove(card)
            Repo.save()
            refresh()
        }) { q, a ->
            card.q = q
            card.a = a
            Repo.save()
            refresh()
        }
    }

    private fun importMenu() {
        val u = unit ?: return
        sheet(
            "Bu üniteye kart ekle", listOf(
                SheetItem("📋", "Panodan yapıştır", "Claude'dan kopyaladığın listeyi ekle") {
                    val t = clipboardText()
                    if (t.isNullOrBlank()) toast("Pano boş. Önce kart listesini kopyala.")
                    else ImportActivity.start(this, t, null, u.id)
                },
                SheetItem("📂", "Dosyadan", ".json ya da .txt kart dosyası") { openFile.launch(arrayOf("*/*")) },
                SheetItem("✏️", "Elle yaz", "Soru ve cevabı kendin gir") { addCard() }
            )
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.unit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val u = unit ?: return super.onOptionsItemSelected(item)
        when (item.itemId) {
            R.id.action_edit -> {
                val lid = Repo.lessonOf(u)?.id
                itemDialog("Üniteyi düzenle", true, u.name, u.emoji, u.color, lid) { name, emoji, color, newLid ->
                    u.name = name; u.emoji = emoji; u.color = color
                    Repo.save()
                    newLid?.let { id -> Repo.lesson(id)?.let { Repo.moveUnit(u, it) } }
                    refresh()
                }
            }
            R.id.action_share -> shareUnit(this, u)
            R.id.action_reset -> confirm(
                "İstatistik sıfırlansın mı?",
                "Bu ünitedeki Bildim/Bilemedim sayıları ve çalışma geçmişi silinir. Kartlar kalır.",
                "Sıfırla"
            ) {
                Repo.resetUnitStats(u)
                refresh()
            }
            R.id.action_delete -> confirm("Ünite silinsin mi?", "\"${u.name}\" ve içindeki ${u.cards.size} kart silinecek.") {
                Repo.deleteUnit(u)
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

class CardAdapter(private val onClick: (Card) -> Unit) : RecyclerView.Adapter<CardAdapter.VH>() {
    private var items = listOf<Card>()
    private var color = Palette.color(0)

    fun submit(list: List<Card>, unitColor: Int) {
        items = list
        color = unitColor
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val num: TextView = v.findViewById(R.id.num)
        val q: TextView = v.findViewById(R.id.q)
        val a: TextView = v.findViewById(R.id.a)
        val st: TextView = v.findViewById(R.id.st)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val c = items[position]
        val ctx = h.itemView.context
        h.num.text = (position + 1).toString()
        h.num.background = circle(soft(color))
        h.num.setTextColor(darker(color, 0.1f))
        h.q.text = c.q
        h.a.text = c.a
        when {
            c.answered == 0 -> {
                h.st.text = "Yeni"
                h.st.setTextColor(ctx.col(R.color.muted))
                h.st.background = rounded(ctx.col(R.color.surface_variant), ctx.dpf(100f))
            }
            c.last == 2 -> {
                h.st.text = "🔁 Tekrar et   ✓${c.known}  ✗${c.unknown}"
                h.st.setTextColor(ctx.col(R.color.unknown))
                h.st.background = rounded(ctx.col(R.color.unknown_soft), ctx.dpf(100f))
            }
            else -> {
                h.st.text = "✓ Biliyor   ✓${c.known}  ✗${c.unknown}"
                h.st.setTextColor(ctx.col(R.color.known))
                h.st.background = rounded(ctx.col(R.color.known_soft), ctx.dpf(100f))
            }
        }
        h.itemView.setOnClickListener { onClick(c) }
    }
}
