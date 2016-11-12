package manolov.em.nik.pacekeeper.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.Vibrator
import android.preference.PreferenceManager
import android.util.Log
import manolov.em.nik.pacekeeper.R

class PaceKeeperService : Service(), IPaceKeeperService, SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        val TAG = PaceKeeperService::class.java.simpleName as String
    }

    val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepCounterSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) as Sensor }
    val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) as SharedPreferences }
    val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val alarmManager by lazy { applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var registered: Boolean = false // flag to avoid registering for sensor data more than once

    lateinit var controller: PaceKeeperServiceController

    // Preferences ---------------
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG, "onSharedPreferenceChanged " + key)

        when (key) {
            getString(R.string.setting_key_active) -> {
                controller.onPreferenceChnaged(R.string.setting_key_active, sharedPreferences?.getBoolean(key, false))
            }
            getString(R.string.setting_key_min_step_freq) -> {
                controller.onPreferenceChnaged(R.string.setting_key_min_step_freq, sharedPreferences
                        ?.getString(key, "0.0"))
            }
            getString(R.string.setting_key_best_pace) -> {
                controller.onPreferenceChnaged(R.string.setting_key_best_pace, sharedPreferences?.getString(key, "0.0"))
            }
        }
    }

    // Sensors ------------------
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    } // do nothing

    override fun onSensorChanged(event: SensorEvent?) {
        controller.onStep(event?.timestamp, event?.values?.get(0))
    }

    // IPaceKeeperService
    override fun recordBestPace(pace: Float) {
        val editor = sharedPreferences.edit()
        editor.putString(getString(R.string.setting_key_best_pace), pace.toString())
        editor.apply()
    }

    override fun buzz(pattern: LongArray, repeat: Int){
        //Log.d(TAG, "buzz $pattern $repeat")
        vibrator.vibrate(pattern, repeat)
    }

    override fun configAlarmManager(listening: Boolean, startDelay: Long, refreshPeriod: Long) {
        Log.d(TAG, "configAlarmManager")
        val intent = Intent(applicationContext, PaceKeeperService::class.java)
        val pendingIntent = PendingIntent.getService(applicationContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        alarmManager.cancel(pendingIntent)
        if (listening) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + startDelay, refreshPeriod, pendingIntent)
        }
    }

    // Service override  ----------------------
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        if (!isSupportedStepCounter()) {
            Log.d(TAG, "Step Counter not supported!")
            stopSelf()
        } else {
            controller = PaceKeeperServiceController()
            controller.bindService(this)

            registerForPreferences()
            registerForStepCounter()
            initFromPreferences()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        // TODO : ? keep it running for a bit so that sensor data can be retrieved
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        controller.unbindService(this)
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        sensorManager.unregisterListener(this, stepCounterSensor)
    }

    // this + ---------------------------------
    fun registerForStepCounter() {
        if (!registered)
            registered = true
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun registerForPreferences() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    fun initFromPreferences() {
        controller.onPreferenceChnaged(R.string.setting_key_active, sharedPreferences.getBoolean(getString(R.string.setting_key_active), false))
        controller.onPreferenceChnaged(R.string.setting_key_min_step_freq, sharedPreferences.getString(getString(R.string.setting_key_min_step_freq), "0.0"))
        controller.onPreferenceChnaged(R.string.setting_key_best_pace, sharedPreferences.getString(getString(R.string.setting_key_min_step_freq), "0.0"))
    }

    fun isSupportedStepCounter(): Boolean {
        val currentApiVersion = android.os.Build.VERSION.SDK_INT
        val packageManager = applicationContext.packageManager
        return currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
                && packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
    }
}
