package com.sitbreak.app.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat

/**
 * 被动站立验证：借助步数传感器（TYPE_STEP_COUNTER）累计计时区间内的步数，
 * 以此推断用户是否真的起身活动，解决「只能提醒、无法验证是否真站立」的信任问题。
 *
 * 设计原则（与隐私定位一致）：
 * - 全部在设备本地完成，不联网、不上传任何数据；
 * - 需要 ACTIVITY_RECOGNITION 权限（Android 10+ 为运行时危险权限）；未授予或无传感器时安全降级为「不可用」，不影响其他功能；
 * - 仅用于验证，不作为强制前置条件。
 */
object StandingValidator : SensorEventListener {

    private const val STAND_THRESHOLD_STEPS = 3

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var baselineSteps = -1f
    private var currentSteps = 0
    private var supported = false

    fun isSupported(): Boolean = supported

    fun hasPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun start(context: Context) {
        if (!hasPermission(context)) {
            supported = false
            return
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: run {
            supported = false
            return
        }
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run {
            supported = false
            return
        }
        sensorManager = sm
        stepSensor = sensor
        baselineSteps = -1f
        currentSteps = 0
        supported = true
        try {
            sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (_: Exception) {
            supported = false
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        stepSensor = null
        baselineSteps = -1f
        currentSteps = 0
        supported = false
    }

    /** 是否检测到足以说明「已起身活动」的步数。 */
    fun standingLikely(): Boolean = supported && currentSteps >= STAND_THRESHOLD_STEPS

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        if (baselineSteps < 0f) baselineSteps = total
        currentSteps = (total - baselineSteps).toInt().coerceAtLeast(0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
