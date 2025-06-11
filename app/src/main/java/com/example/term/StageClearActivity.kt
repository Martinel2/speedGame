package com.example.term

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kakao.sdk.common.util.KakaoCustomTabsClient
import com.kakao.sdk.share.ShareClient
import com.kakao.sdk.share.WebSharerClient
import com.kakao.sdk.template.model.Content
import com.kakao.sdk.template.model.FeedTemplate
import com.kakao.sdk.template.model.Link
import java.io.File
import java.io.FileOutputStream


// 스테이지 성공 시 호출되는 화면 (사용자 확인 후 게임 재시작)
class StageClearActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_clear)

        val stageLevel = intent.getIntExtra("stage_level", 1)
        val predictedAge = intent.getStringExtra("predicted_age") ?: "알 수 없음"
        val isBest = intent.getBooleanExtra("is_best", false)
        val bestReactionTime = intent.getLongExtra("best_reaction_time", -1L)

        findViewById<TextView>(R.id.stageClearText).text = "Stage $stageLevel Clear!"
        findViewById<TextView>(R.id.predictedAgeText).text = "예상 반응속도 연령: $predictedAge"
        if (isBest) {
            findViewById<TextView>(R.id.predictedAgeText).append("\n최고 기록 갱신!\n최고 반응속도: ${bestReactionTime}ms")
        } else if (bestReactionTime > 0) {
            findViewById<TextView>(R.id.predictedAgeText).append("\n최고 반응속도: ${bestReactionTime}ms")
        }

        // 다시 게임으로 돌아가기
        findViewById<Button>(R.id.nextStageButton).setOnClickListener {
            val intent = Intent(this, ReactionGameActivity::class.java)
            intent.putExtra("stage_level", stageLevel + 1) // 다음 Stage로
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.shareKakaoButton).setOnClickListener {
            val rootView = window.decorView.rootView

            // View가 완전히 그려진 후 캡처하도록 post 사용
            rootView.post {
                val bitmap =
                    Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                rootView.draw(canvas)

                // 2. Bitmap을 파일로 저장
                val file = File(cacheDir, "result_share.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                // 카카오톡 설치여부 확인
                if (ShareClient.instance.isKakaoTalkSharingAvailable(this)) {
                    // 카카오 이미지 서버로 업로드
                    ShareClient.instance.uploadImage(file) { imageUploadResult, error ->
                        if (error != null) {
                            Log.e("KakaoShare", "이미지 업로드 실패", error)
                        } else if (imageUploadResult != null) {
                            val imageUrl =
                                imageUploadResult.infos.original?.url ?: return@uploadImage

                            // FeedTemplate 생성
                            val feedTemplate = FeedTemplate(
                                content = Content(
                                    title = "반응속도 게임 결과",
                                    description = "내 반응속도 결과를 확인해보세요!",
                                    imageUrl = imageUrl,
                                    link = Link(
                                        webUrl = "https://github.com/Martinel2/speedGame",
                                        mobileWebUrl = "https://github.com/Martinel2/speedGame"
                                    )
                                )
                            )

                            // 공유 실행
                            ShareClient.instance.shareDefault(
                                this,
                                feedTemplate
                            ) { result, shareError ->
                                if (shareError != null) {
                                    Log.e("KakaoShare", "카카오톡 공유 실패", shareError)
                                } else if (result != null) {
                                    startActivity(result.intent)
                                }
                            }
                        }
                    }
                } else {
                    // 카카오톡 미설치: 웹 공유
                    // 이미지 먼저 업로드
                    ShareClient.instance.uploadImage(file) { imageUploadResult, error ->
                        if (error != null) {
                            Log.e("KakaoShare", "웹용 이미지 업로드 실패", error)
                        } else if (imageUploadResult != null) {
                            val imageUrl =
                                imageUploadResult.infos.original?.url ?: return@uploadImage

                            val feedTemplate = FeedTemplate(
                                content = Content(
                                    title = "반응속도 게임 결과",
                                    description = "내 반응속도 결과를 확인해보세요!",
                                    imageUrl = imageUrl,
                                    link = Link(
                                        webUrl = "https://github.com/Martinel2/speedGame",
                                        mobileWebUrl = "https://github.com/Martinel2/speedGame"
                                    )
                                )
                            )

                            val sharerUrl = WebSharerClient.instance.makeDefaultUrl(feedTemplate)

                            try {
                                KakaoCustomTabsClient.openWithDefault(this, sharerUrl)
                            } catch (e: UnsupportedOperationException) {
                                try {
                                    KakaoCustomTabsClient.open(this, sharerUrl)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(this, "브라우저가 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        val rootLayout = findViewById<View>(R.id.reaction_game_root_layout)  // XML의 루트 id
        WeatherUtil.applyWeatherBackground(this, rootLayout)
    }

}
