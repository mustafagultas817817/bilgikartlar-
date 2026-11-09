package com.bilgikartlari.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Ana ekran: dersler listesi + Pofi'li karşılama. */
class MainActivity : AppCompatActivity() {
    private lateinit var adapter: RowAdapter
    private lateinit var empty: View

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = readUri(uri)
        if (text.isNullOrBlank()) toast("Dosya okunamadı") else ImportActivity.start(this, text, null, null)
    }

    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use {
                it.write(Importer.toJson(withStats = true).toString(2).toByteArray(Charsets.UTF_8))
            }
            toast("Yedek kaydedildi")
        } catch (e: Exception) {
            toast("Yedek kaydedilemedi")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupToolbar("Bilgi Kartları", false)

        empty = findViewById(R.id.empty)
        val rv = findViewById<RecyclerView>(R.id.list)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = RowAdapter(onClick = { openLesson(it as Lesson) }, onLong = { lessonMenu(it as Lesson) })
        rv.adapter = adapter

        findViewById<View>(R.id.fab).setOnClickListener { addMenu() }

        if (!Repo.askedName) {
            Repo.askedName = true
            Repo.save()
            askName(first = true)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(Repo.lessons.map { Row.of(it) })
        empty.visibility = if (Repo.lessons.isEmpty()) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.sectionTitle).text =
            if (Repo.lessons.isEmpty()) "Derslerim" else "Derslerim (${Repo.lessons.size})"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (hour) {
            in 5..11 -> "Günaydın"
            in 12..17 -> "İyi günler"
            else -> "İyi akşamlar"
        }
        val name = Repo.userName.trim()
        findViewById<TextView>(R.id.heroHello).text = if (name.isEmpty()) "$hello! 👋" else "$hello, $name! 👋"
        findViewById<TextView>(R.id.heroSub).text = homeLine()
        findViewById<ImageView>(R.id.heroPofi).setImageResource(if (Repo.todayCount() > 0) Mood.CHEER.res else Mood.HAPPY.res)

        findViewById<TextView>(R.id.chipStreak).text = "🔥 ${Repo.streak()} gün"
        findViewById<TextView>(R.id.chipToday).text = "📝 ${Repo.todayCount()} kart"
        findViewById<TextView>(R.id.chipSuccess).text = "⭐ " + (Repo.overallPercent()?.let { "%$it" } ?: "–")
    }

    private fun openLesson(l: Lesson) {
        startActivity(Intent(this, LessonActivity::class.java).putExtra(LessonActivity.EXTRA_LESSON, l.id))
    }

    private fun addMenu() {
        sheet(
            "Ne eklemek istersin?", listOf(
                SheetItem("📋", "Panodan içe aktar", "Claude'dan kopyaladığın listeyi yapıştır (ders ve ünite otomatik)") { pasteImport() },
                SheetItem("📂", "Dosyadan içe aktar", ".json ya da .txt kart dosyası") { openFile.launch(arrayOf("*/*")) },
                SheetItem("📚", "Yeni ders", "ör. Fen Bilimleri, Türkçe") { newLesson() },
                SheetItem("➕", "Yeni ünite", "Bir dersin altına ünite ekle") { newUnit() }
            )
        )
    }

    private fun pasteImport() {
        val text = clipboardText()
        if (text.isNullOrBlank()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Pano boş")
                .setMessage("Önce Claude'un hazırladığı kart listesini kopyala, sonra tekrar dene.")
                .setPositiveButton("Tamam", null)
                .show()
            return
        }
        ImportActivity.start(this, text, null, null)
    }

    private fun newLesson() {
        itemDialog("Yeni ders", false, null, null, null, null) { name, emoji, color, _ ->
            val l = Repo.addLesson(name, emoji, color)
            refresh()
            openLesson(l)
        }
    }

    private fun newUnit() {
        if (Repo.lessons.isEmpty()) {
            toast("Önce bir ders ekle")
            return
        }
        itemDialog("Yeni ünite", true, null, null, null, Repo.lessons.first().id) { name, emoji, color, lid ->
            val l = Repo.lesson(lid ?: return@itemDialog) ?: return@itemDialog
            val u = Repo.addUnit(l, name, emoji, color)
            startActivity(Intent(this, UnitActivity::class.java).putExtra(UnitActivity.EXTRA_UNIT, u.id))
        }
    }

    private fun lessonMenu(l: Lesson) {
        sheet(
            "${l.emoji}  ${l.name}", listOf(
                SheetItem("✏️", "Düzenle", "Ad, simge ve renk") { editLesson(this, l) { refresh() } },
                SheetItem("🗑️", "Sil", "${l.units.size} ünite ve ${l.cards().size} kart silinir") {
                    confirm("Ders silinsin mi?", "\"${l.name}\" dersi, ${l.units.size} ünitesi ve ${l.cards().size} kartı silinecek.") {
                        Repo.deleteLesson(l)
                        refresh()
                    }
                }
            )
        )
    }

    private fun askName(first: Boolean) {
        inputDialog(if (first) "Hoş geldin! Adın ne?" else "Adın", "Adın", Repo.userName) {
            Repo.userName = it
            Repo.save()
            refresh()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_stats -> startActivity(Intent(this, StatsActivity::class.java))
            R.id.action_name -> askName(first = false)
            R.id.action_backup -> {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                saveBackup.launch("bilgi-kartlari-yedek-$day.json")
            }
            R.id.action_restore -> openFile.launch(arrayOf("*/*"))
            R.id.action_help -> MaterialAlertDialogBuilder(this)
                .setTitle("Nasıl kullanılır?")
                .setMessage(HELP_TEXT)
                .setPositiveButton("Tamam", null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}

fun editLesson(ctx: Context, l: Lesson, done: () -> Unit) {
    ctx.itemDialog("Dersi düzenle", false, l.name, l.emoji, l.color, null) { name, emoji, color, _ ->
        l.name = name; l.emoji = emoji; l.color = color
        Repo.save()
        done()
    }
}

fun shareUnit(ctx: Context, u: Unite) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, u.name)
        putExtra(Intent.EXTRA_TEXT, Importer.toText(u))
    }
    ctx.startActivity(Intent.createChooser(send, "Üniteyi paylaş"))
}

