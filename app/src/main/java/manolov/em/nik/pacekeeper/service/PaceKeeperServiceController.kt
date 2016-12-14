package manolov.em.nik.pacekeeper.service

import android.os.SystemClock
import android.util.Log
import manolov.em.nik.pacekeeper.R

/**
 * Created by Nik on 2016-11-12.
 */
class PaceKeeperServiceController{

    companion object {
        val TAG = PaceKeeperServiceController::class.java.simpleName as String
        val REFRESH_PERIOD: Long = 60 * 1000 // wakeup the device every 60 seconds
        val STANDING_THRESHOLD = 1F
        val ALARM_START_DELAY = 30 * 1000
    }

    var service: IPaceKeeperService? = null
    val startTimeOffset: Long by lazy{System.currentTimeMillis() - SystemClock.elapsedRealtime()}

    var listening = false
    var minPace = 0F
    var bestPace = 0F
    var buzStartTimestamp: Long = 0
    var timestampDequeue: MutableList<Pair<Long, Float>> = arrayListOf() // timestamp, count

    fun bindService(serv: IPaceKeeperService){
        service = serv
        Log.d(TAG, "bindSerivce " + service)
    }

    fun unbindService(serv: IPaceKeeperService){
        Log.d(TAG, "unbindSerivce " + serv)
        service = null
    }


    fun onPreferenceChnaged(key: Int, value: Any?){
        Log.d(PaceKeeperService.TAG, "onSharedPreferenceChanged " + key)
        when (key) {
            R.string.setting_key_active -> {
                listening = (value as Boolean?)?:false
                service?.configAlarmManager(listening, System.currentTimeMillis() + ALARM_START_DELAY, REFRESH_PERIOD)
            }
            R.string.setting_key_min_step_freq -> {
                if (value is String) {
                    try {
                        minPace = value.toFloat()
                    } catch (e: NumberFormatException) {
                    } // don't change
                }
            }
            R.string.setting_key_best_pace -> {
                if (value is String) {
                    try {
                        bestPace = value.toFloat()
                    } catch (e: NumberFormatException) {
                    } // don't change
                }
            }
        }
    }

    fun onStep(timestamp: Long?, value: Float?){
        if (timestamp == null || value == null) return
        val currMills = System.currentTimeMillis()
        val eventDeliveryDelay = currMills - timestamp / 1000000 - startTimeOffset
        // if (startTimeOffset == 0L) startTimeOffset = eventDeliveryDelay

        val currentPace = getNewPace(timestamp, value)

        Log.d(TAG, "onSensorChanged "
                + " value=" + value
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
        if (newPace > bestPace) {
            bestPace = newPace
            service?.recordBestPace(bestPace)
        }
        return newPace
    }

    fun buzzing(): Boolean {
        Log.d(TAG, "buzzing")
        val buzzPeriod: Long = 6 * (1000 * 1 / minPace).toLong() // period in millis
        val timeBetweenBuzz = buzzPeriod * 10 // don't want it buzzing all the time
        return (System.currentTimeMillis() < buzStartTimestamp + timeBetweenBuzz)
    }

    fun buzz() {
        if (!buzzing()) {
            Log.d(TAG, "buzz")
            val stepPeriod: Long = (1000 * 1 / minPace).toLong() // period in millis
            val buzzLength: Long = 200 // in millis
            // buzz 3 times
            val pattern = longArrayOf(0
                            , buzzLength
                            , (stepPeriod + buzzLength)
                            , buzzLength
                            , (stepPeriod + buzzLength)
                            , buzzLength)
            service?.buzz(pattern, -1) //?: Log.d(TAG, "vibrator is NULL")
            // TODO: test synchronization between buzz and ring. Phone's vibration broken ...
            service?.ring(0, 0)

            buzStartTimestamp = System.currentTimeMillis()
        }
    }

}
