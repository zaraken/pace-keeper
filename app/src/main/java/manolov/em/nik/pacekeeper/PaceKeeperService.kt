package manolov.em.nik.pacekeeper

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

class PaceKeeperService : Service(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        val TAG = "PaceKeeperService"
        val REFRESH_PERIOD: Long = 60 * 1000 // wakeup the device every 60 seconds
        val STANDING_THRESHOLD = 1F
    }

    val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepCounterSensor by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) as Sensor }
    val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) as SharedPreferences }
    val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator } // TODO could be null
    val alarmManager by lazy { applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    var registered: Boolean = false // flag to avoid registering for sensor data more than once
    var listening = false

    var timestampDequeue: MutableList<Pair<Long, Float>> = arrayListOf() // timestamp, count
    var startTimeOffset: Long = 0
    var minPace: Float = 0F
    var bestPace: Float = 0F
    var buzStartTimestamp: Long = 0

    // Preferences ---------------
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG, "onSharedPreferenceChanged " + key)
        when (key) {
            getString(R.string.setting_key_active) -> {
                listening = sharedPreferences?.getBoolean(key, false) ?: false
                configAlarmManager(listening)
            }
            getString(R.string.setting_key_min_step_freq) -> {
                minPace = (sharedPreferences
                        ?.getString(getString(R.string.setting_key_min_step_freq), "0.0")
                        ?: "0.0").toFloat()

            }
            getString(R.string.setting_key_best_pace) -> {
                bestPace = (sharedPreferences
                        ?.getString(getString(R.string.setting_key_best_pace), "0.0")
                        ?: "0.0").toFloat()
            }
        }
    }

    // Sensors ------------------
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    } // do nothing

    override fun onSensorChanged(event: SensorEvent?) {

        val currMills = System.currentTimeMillis()
        val eventDeliveryDelay = currMills - (event?.timestamp ?: 0) / 1000000 - startTimeOffset
        if (startTimeOffset == 0L) startTimeOffset = eventDeliveryDelay

        val currentPace = getNewPace(event?.timestamp ?: 0, event?.values?.get(0) ?: -1F)

        Log.d(TAG, "onSensorChanged "
                + " values=" + event?.values?.size
                + " first=" + (event?.values?.get(0) ?: "")
                + " delivery_delay=" + eventDeliveryDelay
                //+ " currMillis=" + currMills
                //+ " timestamp=" + (event?.timestamp ?: 0)
                + " pace=" + currentPace)

        if (listening // enabled in preferences
                && eventDeliveryDelay < 2000 // delivered event is current enough
                && currentPace < minPace // person/device moving with less than specified preference
                && currentPace > STANDING_THRESHOLD // but not staying
                && !buzzing()) {
            buzz()
        }
    }

    // this +

    fun buzz() {
        if (!buzzing()) {
            val stepPeriod: Long = (1000 * 1 / minPace).toLong() // period in millisec
            val buzzLength: Long = 200 // in millisec
            // buzz 3 times
            val pattern = longArrayOf(0, buzzLength, (stepPeriod + buzzLength), buzzLength, (stepPeriod + buzzLength), buzzLength)
            vibrator.vibrate(pattern, -1) //?: Log.d(TAG, "vibrator is NULL")
            buzStartTimestamp = System.currentTimeMillis()
        }
    }

    fun buzzing(): Boolean {
        val buzzPeriod: Long = 6 * (1000 * 1 / minPace).toLong() // period in millisec
        val timeBetweenBuzz = buzzPeriod * 10 // don't want it buzzing all the time
        return if (System.currentTimeMillis() < buzStartTimestamp + timeBetweenBuzz) true else false
    }

    /**
     * Add new stepcount info to a queue
     * Calculate the pace based on the last 3 steps
     *
     * @param[timestamp]
     * @param[stepcount]
     * @return pace in steps per second
     */
    fun getNewPace(timestamp: Long, stepcount: Float): Float {
        val NS2S = 1000000000.0f
        if (timestamp != 0L && stepcount != -1F) {
            timestampDequeue.add(Pair(timestamp, stepcount))
            while (timestampDequeue.size > 3) {
                timestampDequeue.removeAt(0)
            }
        }
        val (stampfirst, countfirst) = timestampDequeue.first()
        val (stamplast, countlast) = timestampDequeue.last()
        val newPace: Float = if (stamplast - stampfirst == 0L) 0F else (countfirst - countlast) * NS2S / (stampfirst - stamplast)
        if (newPace > bestPace) recordBestPace(newPace)
        return newPace
    }

    fun recordBestPace(pace: Float) {
        bestPace = pace
        val editor = sharedPreferences.edit()
        editor.putString(getString(R.string.setting_key_best_pace), pace.toString())
        editor.apply()
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
            registerForPreferences()
            registerForStepCounter()
            initFromPreferences()
            configAlarmManager(listening)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        // TODO : ? keep it running for a bit so that sensor data can be retrieved
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
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
        listening = sharedPreferences.getBoolean(getString(R.string.setting_key_active), false)
        minPace = sharedPreferences.getString(getString(R.string.setting_key_min_step_freq), "0.0").toFloat()
        bestPace = sharedPreferences.getString(getString(R.string.setting_key_best_pace), "0.0").toFloat()
    }

    fun configAlarmManager(on_off: Boolean) {
        Log.d(TAG, "configAlarmManager")
        val intent = Intent(applicationContext, PaceKeeperService::class.java)
        val pendingIntent = PendingIntent.getService(applicationContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
        alarmManager.cancel(pendingIntent)
        if (on_off) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30 * 1000, REFRESH_PERIOD, pendingIntent)
        }
    }

    fun isSupportedStepCounter(): Boolean {
        val currentApiVersion = android.os.Build.VERSION.SDK_INT
        val packageManager = applicationContext.packageManager
        return currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
                && packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
    }
}
