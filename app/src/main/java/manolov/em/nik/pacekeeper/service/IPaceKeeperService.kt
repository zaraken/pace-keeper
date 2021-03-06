package manolov.em.nik.pacekeeper.service

/**
 * Created by Nik on 2016-11-12.
 */
interface IPaceKeeperService {
    fun configAlarmManager(listening: Boolean, startDelay: Long, refreshPeriod: Long)
    fun recordBestPace(pace: Float)
    fun buzz(pattern: LongArray, repeat: Int)
    fun ring(repeat: Int, interval: Int)
}