package com.example.water

import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

//class SpiderOverlayService : Service() {
//    private lateinit var windowManager: WindowManager
//    private lateinit var overlayView: View
//    private var isViewAdded = false // Safety flag to prevent crashes
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onCreate() {
//        super.onCreate()
//        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
//
//        // 1. Create the themed wrapper
//        val themedContext = android.view.ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
//
//        // 2. Inflate using the themed wrapper
//        overlayView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_spider, null)
//
//        val params = WindowManager.LayoutParams(
//            WindowManager.LayoutParams.WRAP_CONTENT,
//            WindowManager.LayoutParams.WRAP_CONTENT,
//            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
//            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
//                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
//                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
//            PixelFormat.TRANSLUCENT
//        )
//
//        // Centers the view at the top of the screen (under the notch)
//        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
//        params.y = 100
//
//        // 3. CHECK IF THE SCREEN IS LOCKED
//        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
//        val isLocked = keyguardManager.isKeyguardLocked
//
//        // Only add the ghost view if the phone is actively unlocked
//        if (!isLocked) {
//            windowManager.addView(overlayView, params)
//            isViewAdded = true
//        }
//
//        // 5. Cleanup after 11 seconds
//        Handler(Looper.getMainLooper()).postDelayed({
//            stopSelf()
//        }, 11000)
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        // Safely remove the view ONLY if it was actually added to the screen
//        if (::overlayView.isInitialized && isViewAdded) {
//            windowManager.removeView(overlayView)
//        }
//    }
//}

class SpiderOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var isViewAdded = false // Safety flag to prevent crashes

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 1. Create the themed wrapper
        val themedContext = android.view.ContextThemeWrapper(
            this,
            androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar
        )

        // 2. Inflate using the themed wrapper
        overlayView = LayoutInflater.from(themedContext).inflate(R.layout.overlay_spider, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        // Centers the view at the top of the screen (under the notch)
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 100

        // 3. CHECK IF THE SCREEN IS LOCKED
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        // Only add the ghost view if the phone is actively unlocked
        if (!isLocked) {
            windowManager.addView(overlayView, params)
            isViewAdded = true
        }

        // 4. Cleanup after 11 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
        }, 11000)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safely remove the view ONLY if it was actually added to the screen
        if (::overlayView.isInitialized && isViewAdded) {
            windowManager.removeView(overlayView)
        }
    }
}