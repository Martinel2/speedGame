package com.example.term

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore

class MeasureSkillActivity : AppCompatActivity() {
    private lateinit var infoText: TextView
    private lateinit var testButton: ImageView
    private lateinit var startButton: Button
    private lateinit var resultText: TextView
    private var reactionTimes = mutableListOf<Long>()
    private var startTime: Long = 0L
    private var testRunning = false
    private var timer: CountDownTimer? = null
    private var bestReactionTime: Long = Long.MAX_VALUE
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var clientId: String
    private lateinit var clickSound: MediaPlayer
    private lateinit var fullButtonImage: Bitmap
    private lateinit var buttonBitmaps: List<Bitmap>


    private fun loadAndSliceButtonImage() {
        val drawable = ContextCompat.getDrawable(this, R.drawable.colorful_buttons)
        val bitmapDrawable = drawable as BitmapDrawable
        fullButtonImage = bitmapDrawable.bitmap

        val rows = 3
        val cols = 3
        val cellWidth = fullButtonImage.width / cols
        val cellHeight = fullButtonImage.height / rows

        buttonBitmaps = mutableListOf()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * cellWidth
                val y = row * cellHeight
                val buttonBitmap = Bitmap.createBitmap(fullButtonImage, x, y, cellWidth, cellHeight)
                (buttonBitmaps as MutableList).add(buttonBitmap)
            }
        }
    }

    private fun getRandomButtonBitmap(): Bitmap {
        return buttonBitmaps.random()
    }

    // dp → px 변환
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measure_skill)

        infoText = findViewById(R.id.info_text)
        testButton = findViewById(R.id.test_button)
        startButton = findViewById(R.id.start_test_button)
        resultText = findViewById(R.id.result_text)
        prefs = getSharedPreferences("game_prefs", MODE_PRIVATE)
        bestReactionTime = prefs.getLong("best_reaction_time", Long.MAX_VALUE)
        clientId = prefs.getString("client_id", "default") ?: "default"
        clickSound = MediaPlayer.create(this, R.raw.button_click)
        loadAndSliceButtonImage()


        testButton.visibility = View.INVISIBLE
        resultText.visibility = View.INVISIBLE

        startButton.setOnClickListener {
            startTest()
        }
        testButton.setOnClickListener {
            if (testRunning) {
                clickSound.seekTo(0)
                clickSound.start()

                testButton.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(100)
                    .withEndAction {
                        testButton.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction {
                                val reaction = System.currentTimeMillis() - startTime
                                reactionTimes.add(reaction)
                                testButton.visibility = View.INVISIBLE
                                showNextButtonWithDelay()
                            }
                            .start()
                    }
                    .start()
            }
        }
    }

    private fun startTest() {
        testRunning = true
        reactionTimes.clear()
        resultText.visibility = View.INVISIBLE
        infoText.text = "30초간 버튼이 나타날 때마다 빠르게 눌러주세요!"
        startButton.visibility = View.INVISIBLE
        showNextButtonWithDelay()
        timer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                testRunning = false
                testButton.visibility = View.INVISIBLE
                showResult()
            }
        }.start()
    }

    private fun showNextButtonWithDelay() {
        if (!testRunning) return
        val delay = (1000..2000).random().toLong()
        Handler(Looper.getMainLooper()).postDelayed({
            if (!testRunning) return@postDelayed

            val bitmap = getRandomButtonBitmap()
            testButton.setImageBitmap(bitmap)

            val sizePx = dpToPx((60..120).random())
            testButton.layoutParams.width = sizePx
            testButton.layoutParams.height = sizePx
            testButton.requestLayout()

            testButton.x = (100..600).random().toFloat()
            testButton.y = (200..1000).random().toFloat()
            testButton.alpha = 0f
            testButton.visibility = View.VISIBLE
            testButton.animate().alpha(1f).setDuration(150).start()

            startTime = System.currentTimeMillis()
        }, delay)
    }


    private fun showResult() {
        val avg = reactionTimes.average().toLong()
        var updated = false
        if (avg < bestReactionTime) {
            bestReactionTime = avg
            prefs.edit().putLong("best_reaction_time", bestReactionTime).apply()
            uploadBestReactionTimeToFirestore(bestReactionTime)
            updated = true
        }
        // 시작 스테이지 배정: 내 반응속도보다 높은 threshold를 가진 스테이지 중 가장 높은 스테이지
        var newStage = 1
        for (i in 1..20) {
            if (bestReactionTime <= getStageThresholdRelaxed(i)) {
                newStage = i + 1
                break
            }
        }
        prefs.edit().putInt("start_stage_level", newStage+1).apply()

        resultText.visibility = View.VISIBLE
        resultText.text = "평균 반응속도: ${avg}ms\n" +
                if (updated) "최고 기록 갱신!\n" else "기존 기록 유지\n" +
                "시작 스테이지: $newStage"
        infoText.text = "실력 측정 결과를 확인하세요."
        startButton.text = "확인"
        startButton.visibility = View.VISIBLE
        startButton.setOnClickListener {
            val resultIntent = Intent().putExtra("new_stage_level", newStage)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun getStageThreshold(stage: Int): Int {
        val a = 398.0 / 39.0
        val b = 371.0 - a * 28.0

        val minAge = 10
        val maxAge = 50
        val totalStages = 20

        val agePerStage = (maxAge - minAge).toDouble() / (totalStages - 1)
        val x = maxAge - (stage - 1) * agePerStage

        return (a * x + b).toInt()
    }

    private fun getStageThresholdRelaxed(stage: Int): Int {
        return (getStageThreshold(stage) * 1.8).toInt()
    }


    private fun uploadBestReactionTimeToFirestore(time: Long) {
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "client_id" to clientId,
            "best_reaction_time" to time,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("user_best").document(clientId).set(data)
    }
    override fun onResume() {
        super.onResume()
        val rootLayout = findViewById<View>(R.id.reaction_game_root_layout)  // XML의 루트 id
        WeatherUtil.applyWeatherBackground(this, rootLayout)
    }

}
