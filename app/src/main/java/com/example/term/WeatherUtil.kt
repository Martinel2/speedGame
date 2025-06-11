package com.example.term

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnSuccessListener

object WeatherUtil {

    // 위치 정보를 받아오는 함수
    @androidx.annotation.RequiresPermission(anyOf = [android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION])
    fun applyWeatherBackground(activity: Activity, rootLayout: View, textView: TextView? = null) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)

        // 위치 권한 확인
        if (ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener(activity, OnSuccessListener { location ->
                if (location != null) {
                    // 위치 정보(위도, 경도) 가져오기
                    val lat = location.latitude
                    val lon = location.longitude

                    // 날씨 API 호출
                    fetchWeatherData(activity, lat, lon, rootLayout, textView)
                } else {
                    Toast.makeText(activity, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Toast.makeText(activity, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 날씨 API 호출
    private fun fetchWeatherData(activity: Activity, lat: Double, lon: Double, rootLayout: View, textView: TextView?) {
        val apiKey = "2a7cfcf0ed690a45db83a205cd467520" // 실제 API 키로 교체
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&lang=kr&units=metric"

        val queue = Volley.newRequestQueue(activity)

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val weatherMain = response.getJSONArray("weather")
                    .getJSONObject(0).getString("main")
                val temp = response.getJSONObject("main").getDouble("temp")
                val city = response.getString("name")

                // 배경 색상 업데이트
                val bgColor = when (weatherMain) {
                    "Clear" -> Color.parseColor("#FFF9C4")
                    "Clouds" -> Color.parseColor("#BDBDBD")
                    "Rain", "Drizzle" -> Color.parseColor("#90CAF9")
                    "Thunderstorm" -> Color.parseColor("#616161")
                    "Mist" -> Color.parseColor("#E1BEE7")
                    "Haze" -> Color.parseColor("#D7CCC8")
                    "Fog" -> Color.parseColor("#CFD8DC")
                    else -> Color.WHITE
                }
                rootLayout.setBackgroundColor(bgColor)

                // 텍스트 업데이트 (홈 화면에서만)
                textView?.text = "$city, ${String.format("%.1f", temp)}도, ${translateWeather(weatherMain)}"
            },
            { error ->
                textView?.text = "날씨 정보 없음"
            })

        queue.add(request)
    }

    // 날씨 상태 번역 (영어 → 한국어)
    private fun translateWeather(main: String): String {
        return when (main) {
            "Clear" -> "맑음"
            "Clouds" -> "흐림"
            "Rain" -> "비"
            "Drizzle" -> "이슬비"
            "Thunderstorm" -> "뇌우"
            "Mist" -> "안개"
            "Haze" -> "연무"
            "Fog" -> "짙은 안개"
            else -> main
        }
    }
}
