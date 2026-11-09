package com.bilgikartlari.app

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Ne çalışılacağını belirler: tek ünite ya da bir dersin tüm üniteleri (karışık). */
class StudyScope(val lessonId: Long, val unitId: Long, val title: String, val color: Int, val cards: List<Card>, val mixed: Boolean) {
    companion object {
        fun from(intent: Intent): StudyScope? {
            val uid = intent.getLongExtra(UnitActivity.EXTRA_UNIT, -1L)
            if (uid != -1L) {
                val u = Repo.unit(uid) ?: return null
                val l = Repo.lessonOf(u)
                return StudyScope(l?.id ?: -1L, u.id, u.name, u.colorInt(), u.cards, false)
            }
            val l = Repo.lesson(intent.getLongExtra(LessonActivity.EXTRA_LESSON, -1L)) ?: return null
            return StudyScope(l.id, -1L, "${l.name} (karışık)", l.colorInt(), l.cards(), true)
        }
    }
}

class StudyActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_WRONG_ONLY = "wrongOnly"
    }

    private var scope: StudyScope? = null
    private var queue = mutableListOf<Card>()
    private val wrongThisRound = mutableListOf<Card>()
    private var index = 0
    private var known = 0
    private var wrong = 0
    private var flipped = false
    private var busy = false
    private var saved = false
    private var color = 0
    private var speaker: Speaker? = null

    private lateinit var card: MaterialCardView
    private lateinit var label: TextView
    private lateinit var text: TextView
    private lateinit var tapHint: TextView
    private lateinit var speak: ImageButton
    private lateinit var btnShow: MaterialButton
    private lateinit var answerRow: View
    private lateinit var progressText: TextView
    private lateinit var scoreText: TextView
    private lateinit var bar: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sc = StudyScope.from(intent)
        val wrongOnly = intent.getBooleanExtra(EXTRA_WRONG_ONLY, false)
        val source: List<Card> = when {
            sc == null -> emptyList()
            wrongOnly -> sc.cards.filter { it.last == 2 }
            else -> sc.cards
        }
        if (sc == null || source.isEmpty()) {
            finish()
            return
        }
        scope = sc
        color = sc.color
        queue = source.shuffled().toMutableList()

        setContentView(R.layout.activity_study)
        setupToolbar(if (wrongOnly) "Tekrar: ${sc.title}" else sc.title, true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)

        card = findViewById(R.id.card)
        label = findViewById(R.id.label)
        text = findViewById(R.id.text)
        tapHint = findViewById(R.id.tapHint)
        speak = findViewById(R.id.speak)
        btnShow = findViewById(R.id.btnShow)
        answerRow = findViewById(R.id.answerRow)
        progressText = findViewById(R.id.progressText)
        scoreText = findViewById(R.id.scoreText)
        bar = findViewById(R.id.bar)

        bar.setIndicatorColor(color)
        btnShow.backgroundTintList = ColorStateList.valueOf(color)
        card.cameraDistance = 9000 * resources.displayMetrics.density
        card.setOnClickListener { flip() }
        btnShow.setOnClickListener { flip() }
        speak.setOnClickListener { speaker?.speak(text.text.toString()) }
        findViewById<View>(R.id.btnKnown).setOnClickListener { answer(true, it) }
        findViewById<View>(R.id.btnUnknown).setOnClickListener { answer(false, it) }

        speaker = Speaker(this)
        show()
    }

    private fun show() {
        flipped = false
        paint()
        btnShow.visibility = View.VISIBLE
        answerRow.visibility = View.GONE
        progressText.text = "Kart ${index + 1} / ${queue.size}"
        scoreText.text = "✓ $known   ✗ $wrong"
        bar.max = queue.size
        bar.setProgressCompat(index, true)
        card.alpha = 0f
        card.translationX = dpf(40f)
        card.animate().alpha(1f).translationX(0f).setDuration(220).start()
    }

    /** Kartın o anki yüzünü (soru ya da cevap) çizer. Karışık çalışmada kartın ünitesi de yazılır. */
    private fun paint() {
        val c = queue[index]
        val from = if (scope?.mixed == true) Repo.unitOf(c)?.let { "${it.emoji} ${it.name}" } else null
        if (!flipped) {
            card.setCardBackgroundColor(col(R.color.white))
            label.text = "SORU"
            label.setTextColor(color)
            label.background = rounded(soft(color), dpf(100f))
            text.text = c.q
            text.setTextColor(col(R.color.ink))
            tapHint.setTextColor(col(R.color.muted))
            tapHint.text = if (from != null) "$from  ·  Cevap için dokun" else "Cevabı görmek için karta dokun"
            speak.imageTintList = ColorStateList.valueOf(color)
        } else {
            card.setCardBackgroundColor(color)
            label.text = "CEVAP"
            label.setTextColor(col(R.color.white))
            label.background = rounded(col(R.color.glass), dpf(100f))
            text.text = c.a
            text.setTextColor(col(R.color.white))
            tapHint.setTextColor(col(R.color.white_80))
            tapHint.text = "Soruya dönmek için dokun"
            speak.imageTintList = ColorStateList.valueOf(col(R.color.white))
        }
    }

    private fun flip() {
        if (busy) return
        busy = true
        speaker?.stop()
        card.animate().rotationY(90f).setDuration(140).withEndAction {
            flipped = !flipped
            paint()
            if (flipped) {
                btnShow.visibility = View.GONE
                answerRow.visibility = View.VISIBLE
            }
            card.rotationY = -90f
            card.animate().rotationY(0f).setDuration(140).withEndAction { busy = false }.start()
        }.start()
    }

    private fun answer(ok: Boolean, v: View) {
        if (busy || !flipped) return
        v.tick(ok)
        speaker?.stop()
        val c = queue[index]
        Repo.record(c, ok)
        if (ok) known++ else {
            wrong++
            wrongThisRound.add(c)
        }
        index++
        if (index >= queue.size) finishRound() else show()
    }

    private fun saveSession() {
        val sc = scope ?: return
        if (!saved && known + wrong > 0) {
            Repo.addSession(sc.lessonId, sc.unitId, sc.title, known, wrong, "kart")
            saved = true
        }
    }

    private fun finishRound() {
        saveSession()
        bar.setProgressCompat(queue.size, true)
        scoreText.text = "✓ $known   ✗ $wrong"
        showResult(known, wrong, color, onRetry = { restart() }, onDone = { finish() })
    }

    private fun restart() {
        queue = wrongThisRound.shuffled().toMutableList()
        wrongThisRound.clear()
        index = 0
        known = 0
        wrong = 0
        saved = false
        show()
    }

    override fun onPause() {
        super.onPause()
        // Yarıda bırakılırsa o ana kadarki çalışma da istatistiğe yazılır
        if (isFinishing) saveSession()
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
