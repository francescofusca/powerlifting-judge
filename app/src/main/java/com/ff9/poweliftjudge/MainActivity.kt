package com.ff9.poweliftjudge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val squatCard: CardView = findViewById(R.id.card_squat)
        val benchCard: CardView = findViewById(R.id.card_bench)
        val deadliftCard: CardView = findViewById(R.id.card_deadlift)
        val sumoCard: CardView = findViewById(R.id.card_sumo)
        val historyButton: Button = findViewById(R.id.btn_history)
        val settingsButton: Button = findViewById(R.id.btn_settings)

        // Animate cards on startup
        animateCardsIn(listOf(squatCard, benchCard, deadliftCard, sumoCard))

        squatCard.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_pulse))
            openJudge("Squat")
        }
        benchCard.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_pulse))
            openJudge("Bench Press")
        }
        deadliftCard.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_pulse))
            openJudge("Deadlift")
        }
        sumoCard.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_pulse))
            openJudge("Sumo Deadlift")
        }
        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun openJudge(liftType: String) {
        val intent = Intent(this, JudgeActivity::class.java)
        intent.putExtra("LIFT_TYPE", liftType)
        startActivity(intent)
    }

    private fun animateCardsIn(cards: List<CardView>) {
        val handler = Handler(Looper.getMainLooper())
        cards.forEachIndexed { index, card ->
            card.alpha = 0f
            handler.postDelayed({
                card.alpha = 1f
                card.startAnimation(AnimationUtils.loadAnimation(this, R.anim.card_slide_up))
            }, (index * 100).toLong())
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }
}
