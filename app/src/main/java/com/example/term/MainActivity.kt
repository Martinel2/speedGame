package com.example.term

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.installations.FirebaseInstallations
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnSuccessListener

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var prefs: SharedPreferences
    private var startStageLevel: Int = 1

    private lateinit var profileImage: ImageView
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var nicknameDisplay: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var profileImageUri: Uri

    private val measureSkillLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newStage = result.data?.getIntExtra("new_stage_level", 1) ?: 1
            startStageLevel = newStage
            getSharedPreferences("game_prefs", MODE_PRIVATE)
                .edit().putInt("start_stage_level", startStageLevel).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("game_prefs", MODE_PRIVATE)

        drawerLayout = findViewById(R.id.main_root_layout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        profileImage = findViewById(R.id.image_profile)
        nicknameDisplay = findViewById(R.id.text_nickname_display)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                val uri: Uri? = it.data!!.data

                // 이미지 경로 확인
                if (uri != null) {
                    profileImageUri = uri
                    profileImage.setImageURI(uri)

                    // 이미지 저장 (Base64로 인코딩하여 저장)
                    saveProfileImage(uri)  // 프로필 이미지 저장
                } else {
                    Toast.makeText(this, "이미지를 선택할 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }


        findViewById<Button>(R.id.change_image_button).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }

        findViewById<Button>(R.id.set_nickname_button).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showNicknameDialog()
        }

        FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val clientId = task.result
                getSharedPreferences("game_prefs", MODE_PRIVATE)
                    .edit().putString("client_id", clientId).apply()
            }
        }

        // FusedLocationProviderClient 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 위치 권한 확인 및 요청
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getUserLocationAndFetchWeather()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }

    // 위치 권한을 요청한 후 결과 처리
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocationAndFetchWeather()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 위치 정보 가져오기
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getUserLocationAndFetchWeather() {
        fusedLocationClient.lastLocation.addOnSuccessListener(this, OnSuccessListener { location ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                fetchWeatherData(lat, lon)
            } else {
                Toast.makeText(this, "위치 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 날씨 API 호출
    private fun fetchWeatherData(lat: Double, lon: Double) {
        val apiKey = "2a7cfcf0ed690a45db83a205cd467520" // 실제 API 키로 교체
        val url = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$apiKey&lang=kr&units=metric"

        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->

                val cityName = response.getString("name")
                val weather = response.getJSONArray("weather")
                    .getJSONObject(0)
                    .getString("main")
                val temp = response.getJSONObject("main")
                    .getDouble("temp")

                findViewById<TextView>(R.id.text_weather).text = "$cityName, ${String.format("%.1f", temp)}도, ${translateWeather(weather)}"

                val rootLayout = findViewById<View>(R.id.main_root_layout)
                val bgColor = when (weather) {
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
            },
            { error ->
                Toast.makeText(this, "날씨 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
            })

        queue.add(request)
    }

    // 날씨 상태 번역
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
    // 닉네임과 이미지를 SharedPreferences에 저장
    private fun saveUserNickname(nickname: String) {
        val sharedPreferences = getSharedPreferences("user_data", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // 닉네임 저장
        editor.putString("nickname", nickname)

        // 변경 사항 저장
        editor.apply()
    }
    private fun saveProfileImage(profileImageUri: Uri) {
        val sharedPreferences = getSharedPreferences("user_data", MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // 이미지 URI를 Base64로 인코딩하여 저장 (이미지의 Uri를 문자열로 변환)
        val encodedImage = encodeImageToBase64(profileImageUri)
        editor.putString("profile_image", encodedImage)

        // 변경 사항 저장
        editor.apply()
    }
    // 이미지 Uri를 Base64로 인코딩
    private fun encodeImageToBase64(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val byteArray = inputStream?.readBytes()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
    // 이미지를 Base64로 인코딩하여 저장된 이미지를 불러오는 메서드
    private fun decodeBase64ToImage(encodedImage: String): Bitmap {
        val decodedBytes = Base64.decode(encodedImage, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }
    // 닉네임과 이미지를 SharedPreferences에서 불러오기
    private fun loadUserData() {
        val sharedPreferences = getSharedPreferences("user_data", MODE_PRIVATE)
        val nickname = sharedPreferences.getString("nickname", "기본 닉네임")
        val encodedImage = sharedPreferences.getString("profile_image", null)

        // 닉네임 설정
        nicknameDisplay.text = nickname

        // 이미지 설정 (Base64로 인코딩된 이미지를 디코딩하여 설정)
        if (encodedImage != null) {
            val decodedImage = decodeBase64ToImage(encodedImage)
            profileImage.setImageBitmap(decodedImage)
        }
    }



    // 닉네임 설정 팝업
    // 닉네임 설정 팝업
    private fun showNicknameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nickname, null)
        val editNickname = dialogView.findViewById<EditText>(R.id.edit_nickname_popup)
        val generateButton = dialogView.findViewById<Button>(R.id.button_generate_random_nickname)

        val dialog = AlertDialog.Builder(this)
            .setTitle("닉네임 설정")
            .setView(dialogView)
            .setPositiveButton("입력") { _, _ ->
                val nickname = editNickname.text.toString().trim()
                nicknameDisplay.text = "닉네임: $nickname"
                saveUserNickname(nickname)
            }
            .setNegativeButton("취소", null)
            .create()

        generateButton.setOnClickListener {
            generateRandomNickname { nickname ->
                editNickname.setText(nickname)
                nicknameDisplay.text = "닉네임: $nickname"
                saveUserNickname(nickname)
            }
        }

        dialog.show()
    }


    // 무작위 닉네임 생성
    private fun generateRandomNickname(onResult: (String) -> Unit) {
        val url = "https://randomuser.me/api/"
        val queue = Volley.newRequestQueue(this)

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                val nickname = response
                    .getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("name")
                    .getString("first")
                onResult(nickname)
            },
            { error ->
                Toast.makeText(this, "API 오류: ${error.message}", Toast.LENGTH_SHORT).show()
            })

        queue.add(request)
    }

    // 메뉴 업데이트
    private fun updateMenuBasedOnPrefs() {
        val bestTime = prefs.getLong("best_reaction_time", Long.MAX_VALUE)
        val isFirstUser = bestTime == Long.MAX_VALUE

        startStageLevel = prefs.getInt("start_stage_level", 1)
        val btnStartGame = findViewById<Button>(R.id.start_button)
        btnStartGame.setOnClickListener {
            val intent = Intent(this, ReactionGameActivity::class.java)
            intent.putExtra("stage_level", startStageLevel)
            startActivity(intent)
        }

        val btnRetest = findViewById<Button>(R.id.measure_button)
        btnRetest.setOnClickListener {
            val intent = Intent(this, MeasureSkillActivity::class.java)
            measureSkillLauncher.launch(intent)
        }

        val btnPractice = findViewById<Button>(R.id.stage_experience_button)
        btnPractice.setOnClickListener {
            startActivity(Intent(this, StageListActivity::class.java))
        }

        findViewById<Button>(R.id.help_button).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("도움말")
                .setMessage("게임 방법: 10초마다 반응속도를 측정해 스테이지를 통과하세요!")
                .setPositiveButton("확인", null)
                .show()
        }

        if (isFirstUser) {
            btnStartGame.visibility = View.GONE
            btnPractice.visibility = View.GONE
            btnRetest.text = "실력 측정 시작"
        } else {
            btnStartGame.visibility = View.VISIBLE
            btnPractice.visibility = View.VISIBLE
            btnRetest.text = "실력 재측정"
        }
    }

    // 앱이 활성화될 때마다 데이터 복원
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResume() {
        super.onResume()
        loadUserData() // onResume에서 호출하여 데이터 복원
        updateMenuBasedOnPrefs()

        val weatherText = findViewById<TextView>(R.id.text_weather)
        val rootLayout = findViewById<View>(R.id.main_root_layout)
        WeatherUtil.applyWeatherBackground(this, rootLayout, weatherText)
    }
    override fun onPause() {
        super.onPause()


    }


}
