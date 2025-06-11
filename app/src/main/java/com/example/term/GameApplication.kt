package com.example.term

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.util.Utility

// 앱 전역 Firebase 초기화 및 설정용 클래스
class GameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this) // Firebase 초기화
        KakaoSdk.init(this, "1198b945ca70b85006d9a95f33583c9a") // 카카오 개발자 콘솔에서 확인
        var keyHash = Utility.getKeyHash(this)
        Log.i("GlobalApplication", "$keyHash")
    }
}
