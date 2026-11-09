package com.bilgikartlari.app

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Çoktan seçmeli test: doğru cevap + 3 yanlış şık (önce aynı üniteden, yetmezse dersten). */
class QuizActivity : AppCompatActivity() {
    private var scope: StudyScope? = null
    private var pool: List<Card> = emptyList()
    private var queue = mutableListOf<Card>()
    private val wrongThisRound = mutableListOf<Card>()
    private var index = 0
    private var known = 0
    private var wrong = 0
    private var answered = false
    private var saved = false
    private var color = 0
    private var speaker: Speaker? = null

    private lateinit var question: TextView
    private lateinit var options: LinearLayout
    private lateinit var fbRow: View
    private lateinit var fbPofi: ImageView
    private lateinit var feedback: TextView
    private lateinit var progressText: TextView
    private lateinit var scoreText: TextView
    private lateinit var bar: LinearProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sc = StudyScope.from(intent)
        if (sc == null || sc.cards.size < 4) {
            finish()
            return
        }
        scope = sc
        color = sc.color
        pool = Repo.lesson(sc.lessonId)?.cards() ?: sc.cards
        queue = sc.cards.shuffled().toMutableList()

        setContentView(R.layout.activity_quiz)
        setupToolbar("Test: ${sc.title}", true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)

        question = findViewById(R.id.question)
        options = findViewById(R.id.options)
        fbRow = findViewById(R.id.fbRow)
        fbPofi = findViewById(R.id.fbPofi)
        feedback = findViewById(R.id.feedback)
        progressText = findViewById(R.id.progressText)
        scoreText = findViewById(R.id.scoreText)
        bar = findViewById(R.id.bar)

        findViewById<MaterialCardView>(R.id.qCard).setCardBackgroundColor(color)
        bar.setIndicatorColor(color)
        speaker = Speaker(this)
        findViewById<ImageButton>(R.id.speak).setOnClickListener { speaker?.speak(question.text.toString()) }
        show()
    }

    private fun distractors(c: Card): List<String> {
        val same = Repo.unitOf(c)?.cards ?: emptyList()
        val seen = mutableSetOf(Importer.norm(c.a))
        val out = mutableListOf<String>()
        for (src in listOf(same.shuffled(), pool.shuffled())) {
            for (x in src) {
                if (out.size >= 3) break
                val k = Importer.norm(x.a)
                if (x.id != c.id && k !in seen) {
                    seen.add(k)
                    out.add(x.a)
                }
            }
        }
        return out
    }

    private fun show() {
        answered = false
        val c = queue[index]
        progressText.text = "Soru ${index + 1} / ${queue.size}"
        scoreText.text = "✓ $known   ✗ $wrong"
        bar.max = queue.size
        bar.setProgressCompat(index, true)
        val from = if (scope?.mixed == true) Repo.unitOf(c)?.let { "\n\n${it.emoji} ${it.name}" } ?: "" else ""
        question.text = c.q + from
        fbRow.visibility = View.INVISIBLE

        val choices = (distractors(c) + c.a).shuffled()
        options.removeAllViews()
        val letters = listOf("A", "B", "C", "D")
        choices.forEachIndexed { i, choice ->
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "${letters[i]})  $choice"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                textSize = 15f
                minHeight = dp(60)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                cornerRadius = dp(18)
                strokeWidth = dp(2)
                strokeColor = ColorStateList.valueOf(col(R.color.outline))
                setTextColor(col(R.color.ink))
                backgroundTintList = ColorStateList.valueOf(col(R.color.white))
                tag = choice
                setOnClickListener { choose(this, choice == c.a) }
            }
            options.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            })
        }
        options.alpha = 0f
        options.animate().alpha(1f).setDuration(200).start()
    }

    private fun paintButton(b: MaterialButton, bg: Int, fg: Int) {
        b.backgroundTintList = ColorStateList.valueOf(soft(bg, 0.82f))
        b.strokeColor = ColorStateList.valueOf(bg)
        b.setTextColor(fg)
    }

    private fun choose(b: MaterialButton, ok: Boolean) {
        if (answered) return
        answered = true
        b.tick(ok)
        val c = queue[index]
        Repo.record(c, ok)
        for (i in 0 until options.childCount) {
            val ob = options.getChildAt(i) as MaterialButton
            ob.isClickable = false
            if (ob.tag == c.a) paintButton(ob, col(R.color.known), darker(col(R.color.known), 0.3f))
        }
        if (ok) {
            known++
            fbPofi.setImageResource(Mood.CHEER.res)
            feedback.text = listOf("Doğru! 🎉", "Harika! ✅", "Süpersin! ⭐", "Aferin! 👏", "Pofi çok mutlu! 🦉").random()
            feedback.setTextColor(col(R.color.known))
        } else {
            wrong++
            wrongThisRound.add(c)
            paintButton(b, col(R.color.unknown), darker(col(R.color.unknown), 0.3f))
            fbPofi.setImageResource(Mood.OOPS.res)
            feedback.text = "Sorun değil! Doğrusu yeşil olan."
            feedback.setTextColor(col(R.color.unknown))
        }
        fbRow.visibility = View.VISIBLE
        scoreText.text = "✓ $known   ✗ $wrong"
        options.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            index++
            if (index >= queue.size) finishRound() else show()
        }, if (ok) 1000L else 1900L)
    }

    private fun saveSession() {
        val sc = scope ?: return
        if (!saved && known + wrong > 0) {
            Repo.addSession(sc.lessonId, sc.unitId, sc.title, known, wrong, "test")
            saved = true
        }
    }

    private fun finishRound() {
        saveSession()
        bar.setProgressCompat(queue.size, true)
        val retry: (() -> Unit)? = if (wrongThisRound.isNotEmpty()) ({ restart() }) else null
        showResult(known, wrong, color, onRetry = retry, onDone = { finish() })
    }

    /** Yanlış yapılan soruları yeniden sorar. */
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
