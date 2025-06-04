package com.example.term

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView

    private var progressStatus = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)

        Thread {
            while (progressStatus < 100) {
                progressStatus++
                Thread.sleep(30)  // 속도 조절 (30ms 간격)

                handler.post {
                    progressBar.progress = progressStatus
                    progressText.text = "$progressStatus%"
                }
            }

            // 100% 되면 메인 화면으로 이동
            handler.post {
                startActivity(Intent(this, MainActivity::class.java))
                finish()  // SplashActivity 종료
            }
        }.start()
    }

}