/** Ders ya da ünite satırı için ortak görünüm verisi. */
class Row(
    val item: Any,
    val emoji: String,
    val name: String,
    val sub: String,
    val color: Int,
    val percent: Int?,
    val wrong: Int
) {
    companion object {
        fun of(l: Lesson): Row {
            val cards = l.cards()
            val sub = if (l.units.isEmpty()) "Henüz ünite yok" else "${l.units.size} ünite · ${cards.size} kart"
            return Row(l, l.emoji, l.name, sub, l.colorInt(), l.successPercent(), l.wrongCards().size)
        }

        fun of(u: Unite): Row {
            val p = u.successPercent()
            return Row(u, u.emoji, u.name, "${u.cards.size} kart" + if (p == null) " · yeni" else "", u.colorInt(), p, u.wrongCards().size)
        }
    }
}

class RowAdapter(
    private val onClick: (Any) -> Unit,
    private val onLong: (Any) -> Unit
) : RecyclerView.Adapter<RowAdapter.VH>() {
    private var items = listOf<Row>()

    fun submit(list: List<Row>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val emoji: TextView = v.findViewById(R.id.emoji)
        val name: TextView = v.findViewById(R.id.name)
        val sub: TextView = v.findViewById(R.id.sub)
        val badge: TextView = v.findViewById(R.id.badge)
        val ring: CircularProgressIndicator = v.findViewById(R.id.ring)
        val pct: TextView = v.findViewById(R.id.pct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_unit, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        val ctx = h.itemView.context
        h.emoji.text = r.emoji
        h.emoji.background = circle(soft(r.color))
        h.name.text = r.name
        h.sub.text = r.sub
        if (r.wrong > 0) {
            h.badge.visibility = View.VISIBLE
            h.badge.text = "🔁 ${r.wrong} tekrar"
            h.badge.setTextColor(ctx.col(R.color.unknown))
            h.badge.background = rounded(ctx.col(R.color.unknown_soft), ctx.dpf(100f))
        } else {
            h.badge.visibility = View.GONE
        }
        h.ring.setIndicatorColor(r.color)
        h.ring.trackColor = soft(r.color)
        h.ring.setProgressCompat(r.percent ?: 0, false)
        h.pct.text = if (r.percent == null) "–" else "%${r.percent}"
        h.itemView.setOnClickListener { onClick(r.item) }
        h.itemView.setOnLongClickListener { onLong(r.item); true }
    }
}
