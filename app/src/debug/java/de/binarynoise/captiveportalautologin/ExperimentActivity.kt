@file:Suppress("MemberVisibilityCanBePrivate")

package de.binarynoise.captiveportalautologin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat

class ExperimentActivity : Activity() {
    
    val log: TextView by lazy { findViewById(R.id.log) }
    val text1: TextView by lazy { findViewById(R.id.text1) }
    val text2: TextView by lazy { findViewById(R.id.text2) }
    val box1: CheckBox by lazy { findViewById(R.id.box1) }
    val box2: CheckBox by lazy { findViewById(R.id.box2) }
    val box3: CheckBox by lazy { findViewById(R.id.box3) }
    val button1: Button by lazy { findViewById(R.id.button1) }
    val button2: Button by lazy { findViewById(R.id.button2) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_experiment)
        
    }
}
