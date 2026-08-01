package com.scaso.drclawapp

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "No trace"
        val tv = TextView(this).apply {
            text = trace
            textSize = 11f
            setPadding(24, 48, 24, 24)
            setTextIsSelectable(true)
        }
        val sv = ScrollView(this).apply { addView(tv) }
        setContentView(sv)
    }
}
