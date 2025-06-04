package com.example.term

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley

object WeatherUtil {
    fun applyWeatherBackground(activity: Activity, rootLayout: View, textView: TextView? = null) {
        val apiKey = "2a7cfcf0ed690a45db83a205cd467520"
        val lat = 35.1796  // 부산 위도
        val lon = 129.0756 // 부산 경도
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&lang=kr&units=metric"

        val queue = Volley.newRequestQueue(activity)

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val weatherMain = response.getJSONArray("weather")
                    .getJSONObject(0).getString("main")
                val temp = response.getJSONObject("main").getDouble("temp")
                val city = response.getString("name")

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

                // 홈 화면(TextView가 주어졌을 때만) 텍스트 업데이트
                textView?.text = "$city, ${String.format("%.1f", temp)}도, ${translateWeather(weatherMain)}"
            },
            {
                textView?.text = "날씨 정보 없음"
            })

        queue.add(request)
    }

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
