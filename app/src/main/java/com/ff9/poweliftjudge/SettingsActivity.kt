package com.ff9.poweliftjudge

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)

            val seekTimer: SeekBar = findViewById(R.id.seekTimer)
            val valTimer: TextView = findViewById(R.id.valTimer)

            val seekSquat: SeekBar = findViewById(R.id.seekSquat)
            val valSquat: TextView = findViewById(R.id.valSquat)

            val seekBench: SeekBar = findViewById(R.id.seekBench)
            val valBench: TextView = findViewById(R.id.valBench)

            val seekDeadlift: SeekBar = findViewById(R.id.seekDeadlift)
            val valDeadlift: TextView = findViewById(R.id.valDeadlift)

            val seekSumo: SeekBar = findViewById(R.id.seekSumo)
            val valSumo: TextView = findViewById(R.id.valSumo)

            val btnSave: Button = findViewById(R.id.btnSave)
            val btnSelectSound: Button = findViewById(R.id.btnSelectSound)
            val tvCurrentSound: TextView = findViewById(R.id.tvCurrentSound)
            val btnSelectLanguage: Button = findViewById(R.id.btnSelectLanguage)
            val tvCurrentLanguage: TextView = findViewById(R.id.tvCurrentLanguage)

            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

            // Defaults
            seekTimer.progress = prefs.getInt("countdown_timer", 3)
            seekSquat.progress = prefs.getInt("threshold_squat", 90)
            seekBench.progress = prefs.getInt("threshold_bench", 90)
            seekDeadlift.progress = prefs.getInt("threshold_deadlift", 60)
            seekSumo.progress = prefs.getInt("threshold_sumo", 60)

            // Sound selection
            val currentSound = prefs.getString("start_sound", "start") ?: "start"
            tvCurrentSound.text = getString(R.string.current_sound, "$currentSound.mp3")

            // Language selection
            val currentLang = LocaleHelper.getLanguage(this)
            val currentLangName = LocaleHelper.getLanguageName(this, currentLang)
            tvCurrentLanguage.text = getString(R.string.current_language, currentLangName)

            val timerText = if (seekTimer.progress == 1) {
                "1 ${getString(R.string.second)}"
            } else {
                "${seekTimer.progress} ${getString(R.string.seconds)}"
            }
            valTimer.text = timerText
            valSquat.text = "${seekSquat.progress}°"
            valBench.text = "${seekBench.progress}°"
            valDeadlift.text = "${seekDeadlift.progress}°"
            valSumo.text = "${seekSumo.progress}°"

            val listener = object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    when(seekBar?.id) {
                        R.id.seekTimer -> {
                            val text = if (progress == 1) {
                                "1 ${getString(R.string.second)}"
                            } else {
                                "$progress ${getString(R.string.seconds)}"
                            }
                            valTimer.text = text
                        }
                        R.id.seekSquat -> valSquat.text = "$progress°"
                        R.id.seekBench -> valBench.text = "$progress°"
                        R.id.seekDeadlift -> valDeadlift.text = "$progress°"
                        R.id.seekSumo -> valSumo.text = "$progress°"
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // Salva automaticamente quando l'utente rilascia il seekbar
                    saveSettings()
                }
            }

            seekTimer.setOnSeekBarChangeListener(listener)
            seekSquat.setOnSeekBarChangeListener(listener)
            seekBench.setOnSeekBarChangeListener(listener)
            seekDeadlift.setOnSeekBarChangeListener(listener)
            seekSumo.setOnSeekBarChangeListener(listener)

            btnSelectSound.setOnClickListener {
                showSoundSelectionDialog()
            }

            btnSelectLanguage.setOnClickListener {
                showLanguageSelectionDialog()
            }

            // Rimuoviamo il pulsante SALVA e salviamo automaticamente
            btnSave.visibility = Button.GONE

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error, e.message), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val seekTimer: SeekBar = findViewById(R.id.seekTimer)
        val seekSquat: SeekBar = findViewById(R.id.seekSquat)
        val seekBench: SeekBar = findViewById(R.id.seekBench)
        val seekDeadlift: SeekBar = findViewById(R.id.seekDeadlift)
        val seekSumo: SeekBar = findViewById(R.id.seekSumo)

        editor.putInt("countdown_timer", seekTimer.progress)
        editor.putInt("threshold_squat", seekSquat.progress)
        editor.putInt("threshold_bench", seekBench.progress)
        editor.putInt("threshold_deadlift", seekDeadlift.progress)
        editor.putInt("threshold_sumo", seekSumo.progress)
        editor.apply()
    }

    override fun onPause() {
        super.onPause()
        // Salva quando l'utente esce dalle impostazioni
        saveSettings()
    }

    private fun showSoundSelectionDialog() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentSound = prefs.getString("start_sound", "start") ?: "start"

        // Available sounds in res/raw
        val soundOptions = arrayOf("start", "race_start", "beep_short")
        val soundNames = arrayOf("Start.mp3", "Race Start.mp3", "Beep Short.mp3")

        val currentIndex = soundOptions.indexOf(currentSound).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_start_sound)
            .setSingleChoiceItems(soundNames, currentIndex) { dialog, which ->
                val selectedSound = soundOptions[which]

                // Save to preferences
                val editor = prefs.edit()
                editor.putString("start_sound", selectedSound)
                editor.apply()

                // Update UI
                val tvCurrentSound: TextView = findViewById(R.id.tvCurrentSound)
                tvCurrentSound.text = getString(R.string.current_sound, "$selectedSound.mp3")

                Toast.makeText(this, getString(R.string.sound_updated, soundNames[which]), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguageSelectionDialog() {
        val languageCodes = arrayOf("en", "it", "es", "ru", "pt", "de", "fr", "ja")
        val languageNames = arrayOf(
            getString(R.string.lang_english),
            getString(R.string.lang_italian),
            getString(R.string.lang_spanish),
            getString(R.string.lang_russian),
            getString(R.string.lang_portuguese),
            getString(R.string.lang_german),
            getString(R.string.lang_french),
            getString(R.string.lang_japanese)
        )

        val currentLang = LocaleHelper.getLanguage(this)
        val currentIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                val selectedLang = languageCodes[which]

                // Save language and apply
                LocaleHelper.setLocale(this, selectedLang)

                // Update UI
                val tvCurrentLanguage: TextView = findViewById(R.id.tvCurrentLanguage)
                tvCurrentLanguage.text = getString(R.string.current_language, languageNames[which])

                Toast.makeText(this, R.string.language_updated, Toast.LENGTH_LONG).show()
                dialog.dismiss()

                // Recreate activity to apply changes
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang))
    }
}
