package commands.make

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class Ratio(
    val w: Int = 1,
    val h: Int = 1
) {
    fun calculateSize(baseSize: Int): Pair<Int, Int> {
        val r = max(this.w, this.h) / min(this.w, this.h).toDouble()
        val sW = floor(baseSize * r).toInt()
        val sH = floor(sW / r).toInt()

        return if (this.w < this.h) {
            sH to sW
        } else {
            sW to sH
        }
    }
}