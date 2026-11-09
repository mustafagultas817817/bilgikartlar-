package com.bilgikartlari.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Küçük yardımcılar ----------

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.col(id: Int): Int = ContextCompat.getColor(this, id)
fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun soft(color: Int, amount: Float = 0.86f): Int = ColorUtils.blendARGB(color, Color.WHITE, amount)
fun darker(color: Int, amount: Float = 0.25f): Int = ColorUtils.blendARGB(color, Color.BLACK, amount)

fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

fun rounded(color: Int, radiusPx: Float): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    cornerRadius = radiusPx
    setColor(color)
}

fun gradient(color: Int, radiusPx: Float): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.12f), darker(color, 0.18f))).apply {
        cornerRadius = radiusPx
    }

fun formatDate(t: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(t))

fun View.tick(ok: Boolean) {
    performHapticFeedback(if (ok) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS)
}

fun AppCompatActivity.setupToolbar(title: String, up: Boolean) {
    val tb = findViewById<MaterialToolbar>(R.id.toolbar)
    setSupportActionBar(tb)
    supportActionBar?.title = title
    supportActionBar?.setDisplayHomeAsUpEnabled(up)
}

fun Context.clipboardText(): String? {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()
}

fun Context.readUri(uri: Uri): String? = try {
    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
} catch (e: Exception) {
    null
}

fun Context.selectableBg(view: View) {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
    view.setBackgroundResource(tv.resourceId)
}

// ---------- Pofi (çalışma arkadaşı maskot) ----------

enum class Mood(val res: Int) {
    HAPPY(R.drawable.pofi_happy),
    CHEER(R.drawable.pofi_cheer),
    THINK(R.drawable.pofi_think),
    OOPS(R.drawable.pofi_oops)
}

fun Context.pofiView(mood: Mood, sizeDp: Int): ImageView = ImageView(this).apply {
    setImageResource(mood.res)
    contentDescription = "Pofi"
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Pofi + konuşma balonu satırı. */
fun Context.pofiSays(mood: Mood, text: String, sizeDp: Int = 70): LinearLayout {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        setPadding(dp(8), dp(6), dp(8), dp(4))
    }
    row.addView(pofiView(mood, sizeDp))
    row.addView(TextView(this).apply {
        this.text = text
        textSize = 13.5f
        setTextColor(col(R.color.ink))
        setBackgroundResource(R.drawable.bg_bubble)
        elevation = dpf(2f)
        setPadding(dp(14), dp(10), dp(14), dp(10))
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = dp(6)
        bottomMargin = dp(12)
    })
    return row
}

fun homeLine(): String {
    val today = Repo.todayCount()
    val waiting = Repo.allCards().count { it.last == 2 }
    val st = Repo.streak()
    return when {
        today > 0 && st > 1 -> "$st gündür buradasın, harikasın! Bugün $today kart çalıştın."
        today > 0 -> "Bugün $today kart çalıştın. Çok iyi gidiyorsun!"
        waiting > 0 -> "$waiting kart tekrarını bekliyor. Hadi birlikte bakalım!"
        else -> "Ben Pofi! Bugün birlikte ne öğrenelim?"
    }
}

// ---------- Diyaloglar ----------

private fun Context.box(vararg views: View): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(8), dp(22), 0)
        views.forEach {
            addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

private fun Context.makeField(hintText: String, value: String, multi: Boolean): EditText =
    EditText(this).apply {
        hint = hintText
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                (if (multi) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
        if (multi) maxLines = 6
    }

fun Context.inputDialog(title: String, hintText: String, initial: String = "", onOk: (String) -> Unit) {
    val et = makeField(hintText, initial, false)
    et.setSelection(et.text.length)
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box(et))
        .setPositiveButton("Kaydet") { _, _ -> onOk(et.text.toString().trim()) }
        .setNegativeButton("Vazgeç", null)
        .show()
}

fun Context.confirm(title: String, msg: String, okText: String = "Sil", onOk: () -> Unit) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(msg)
        .setPositiveButton(okText) { _, _ -> onOk() }
        .setNegativeButton("Vazgeç", null)
        .show()
}

/** Kart ekleme / düzenleme. Eklerken "Kaydet ve yeni" ile pencere kapanmadan devam edilir. */
fun Context.cardDialog(
    title: String,
    q: String = "",
    a: String = "",
    onDelete: (() -> Unit)? = null,
    onOk: (String, String) -> Unit
) {
    val qe = makeField("Soru", q, true)
    val ae = makeField("Cevap", a, true)
    val builder = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(box(qe, ae))
        .setPositiveButton("Kaydet", null)
        .setNegativeButton("Vazgeç", null)
    if (onDelete != null) {
        builder.setNeutralButton("Sil") { _, _ -> confirm("Kart silinsin mi?", q) { onDelete() } }
    } else {
        builder.setNeutralButton("Kaydet ve yeni", null)
    }
    val dialog = builder.show()

    fun read(): Pair<String, String>? {
        val qq = qe.text.toString().trim()
        val aa = ae.text.toString().trim()
        if (qq.isEmpty() || aa.isEmpty()) {
            toast("Soru ve cevap boş olamaz")
            return null
        }
        return qq to aa
    }

    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
        val r = read() ?: return@setOnClickListener
        onOk(r.first, r.second)
        dialog.dismiss()
    }
    if (onDelete == null) {
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            val r = read() ?: return@setOnClickListener
            onOk(r.first, r.second)
            qe.setText("")
            ae.setText("")
            qe.requestFocus()
            toast("Kart eklendi")
        }
    }
}

