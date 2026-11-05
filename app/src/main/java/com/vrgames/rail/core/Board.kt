package com.vrgames.rail.core

import kotlin.math.cos
import kotlin.math.sin

/** Where the board stands in front of the seated player, in the room's meters. */
object Board {
    const val CELL = 0.08
    const val TILT = 18.0
    /** The board's middle: height above the floor, and how far in front of the player. */
    const val Y = 0.72
    const val AHEAD = 0.93

    fun localX(x: Double) = (x - Play.W / 2.0) * CELL
    fun localZ(y: Double) = (y - Play.H / 2.0) * CELL

    /** A point of the board ([x], [y] in cells, [up] meters out of it) in the room. */
    fun world(x: Double, y: Double, up: Double = 0.0): Vec3 {
        val a = Math.toRadians(TILT)
        val lz = localZ(y)
        return Vec3(localX(x), Y + up * cos(a) - lz * sin(a), -AHEAD + up * sin(a) + lz * cos(a))
    }

    /** Where a ray from [o] along [d] meets the board, in cells, or null. */
    fun hit(o: Vec3, d: Vec3): Pair<Double, Double>? {
        val a = Math.toRadians(TILT)
        val n = Vec3(0.0, cos(a), sin(a))
        val p0 = Vec3(0.0, Y, -AHEAD)
        val den = d.x * n.x + d.y * n.y + d.z * n.z
        if (kotlin.math.abs(den) < 1e-6) return null
        val u = ((p0.x - o.x) * n.x + (p0.y - o.y) * n.y + (p0.z - o.z) * n.z) / den
        if (u < 0) return null
        val h = Vec3(o.x + d.x * u, o.y + d.y * u, o.z + d.z * u)
        val lz = (h.y - Y) * -sin(a) + (h.z + AHEAD) * cos(a)
        return h.x / CELL + Play.W / 2.0 to lz / CELL + Play.H / 2.0
    }
}
