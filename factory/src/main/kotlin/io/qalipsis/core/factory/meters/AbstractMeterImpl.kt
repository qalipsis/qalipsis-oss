//package io.qalipsis.core.factory.meters
//
//import io.aerisconsulting.catadioptre.KTestable
//import java.util.TreeSet
//import java.util.concurrent.TimeUnit
//import java.util.concurrent.atomic.LongAdder
//import kotlin.Comparator
//
//abstract class AbstractMeterImpl<T: Number>() {
//
//    private val count = LongAdder()
//
//    private val total = LongAdder()
//
//    private val bucket = mutableListOf<T>()
//
//    private val C0 = 1L
//
//    private val C1 = C0 * 1000L
//
//    private val C2 = C1 * 1000L
//
//    private val C3 = C2 * 1000L
//
//    private val C4 = C3 * 60L
//
//    private val C5 = C4 * 60L
//
//    private val C6 = C5 * 24L
//
//    @KTestable
//    private var maxBucket = TreeSet(
//        Comparator<T> { a, b -> if (b!! > a!!) 1 else -1 }
//    )
//
//    fun count() = count.toLong()
//
//    fun totalTime(unit: TimeUnit?) = nanosToUnitConverter(total.toLong(), unit)
//
//    fun max(unit: TimeUnit?): Double {
//        return nanosToUnitConverter(maxBucket.first() as Long, unit)
//    }
//
//    fun record(amount: T, unit: TimeUnit?) {
//        if (amount >= 0) {
//            //record the amount
//            //They did a rotate before adding to the bucket
//            val amountInNanos = TimeUnit.NANOSECONDS.convert(amount, unit)
//            maxBucket.add(amountInNanos)
//            //histogram here is like the bucket
//            //@TODO not sure about the histogram.record. Basically it adds a value to an AtomicLongArrayBucket.
//            //histogram.recordLong(TimeUnit.NANOSECONDS.convert(amount, unit))
//            count.add(1)
//            total.add(amountInNanos)
//            bucket.add(amountInNanos)
//        }
//    }
//
//    suspend fun <T> record(block: suspend () -> T): T {
//        val preExecutionTimeInNanos = System.nanoTime()
//        return try {
//            block()
//        } finally {
//            val postExecutionTimeInNanos = System.nanoTime()
//            record(postExecutionTimeInNanos - preExecutionTimeInNanos, TimeUnit.NANOSECONDS)
//        }
//    }
//
//
//    /**
//     * [0-1]
//     */
//    fun percentile(percentile: Double, unit: TimeUnit?): Double {
//        //include logic to sort once
////        bucket.sort()
////        val index = percentile * (bucket.size - 1)
////        val integerIndex = index.toInt()
////        println(bucket)
////        println("IND $index $integerIndex")
////        val percentileValue = if (integerIndex.toDouble() == index) {
////            bucket[integerIndex].toDouble()
////        } else {
////            println("In else")
////            val lowIndex = index.toInt()
////            val highIndex = lowIndex + 1
////            val lowValue = bucket[integerIndex]
////            val highValue = if (integerIndex + 1 < bucket.size) bucket[integerIndex + 1] else bucket.last()
////            println("GIBBERISHt" + lowValue + (highValue - lowValue) * (index - lowIndex))
////            lowValue + ((highValue - lowValue) * (index - integerIndex))
////            (lowValue + (index - integerIndex)) * (highValue - lowValue)
//        bucket.sort()
//        println(bucket)
//        val index = Math.ceil(percentile * bucket.size).toInt()
//        println(index)
//        return nanosToUnitConverter(bucket[index - 1], unit)
////        return nanosToUnitConverter(percentileValue.toLong(), unit)
//    }
//
//    /**
//     * Converts the total time to the specified format. Defaults to nanoseconds when not specified
//     */
//    //@TODO improve implementation for the converter
//    private fun nanosToUnitConverter(totalTimeInNanos: Long, unit: TimeUnit?): Double {
//        println("TOT TIME $totalTimeInNanos")
//        return when (unit) {
//            TimeUnit.NANOSECONDS -> totalTimeInNanos.toDouble()
//            TimeUnit.MICROSECONDS -> totalTimeInNanos / (C1 / C0).toDouble()
//            TimeUnit.MILLISECONDS -> totalTimeInNanos / (C2 / C0).toDouble()
//            TimeUnit.SECONDS -> totalTimeInNanos / (C3 / C0).toDouble()
//            TimeUnit.MINUTES -> totalTimeInNanos / (C4 / C0).toDouble()
//            TimeUnit.HOURS -> totalTimeInNanos / (C5 / C0).toDouble()
//            TimeUnit.DAYS -> totalTimeInNanos / (C6 / C0).toDouble()
//            null -> totalTimeInNanos.toDouble()
//            else -> {
//                throw Exception("Unsupported time unit format $unit")
//            }
//        }
//    }
//}