/** Ders listesini gösteren açılır menü. */
fun Context.lessonSpinner(selectedId: Long?, extraNewName: String? = null): Spinner {
    val names = Repo.lessons.map { "${it.emoji}  ${it.name}" }.toMutableList()
    if (extraNewName != null) names.add("➕ Yeni ders: $extraNewName")
    val sp = Spinner(this)
    sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
    val idx = Repo.lessons.indexOfFirst { it.id == selectedId }
    sp.setSelection(if (idx >= 0) idx else if (extraNewName != null) names.size - 1 else 0)
    return sp
}

/**
 * Ders ya da ünite oluşturma / düzenleme: ad + simge + renk.
 * Ünite için ayrıca ders seçilir (ünite başka derse taşınabilir).
 */
fun Context.itemDialog(
    title: String,
    isUnit: Boolean,
    name: String?,
    emoji0: String?,
    color0: Int?,
    lessonId: Long?,
    onOk: (name: String, emoji: String, color: Int, lessonId: Long?) -> Unit
) {
    var emoji = emoji0 ?: if (isUnit) "📘" else "📖"
    var color = color0 ?: (Repo.lessons.size % Palette.COLORS.size)

    val nameField = makeField(if (isUnit) "Ünite adı (ör. Kültürel Mirasımız)" else "Ders adı (ör. Sosyal Bilgiler)", name ?: "", false)

    fun label(s: String) = TextView(this).apply {
        text = s
        textSize = 13f
        setTextColor(col(R.color.muted))
        setPadding(0, dp(16), 0, dp(6))
    }

    val lessonSp = if (isUnit) lessonSpinner(lessonId) else null

    val emojiRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val emojiViews = mutableListOf<TextView>()
    val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    val colorViews = mutableListOf<View>()

    fun paint() {
        val c = Palette.color(color)
        emojiViews.forEach { v ->
            v.background = if (v.text.toString() == emoji) circle(soft(c, 0.75f)) else circle(Color.TRANSPARENT)
        }
        colorViews.forEachIndexed { i, v ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.color(i))
                if (i == color) setStroke(dp(4), darker(Palette.color(i), 0.35f))
            }
        }
    }

    Palette.EMOJIS.forEach { e ->
        val tv = TextView(this).apply {
            text = e
            textSize = 24f
            gravity = Gravity.CENTER
            setOnClickListener { emoji = e; paint() }
        }
        emojiViews.add(tv)
        emojiRow.addView(tv, LinearLayout.LayoutParams(dp(48), dp(48)))
    }
    Palette.COLORS.indices.forEach { i ->
        val v = View(this).apply { setOnClickListener { color = i; paint() } }
        colorViews.add(v)
        colorRow.addView(v, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(8) })
    }
    paint()

    val emojiScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(emojiRow) }
    val colorScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(colorRow) }
    val views = mutableListOf<View>(nameField)
    if (lessonSp != null) { views.add(label("Ders")); views.add(lessonSp) }
    views.add(label("Simge")); views.add(emojiScroll); views.add(label("Renk")); views.add(colorScroll)
    val content = box(*views.toTypedArray())

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(content)
        .setPositiveButton("Kaydet", null)
        .setNegativeButton("Vazgeç", null)
        .show()
    dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
        val n = nameField.text.toString().trim()
        if (n.isEmpty()) {
            toast(if (isUnit) "Ünite adı boş olamaz" else "Ders adı boş olamaz")
            return@setOnClickListener
        }
        val lid = lessonSp?.let { Repo.lessons.getOrNull(it.selectedItemPosition)?.id }
        if (isUnit && lid == null) {
            toast("Önce bir ders ekle")
            return@setOnClickListener
        }
        onOk(n, emoji, color, lid)
        dialog.dismiss()
    }
}

class SheetItem(val icon: String, val title: String, val sub: String? = null, val action: () -> Unit)

