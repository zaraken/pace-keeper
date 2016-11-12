package manolov.em.nik.pacekeeper

/**
 * Created by Nik on 2016-11-12.
 */
interface IPaceKeeperService {
    fun configAlarmManager(listening: Boolean, startDelay: Long, refreshPeriod: Long)
    fun recordBestPace(pace: Float)
    fun buzz(pattern: LongArray, repeat: Int)
}