package com.example.term

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.firebase.installations.FirebaseInstallations

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var prefs: SharedPreferences
    private var startStageLevel: Int = 1

    private lateinit var profileImage: ImageView
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var nicknameDisplay: TextView

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
                profileImage.setImageURI(uri)
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
    }

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
            }
            .setNegativeButton("취소", null)
            .create()

        generateButton.setOnClickListener {
            generateRandomNickname { nickname ->
                editNickname.setText(nickname)
                nicknameDisplay.text = "닉네임: $nickname"
            }
        }

        dialog.show()
    }

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




    override fun onResume() {
        super.onResume()
        updateMenuBasedOnPrefs()

        val weatherText = findViewById<TextView>(R.id.text_weather)
        val rootLayout = findViewById<View>(R.id.main_root_layout)
        WeatherUtil.applyWeatherBackground(this, rootLayout, weatherText)
    }

}
