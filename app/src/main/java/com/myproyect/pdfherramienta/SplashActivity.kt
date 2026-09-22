package com.myproyect.pdfherramienta

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val DURACION = 2600L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PDFBoxResourceLoader.init(applicationContext)

        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing || isDestroyed) {
                return@postDelayed
            }

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }, DURACION)
    }
}