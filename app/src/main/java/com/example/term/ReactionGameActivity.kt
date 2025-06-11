// ReactionGameActivity.kt (Measurement Protocol + Firebase SDK 병행 적용)
package com.example.term

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class ReactionGameActivity : AppCompatActivity() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private var stageLevel = 1
    private var startTime: Long = 0
    private val reactionTimes = mutableListOf<Pair<Long, Long>>()  // (timestamp, reactionTime)
    private lateinit var gameButton: Button
    private lateinit var stageTextView: TextView
    private lateinit var clientId: String
    private var stageLoopJob: Job? = null
    private val measurementId = "G-11233218848" // TODO: Replace with real Measurement ID
    private val apiSecret = "pkKG4t0mTd6oVPOYZUWDmA" // TODO: Replace with real API Secret
    private var currentButtonDuration: Long = 0L // 버튼 노출 시간(ms)
    private lateinit var prefs: SharedPreferences
    private var bestStageLevel: Int = 1
    private var bestReactionTime: Long = Long.MAX_VALUE
    private lateinit var pauseButton: Button
    private lateinit var pauseOverlay: View
    private lateinit var resumeButton: Button
    private var isPaused: Boolean = false
    private var pauseTime: Long = 0L
    private var remainingButtonDuration: Long = 0L
    private var buttonHandler: Handler? = null
    private var buttonRunnable: Runnable? = null
    private lateinit var congratsText: TextView
    private lateinit var mainButton: Button
    private lateinit var avgReactionText: TextView
    private lateinit var thresholdText: TextView
    private lateinit var bestReactionText: TextView
    private lateinit var predictor:TFLitePredictor

    // 3x3 그리드별 [시도, 성공, 가중치] 기록용 데이터 클래스
    data class GridStat(var attempts: Int = 0, var success: Int = 0, var weightedScore: Double = 0.0)
    private val gridStats = Array(3) { Array(3) { GridStat() } }

    // Quadruple 임시 정의
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // dp → px 변환
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }

    // 최신 방식의 화면 크기 구하기
    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 이상
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds: Rect = windowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            // 구버전 호환
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            display.getSize(size)
            Pair(size.x, size.y)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        predictor = TFLitePredictor(applicationContext)
        setContentView(R.layout.activity_reaction_game)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        // SharedPreferences 초기화 및 최고 기록 불러오기
        prefs = getSharedPreferences("game_prefs", MODE_PRIVATE)
        bestStageLevel = prefs.getInt("new_stage_level", 1)
        bestReactionTime = prefs.getLong("best_reaction_time", Long.MAX_VALUE)

        // Firestore에서 사용자 최고 반응속도 불러오기
        fetchBestReactionTimeFromFirestore()


        stageLevel = intent.getIntExtra("stage_level", 3)

        gameButton = findViewById(R.id.game_button)
        stageTextView = findViewById(R.id.stage_text)
        stageTextView.text = "Stage $stageLevel"

        pauseButton = findViewById(R.id.pause_button)
        pauseOverlay = findViewById(R.id.pause_overlay)
        resumeButton = findViewById(R.id.resume_button)
        pauseButton.setOnClickListener { pauseGame() }
        resumeButton.setOnClickListener { resumeGame() }

        congratsText = findViewById(R.id.congrats_text)

        mainButton = findViewById(R.id.main_button)
        mainButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        avgReactionText = findViewById(R.id.avg_reaction_text)
        thresholdText = findViewById(R.id.threshold_text)
        bestReactionText = findViewById(R.id.best_reaction_text)

        thresholdText.text = "기준: ${getStageThresholdRelaxed(stageLevel)}ms"
        getRecentAverage()?.let { updateAvgReactionText(it) }

        FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                clientId = task.result
                startStageLoop()
            }
        }

        prepareNextButton()
    }

    private fun getRecentAverage(windowMillis: Long = 10_000): Double? {
        val now = System.currentTimeMillis()
        val recent = reactionTimes.filter { now - it.first <= windowMillis }
        return if (recent.isNotEmpty()) recent.map { it.second }.average() else null
    }


    // prepareNextButton 내부에서 클릭 및 실패 처리를 각각 함수로 분리해 호출하도록 연결
    private fun prepareNextButton() {
        if (isPaused) return
        gameButton.visibility = View.INVISIBLE
        val delay = (1400..1600).random().toLong()
        val duration = (1500..2000).random().toLong()
        currentButtonDuration = duration
        Handler(Looper.getMainLooper()).postDelayed({
            if (isPaused) return@postDelayed
            val buttonSizeDp = (50..150).random()
            val buttonSizePx = dpToPx(buttonSizeDp)
            val color = getRandomColor()
            gameButton.setBackgroundColor(color)
            val (x, y, gridX, gridY) = getRandomButtonPositionAndGrid(buttonSizeDp)
            gameButton.x = x
            gameButton.y = y
            gameButton.width = buttonSizePx
            gameButton.height = buttonSizePx
            gridStats[gridX][gridY].attempts++
            gameButton.visibility = View.VISIBLE
            startTime = System.currentTimeMillis()
            getRecentAverage()?.let { updateAvgReactionText(it) }
            buttonHandler = Handler(Looper.getMainLooper())
            buttonRunnable = Runnable {
                if (gameButton.visibility == View.VISIBLE) {
                    registerFailure(duration, gridX, gridY, buttonSizePx)
                }
            }
            buttonHandler?.postDelayed(buttonRunnable!!, duration)
            gameButton.setOnClickListener {
                onGameButtonClicked()
            }
        }, delay)
    }

    // 버튼 클릭 시 timestamp와 함께 반응속도 저장
    private fun onGameButtonClicked() {
        val reactionTime = System.currentTimeMillis() - startTime
        val now = System.currentTimeMillis()
        if (reactionTime > 50) {  // 필터링 적용
            reactionTimes.add(now to reactionTime)
        }
        logFirebaseEvent("button_click", reactionTime)
        val (gridX, gridY) = getCurrentButtonGrid()
        gridStats[gridX][gridY].success++
        val successWeight = calcWeight(gameButton.width, reactionTime)
        gridStats[gridX][gridY].weightedScore += successWeight
        getRecentAverage()?.let { updateAvgReactionText(it) }
        prepareNextButton()
    }

    // 실패 시 duration을 timestamp와 함께 저장하도록 보완
    private fun registerFailure(duration: Long, gridX: Int, gridY: Int, sizePx: Int) {
        val failWeight = calcWeight(sizePx, duration)
        gridStats[gridX][gridY].weightedScore -= failWeight
        val now = System.currentTimeMillis()
        reactionTimes.add(now to duration)
        getRecentAverage()?.let { updateAvgReactionText(it) }
        gameButton.visibility = View.INVISIBLE
        prepareNextButton()
    }


    // Stage 루프: 최근 평균 반응속도로 판단, 충분한 반응 데이터가 있을 때만 통과 판정
    private fun startStageLoop() {
        stageLoopJob = CoroutineScope(Dispatchers.Main).launch {
            val windowMillis = 10_000L
            val startWindow = System.currentTimeMillis()
            while (isActive) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val duration = now - startWindow
                val avg = getRecentAverage(windowMillis)
                if (avg != null) {
                    updateAvgReactionText(avg)
                    if (duration >= windowMillis && avg < getStageThresholdRelaxed(stageLevel)) {
                        sendToMeasurementProtocol(avg)
                        navigateToStageClear(avg)
                        cancel()
                    }
                } else {
                    avgReactionText.text = "평균: 측정 중..."
                }
            }
        }
    }


    private fun sendToMeasurementProtocol(average: Double) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("client_id", clientId)
            put("events", listOf(
                JSONObject().apply {
                    put("name", "stage_cleared")
                    put("params", JSONObject().apply {
                        put("reaction_avg", average)
                        put("stage", stageLevel)
                        put("debug_mode", 1) // DebugView 표시용
                    })
                }
            ))
        }

        val request = Request.Builder()
            .url("https://www.google-analytics.com/mp/collect?measurement_id=$measurementId&api_secret=$apiSecret")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MP", "Failed to send event: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("MP", "Sent event. Response: ${response.code}")
            }
        })
    }

    private fun navigateToStageClear(finalAvg: Double) {
        buttonRunnable?.let { buttonHandler?.removeCallbacks(it) }
        gameButton.visibility = View.INVISIBLE
        isPaused = true
        uploadStageStatsToFirestore(finalAvg)

        congratsText.alpha = 0f
        congratsText.visibility = View.VISIBLE
        congratsText.animate().alpha(1f).setDuration(700).withEndAction {
            congratsText.animate().alpha(0f).setDuration(700).withEndAction {
                congratsText.visibility = View.GONE
                var isBest = false
                if (finalAvg < bestReactionTime) {
                    bestReactionTime = finalAvg.toLong()
                    prefs.edit().putLong("best_reaction_time", bestReactionTime).apply()
                    uploadBestReactionTimeToFirestore(bestReactionTime)
                    isBest = true
                }
                if (stageLevel+1 > bestStageLevel) {
                    bestStageLevel = stageLevel+1
                }
                prefs.edit().putInt("new_stage_level", bestStageLevel).apply()
                prefs.edit().putInt("start_stage_level", bestStageLevel).apply()
                val intent = Intent(this, StageClearActivity::class.java).apply {
                    putExtra("stage_level", stageLevel)
                    putExtra("predicted_age", predictAgeFromStats(finalAvg))
                    putExtra("is_best", isBest)
                    putExtra("best_reaction_time", bestReactionTime)
                }
                startActivity(intent)
                finish()
            }
        }.start()
    }

    private fun logFirebaseEvent(eventName: String, reactionTime: Long) {
        val bundle = Bundle().apply {
            putLong("reaction_time", reactionTime)
            putInt("stage", stageLevel)
        }
        firebaseAnalytics.logEvent(eventName, bundle)
    }


    // 실력 평가 텍스트 반환
    private fun predictAgeFromStats(avg: Double): String {
        val predictedAgeGroup = predictor.predictReactionAge(floatArrayOf(avg.toFloat()))
        return "당신은 ${avg.toInt()}ms로 $predictedAgeGroup 실력입니다."
    }

    // 버튼 랜덤 색상 (배경색과 유사한 색상 제외, 임시 랜덤)
    private fun getRandomColor(): Int {
        // TODO: 배경색과의 색상 거리 계산 후 필터링
        val bgColor = Color.WHITE // 임시(실제 배경색 적용 필요)
        var color: Int
        do {
            color = Color.rgb((0..255).random(), (0..255).random(), (0..255).random())
        } while (colorDistance(color, bgColor) < 100.0)
        return color
    }

    // 색상 거리 계산(간단한 유클리드 거리)
    private fun colorDistance(c1: Int, c2: Int): Double {
        val r1 = Color.red(c1)
        val g1 = Color.green(c1)
        val b1 = Color.blue(c1)
        val r2 = Color.red(c2)
        val g2 = Color.green(c2)
        val b2 = Color.blue(c2)
        return Math.sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble())
    }

    // 버튼 랜덤 위치 및 3x3 그리드 좌표 반환
    private fun getRandomButtonPositionAndGrid(sizeDp: Int): Quadruple<Float, Float, Int, Int> {
        val sizePx = dpToPx(sizeDp)
        val (screenW, screenH) = getScreenSize()
        val gridW = screenW / 3
        val gridH = screenH / 3
        val gridX = (0..2).random()
        val gridY = (0..2).random()
        // 상단 바/툴바/일시정지 버튼/하단 텍스트뷰 영역 피해서 위치 계산
        val topMargin = dpToPx(80) // 상단 바+툴바+여유
        val bottomMargin = dpToPx(180) // 하단 텍스트뷰+여유
        val leftMargin = dpToPx(16)
        val rightMargin = dpToPx(16)
        // 일시정지 버튼이 있는 x영역(예: 상단 오른쪽 120dp) 피하기
        val pauseButtonWidth = dpToPx(56)
        val pauseButtonAreaStart = screenW - pauseButtonWidth - rightMargin
        var minX = gridX * gridW + leftMargin
        var minY = gridY * gridH + topMargin
        var maxX = ((gridX + 1) * gridW - sizePx - rightMargin).coerceAtLeast(minX)
        var maxY = ((gridY + 1) * gridH - sizePx - bottomMargin).coerceAtLeast(minY)

        if (minY < topMargin + pauseButtonWidth && maxX > pauseButtonAreaStart) {
            minX = leftMargin
            maxX = pauseButtonAreaStart - sizePx
        }
        // empty range 방지
        val safeMaxX = if (maxX < minX) minX else maxX
        val safeMaxY = if (maxY < minY) minY else maxY
        val x = (minX..safeMaxX).random().toFloat()
        val y = (minY..safeMaxY).random().toFloat()
        return Quadruple(x, y, gridX, gridY)
    }

    // 현재 버튼의 그리드 좌표 반환
    private fun getCurrentButtonGrid(): Pair<Int, Int> {
        val (screenW, screenH) = getScreenSize()
        val gridW = screenW / 3
        val gridH = screenH / 3
        val x = gameButton.x.toInt()
        val y = gameButton.y.toInt()
        val gridX = (x / gridW).coerceIn(0, 2)
        val gridY = (y / gridH).coerceIn(0, 2)
        return Pair(gridX, gridY)
    }

    // 가중치 계산 함수(예시)
    private fun calcWeight(size: Int, time: Long): Double {
        // 크기가 작고, 반응속도가 빠를수록 높은 점수
        return (200.0 / size) + (1000.0 / (time + 1))
    }

    // Firestore 업로드 함수
    private fun uploadStageStatsToFirestore(avg: Double) {
        val db = FirebaseFirestore.getInstance()
        val gridRates = mutableListOf<Float>()
        for (i in 0..2) {
            for (j in 0..2) {
                val stat = gridStats[i][j]
                val rate = if (stat.attempts > 0) stat.success.toFloat() / stat.attempts else 0f
                gridRates.add(rate)
            }
        }
        val data = hashMapOf(
            "client_id" to clientId,
            "stage" to stageLevel,
            "reaction_avg" to avg,
            "grid_rates" to gridRates,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("stage_stats").add(data)
    }

    private fun fetchBestReactionTimeFromFirestore() {
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("user_best").document(clientId).get()
                .addOnSuccessListener { doc ->
                    val best = doc.getLong("best_reaction_time") ?: Long.MAX_VALUE
                    bestReactionTime = best
                    prefs.edit().putLong("best_reaction_time", bestReactionTime).apply()
                    updateBestReactionText()
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "최고 반응속도 불러오기 실패: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("Firestore", "최고 반응속도 fetch 오류: ${e.message}")
        }
    }

    private fun uploadBestReactionTimeToFirestore(time: Long) {
        try {
            val db = FirebaseFirestore.getInstance()
            val data = hashMapOf(
                "client_id" to clientId,
                "best_reaction_time" to time,
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("user_best").document(clientId).set(data)
        } catch (e: Exception) {
            Log.e("Firestore", "최고 반응속도 업로드 오류: ${e.message}")
        }
    }

    private fun pauseGame() {
        if (isPaused) return
        isPaused = true
        pauseOverlay.visibility = View.VISIBLE
        // 코루틴 일시정지
        stageLoopJob?.cancel()
        // 버튼 타이머 일시정지
        buttonRunnable?.let { buttonHandler?.removeCallbacks(it) }
        // 남은 버튼 노출 시간 계산
        remainingButtonDuration = (startTime + currentButtonDuration) - System.currentTimeMillis()
        if (remainingButtonDuration < 0) remainingButtonDuration = 0
    }

    private fun resumeGame() {
        if (!isPaused) return
        isPaused = false
        pauseOverlay.visibility = View.GONE
        // 코루틴 재시작
        startStageLoop()
        // 버튼 타이머 재시작
        if (gameButton.visibility == View.VISIBLE && remainingButtonDuration > 0) {
            buttonHandler = Handler(Looper.getMainLooper())
            buttonRunnable = java.lang.Runnable {
                if (gameButton.visibility == View.VISIBLE) {
                    val failWeight = calcWeight(gameButton.width, remainingButtonDuration)
                    val (gridX, gridY) = getCurrentButtonGrid()
                    gridStats[gridX][gridY].weightedScore -= failWeight
                    gameButton.visibility = View.INVISIBLE
                    prepareNextButton()
                }
            }
            buttonHandler?.postDelayed(buttonRunnable!!, remainingButtonDuration)
        } else {
            prepareNextButton()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) pauseGame()
    }

    override fun onDestroy() {
        stageLoopJob?.cancel()
        super.onDestroy()
    }

    private fun updateAvgReactionText(average: Double) {
        avgReactionText.text = "평균: ${average.toLong()}ms"
        thresholdText.text = "기준: ${getStageThresholdRelaxed(stageLevel)}ms"
        updateBestReactionText()
    }


    private fun updateBestReactionText() {
        bestReactionText.text = "최고: ${bestReactionTime}ms"
    }

    // 스테이지별 통과 기준(반응속도, ms) 계산 함수
    private fun getStageThreshold(stage: Int): Int {
        val a = 398.0 / 39.0
        val b = 371.0 - a * 28.0

        val minAge = 10
        val maxAge = 50
        val totalStages = 20

        // 스테이지 1일 때 50세, 20일 때 10세로 설정
        val agePerStage = (maxAge - minAge).toDouble() / (totalStages - 1)
        val x = maxAge - (stage - 1) * agePerStage  // stage가 높을수록 젊은 기준

        val threshold = (a * x + b).toInt()
        return threshold
    }

    // 느슨한 기준 (1.8배)
    private fun getStageThresholdRelaxed(stage: Int): Int {
        return (getStageThreshold(stage) * 1.8).toInt()
    }

    override fun onResume() {
        super.onResume()
        val rootLayout = findViewById<View>(R.id.reaction_game_root_layout)  // XML의 루트 id
        WeatherUtil.applyWeatherBackground(this, rootLayout)
    }

}