/** Alttan açılan seçenek menüsü. */
fun Context.sheet(title: String?, items: List<SheetItem>) {
    val dialog = BottomSheetDialog(this)
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(24))
    }
    if (title != null) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(col(R.color.ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(24), dp(12), dp(24), dp(8))
        })
    }
    items.forEach { item ->
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                item.action()
            }
        }
        selectableBg(row)
        row.addView(TextView(this).apply {
            text = item.icon
            textSize = 20f
            gravity = Gravity.CENTER
            background = circle(col(R.color.primary_soft))
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, 0, 0)
        }
        texts.addView(TextView(this).apply {
            text = item.title
            textSize = 16f
            setTextColor(col(R.color.ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        if (item.sub != null) {
            texts.addView(TextView(this).apply {
                text = item.sub
                textSize = 13f
                setTextColor(col(R.color.muted))
            })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
    }
    dialog.setContentView(root)
    dialog.show()
}

/** Çalışma / test sonunda gösterilen sonuç penceresi (Pofi sonuca göre tepki verir). */
fun Activity.showResult(known: Int, wrong: Int, color: Int, onRetry: (() -> Unit)?, onDone: () -> Unit) {
    val v = LayoutInflater.from(this).inflate(R.layout.dialog_result, null)
    val total = known + wrong
    val p = if (total == 0) 0 else Math.round(known * 100f / total)
    val stars = when {
        p >= 90 -> 3
        p >= 70 -> 2
        p >= 40 -> 1
        else -> 0
    }
    val mood = when (stars) {
        3, 2 -> Mood.CHEER
        1 -> Mood.HAPPY
        else -> Mood.OOPS
    }
    v.findViewById<ImageView>(R.id.rPofi).setImageResource(mood.res)
    v.findViewById<TextView>(R.id.rTitle).text = when (stars) {
        3 -> if (wrong == 0) "Mükemmel! Hepsini bildin" else "Harika iş çıkardın!"
        2 -> "Çok iyi gidiyorsun!"
        1 -> "Güzel, devam et!"
        else -> "Biraz daha tekrar edelim"
    }
    v.findViewById<TextView>(R.id.rSay).text = when (stars) {
        3 -> "Pofi seninle gurur duyuyor! 🎉"
        2 -> "Az kaldı, birkaç tekrar daha!"
        1 -> "Her tekrar seni daha güçlü yapar."
        else -> "Hiç dert etme, birlikte tekrar edelim."
    }
    v.findViewById<TextView>(R.id.rStars).text = "★".repeat(stars) + "☆".repeat(3 - stars)
    v.findViewById<TextView>(R.id.rStars).setTextColor(col(R.color.accent))
    v.findViewById<TextView>(R.id.rPct).text = "%$p"
    v.findViewById<CircularProgressIndicator>(R.id.rRing).apply {
        setIndicatorColor(color)
        setProgressCompat(p, true)
    }
    v.findViewById<TextView>(R.id.rDetail).text = "✓ $known bildin   ·   ✗ $wrong bilemedin"

    val dialog = MaterialAlertDialogBuilder(this).setView(v).setCancelable(false).create()
    val retry = v.findViewById<MaterialButton>(R.id.rRetry)
    if (onRetry == null || wrong == 0) {
        retry.visibility = View.GONE
    } else {
        retry.setOnClickListener { dialog.dismiss(); onRetry() }
    }
    val done = v.findViewById<MaterialButton>(R.id.rDone)
    done.backgroundTintList = ColorStateList.valueOf(color)
    done.setOnClickListener { dialog.dismiss(); onDone() }
    dialog.show()
}

// ---------- Sesli okuma ----------

class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var warned = false

    init {
        tts = TextToSpeech(app, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val r = tts?.setLanguage(Locale.forLanguageTag("tr-TR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String) {
        if (!ready) {
            if (!warned) {
                Toast.makeText(app, "Telefonda Türkçe sesli okuma bulunamadı (Ayarlar › Metin okuma).", Toast.LENGTH_LONG).show()
                warned = true
            }
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kart")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

const val HELP_TEXT = """📚 Düzen
Ana ekranda dersler var. Bir derse dokununca altındaki üniteler açılır, üniteye dokununca kartlar.

📥 Claude'dan kart almak
1. Konunun fotoğrafını Claude'a gönder.
2. Claude "@ders:" ve "@ünite:" ile başlayan bir liste verir. Listeyi kopyala.
3. Ekle › Panodan içe aktar. Kartlar doğru dersin altına yerleşir. Önizlemede dersi değiştirebilirsin.

✏️ Elle eklemek
Ders ve ünite: Ekle düğmesi. Kart: ünitenin içinde "Ekle".

🃏 Kartlarla çalış
Karta dokununca cevap görünür, sonra Bildim ya da Bilemedim. 🔊 sesli okur.

❓ Test çöz
Her soru için 4 şık. En az 4 kart gerekir.

🔀 Karışık çalış / test
Dersin tüm ünitelerinden karışık sorar.

🔁 Tekrar
Sadece son seferde bilemediğin kartlar.

📊 İstatistikler
Başarı, seri, son 7 gün, derslere ve ünitelere göre durum.

💾 Yedek
Menü › Yedeği kaydet ile tüm dersleri dosyaya kaydedip başka telefona taşıyabilirsin."""
