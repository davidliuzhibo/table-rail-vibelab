package com.vrgames.rail.render

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import kotlin.math.max
import kotlin.math.min

/** Collects quads and makes one mesh of them: one draw for a whole track or hill. */
class MeshBuilder {
    private val pos = ArrayList<Vector3>()
    private val nor = ArrayList<Vector3>()
    private val uvs = ArrayList<Vector2>()
    private val idx = ArrayList<Int>()
    private var x0 = Float.MAX_VALUE
    private var y0 = Float.MAX_VALUE
    private var z0 = Float.MAX_VALUE
    private var x1 = -Float.MAX_VALUE
    private var y1 = -Float.MAX_VALUE
    private var z1 = -Float.MAX_VALUE
    val empty get() = pos.isEmpty()

    private fun v(p: Vector3, n: Vector3, uv: Vector2) {
        pos += p
        nor += n
        uvs += uv
        x0 = min(x0, p.x); y0 = min(y0, p.y); z0 = min(z0, p.z)
        x1 = max(x1, p.x); y1 = max(y1, p.y); z1 = max(z1, p.z)
    }

    /** Four corners counterclockwise seen from outside. */
    fun quad(a: Vector3, b: Vector3, c: Vector3, d: Vector3, n: Vector3, ua: Vector2 = ZERO, ub: Vector2 = ZERO, uc: Vector2 = ZERO, ud: Vector2 = ZERO) {
        val k = pos.size
        v(a, n, ua); v(b, n, ub); v(c, n, uc); v(d, n, ud)
        idx += listOf(k, k + 1, k + 2, k, k + 2, k + 3)
    }

    /** As [quad], a normal at each corner: for smooth ground. */
    fun smooth(a: Vector3, b: Vector3, c: Vector3, d: Vector3, na: Vector3, nb: Vector3, nc: Vector3, nd: Vector3, ua: Vector2, ub: Vector2, uc: Vector2, ud: Vector2) {
        val k = pos.size
        v(a, na, ua); v(b, nb, ub); v(c, nc, uc); v(d, nd, ud)
        idx += listOf(k, k + 1, k + 2, k, k + 2, k + 3)
    }

    /** A flat piece of ground, x and z ranges at height y, its texture laid by board position. */
    fun ground(xa: Float, xb: Float, za: Float, zb: Float, y: Float, uvScale: Float) {
        quad(
            Vector3(xa, y, zb), Vector3(xb, y, zb), Vector3(xb, y, za), Vector3(xa, y, za), UP,
            Vector2(xa * uvScale + 0.5f, zb * uvScale + 0.5f), Vector2(xb * uvScale + 0.5f, zb * uvScale + 0.5f), Vector2(xb * uvScale + 0.5f, za * uvScale + 0.5f), Vector2(xa * uvScale + 0.5f, za * uvScale + 0.5f),
        )
    }

    /** A strip along a path at height [y], [w] wide, seen from above, with sides down [h]. */
    fun strip(path: List<Pair<Float, Float>>, off: Float, w: Float, y: Float, h: Float) {
        fun side(k: Int): Pair<Float, Float> {
            val a = path[max(k - 1, 0)]
            val b = path[min(k + 1, path.size - 1)]
            val dx = b.first - a.first
            val dz = b.second - a.second
            val l = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6f)
            return -dz / l to dx / l
        }
        for (k in 0 until path.size - 1) {
            val (nx0, nz0) = side(k)
            val (nx1, nz1) = side(k + 1)
            val c0 = path[k]
            val c1 = path[k + 1]
            val l0 = Vector3(c0.first + nx0 * (off - w / 2), y, c0.second + nz0 * (off - w / 2))
            val r0 = Vector3(c0.first + nx0 * (off + w / 2), y, c0.second + nz0 * (off + w / 2))
            val l1 = Vector3(c1.first + nx1 * (off - w / 2), y, c1.second + nz1 * (off - w / 2))
            val r1 = Vector3(c1.first + nx1 * (off + w / 2), y, c1.second + nz1 * (off + w / 2))
            face(l0, r0, r1, l1, UP)
            if (h > 0) {
                val down = Vector3(0f, -h, 0f)
                face(l0.plus(down), l0, l1, l1.plus(down), Vector3(-nx0, 0f, -nz0))
                face(r0, r0.plus(down), r1.plus(down), r1, Vector3(nx0, 0f, nz0))
            }
        }
    }

    /** A box lying at ([x], [z]) turned [yaw] radians, its top at [y]. */
    fun tie(x: Float, z: Float, yaw: Float, len: Float, wid: Float, y: Float, h: Float) {
        val cx = kotlin.math.cos(yaw)
        val sx = kotlin.math.sin(yaw)
        fun p(a: Float, b: Float, yy: Float) = Vector3(x + a * cx - b * sx, yy, z + a * sx + b * cx)
        val a = len / 2
        val b = wid / 2
        val lo = y - h
        val nA = Vector3(cx, 0f, sx)
        val nB = Vector3(-sx, 0f, cx)
        face(p(-a, b, y), p(a, b, y), p(a, -b, y), p(-a, -b, y), UP)
        face(p(a, b, lo), p(a, -b, lo), p(a, -b, y), p(a, b, y), nA)
        face(p(-a, -b, lo), p(-a, b, lo), p(-a, b, y), p(-a, -b, y), Vector3(-cx, 0f, -sx))
        face(p(-a, b, lo), p(a, b, lo), p(a, b, y), p(-a, b, y), nB)
        face(p(a, -b, lo), p(-a, -b, lo), p(-a, -b, y), p(a, -b, y), Vector3(sx, 0f, -cx))
    }

    /** One-sided by the renderer, and these are small: winding made to agree with the normal, whichever way the corners came. */
    private fun face(a: Vector3, b: Vector3, c: Vector3, d: Vector3, n: Vector3) {
        val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
        val wx = c.x - a.x; val wy = c.y - a.y; val wz = c.z - a.z
        val cx = uy * wz - uz * wy
        val cy = uz * wx - ux * wz
        val cz = ux * wy - uy * wx
        if (cx * n.x + cy * n.y + cz * n.z >= 0) quad(a, b, c, d, n) else quad(d, c, b, a, n)
    }

    fun build(name: String): MeshResource = MeshResource.createWithMeshModel(
        MeshModel(pos, idx, nor, List(pos.size) { Vector4(1f, 0f, 0f, 1f) }, uvs),
        BoundingBox(Vector3(x0, y0, z0), Vector3(x1, y1, z1)),
        name,
    )

    private fun Vector3.plus(o: Vector3) = Vector3(x + o.x, y + o.y, z + o.z)

    companion object {
        val UP = Vector3(0f, 1f, 0f)
        val ZERO = Vector2(0f, 0f)
    }
}
