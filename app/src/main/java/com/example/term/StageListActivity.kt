package com.example.term

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StageListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stage_list)

        val recyclerView = findViewById<RecyclerView>(R.id.stage_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val stages = (1..20).map { "Stage $it" }
        val adapter = StageListAdapter(stages) { stageName ->
            val stageNumber = stageName.removePrefix("Stage ").toIntOrNull() ?: 1
            val intent = Intent(this, ReactionGameActivity::class.java)
            intent.putExtra("stage_level", stageNumber)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResume() {
        super.onResume()
        val rootLayout = findViewById<View>(R.id.reaction_game_root_layout)  // XML의 루트 id
        WeatherUtil.applyWeatherBackground(this, rootLayout)
    }

}
