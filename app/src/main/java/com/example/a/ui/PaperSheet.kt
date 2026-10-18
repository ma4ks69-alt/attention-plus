package com.example.a.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A calendar page simulated as an inextensible strip of paper seen from the side.
 *
 * Coordinates are pixels: `y` runs down the card and `z` points toward the viewer.
 * The calendar body is a slab from the card face (z = 0) back to z = -[depth],
 * starting at [topY]. The page hangs from [hingeY] (the binding's lower edge).
 * Verlet integration with gravity, bending stiffness and slab collisions lets it
 * curl while it is thrown up, drape over the binding and settle behind the body.
 */
internal class PaperSheet(
    private val hingeY: Float,
    private val topY: Float,
    private val depth: Float,
    val length: Float,
    private val gravity: Float,
    flipped: Boolean,
) {
    private val count = Segments + 1
    val segment = length / Segments
    val y = FloatArray(count)
    val z = FloatArray(count)
    private val oldY = FloatArray(count)
    private val oldZ = FloatArray(count)
    private val restY = FloatArray(count)
    private val restZ = FloatArray(count)

    /** Incremented after every simulated frame so drawing observes the motion. */
    var frame by mutableIntStateOf(0)
        private set

    /** The state the page last came to rest in, or is resting in. */
    var restingFlipped by mutableStateOf(flipped)
        private set
    var atRest by mutableStateOf(true)
        private set

    private var driveFrom = 0f
    private var driveTo = 0f
    private var radiusFrom = 0f
    private var radiusTo = 0f
    private var driveTime = 0f
    private var elapsed = 0f
    private var quietFrames = 0

    init {
        placeAtRest(flipped)
    }

    /** Exact resting shapes: flat on the face, or wrapped over the top and hanging behind. */
    private fun restShape(flipped: Boolean, outY: FloatArray, outZ: FloatArray) {
        val climb = hingeY - topY
        for (i in 0 until count) {
            val s = i * segment
            if (!flipped) {
                outY[i] = hingeY + s
                outZ[i] = 0f
            } else when {
                s <= climb -> { outY[i] = hingeY - s; outZ[i] = 0f }
                s <= climb + depth -> { outY[i] = topY; outZ[i] = -(s - climb) }
                else -> { outY[i] = topY + (s - climb - depth); outZ[i] = -depth }
            }
        }
    }

    private fun placeAtRest(flipped: Boolean) {
        restShape(flipped, y, z)
        y.copyInto(oldY)
        z.copyInto(oldZ)
    }

    /**
     * Starts a throw toward [flipped]. The free edge is guided along an arc about
     * the binding's top edge, from wherever it is now, so interruptions are smooth.
     */
    fun beginDrive(flipped: Boolean) {
        val end = count - 1
        // Angles run 0 (hanging in front) through 90 (toward the viewer) and 180
        // (straight up) to 360 (hanging behind). Anything behind the face plane
        // belongs to the far half, so returning travels back over the top
        // instead of being pulled through the calendar body.
        var from = (atan2(z[end], y[end] - topY) * 180f / PI.toFloat())
        if (from < 0f) from += 360f
        driveFrom = from
        driveTo = if (flipped) 345f else 0f
        radiusFrom = hypot(y[end] - topY, z[end])
        radiusTo = hingeY + length - topY
        driveTime = (0.78f * abs(driveTo - driveFrom) / 345f).coerceIn(0.32f, 0.78f)
        restShape(flipped, restY, restZ)
        elapsed = 0f
        quietFrames = 0
        atRest = false
    }

    /** Advances the simulation by [dt] seconds; returns true once settled. */
    fun advance(dt: Float, target: Boolean): Boolean {
        val steps = 4
        val h = dt / steps
        repeat(steps) {
            elapsed += h
            step(h)
        }
        frame++
        var motion = 0f
        for (i in 1 until count) motion = max(motion, abs(y[i] - oldY[i]) + abs(z[i] - oldZ[i]))
        quietFrames = if (elapsed > driveTime && motion < 0.04f) quietFrames + 1 else 0
        if (quietFrames >= 8 || elapsed > driveTime + 2f) {
            // Settle exactly into the resting shape the page has converged on.
            placeAtRest(target)
            frame++
            restingFlipped = target
            atRest = true
            return true
        }
        return false
    }

    private fun step(h: Float) {
        val end = count - 1
        // The calendar leans back slightly, so gravity also presses the page onto
        // the face when it hangs in front, like a real desk calendar.
        val gy = gravity * h * h
        val gz = -0.18f * gravity * h * h
        for (i in 1 until count) {
            val vy = (y[i] - oldY[i]) * Damping
            val vz = (z[i] - oldZ[i]) * Damping
            oldY[i] = y[i]
            oldZ[i] = z[i]
            y[i] += vy + gy
            z[i] += vz + gz
        }
        if (elapsed < driveTime) {
            // Smoothstep pacing: the hand lifts gently, throws, then lets go.
            val u = (elapsed / driveTime).coerceIn(0f, 1f)
            val e = u * u * (3f - 2f * u)
            val angle = (driveFrom + (driveTo - driveFrom) * e) * PI.toFloat() / 180f
            val radius = radiusFrom + (radiusTo - radiusFrom) * e
            val ty = topY + radius * cos(angle)
            val tz = radius * sin(angle)
            y[end] += (ty - y[end]) * 0.35f
            z[end] += (tz - z[end]) * 0.35f
        }
        if (elapsed >= driveTime) {
            // After the throw, the paper is drawn gently into its resting shape, so
            // it always lands, and lands softly instead of bouncing.
            val k = ((elapsed - driveTime) / 0.5f).coerceIn(0f, 1f) * 0.06f
            for (i in 1 until count) {
                y[i] += (restY[i] - y[i]) * k
                z[i] += (restZ[i] - z[i]) * k
            }
        }
        repeat(Iterations) {
            y[0] = hingeY
            z[0] = 0f
            for (i in 0 until end) {
                val dy = y[i + 1] - y[i]
                val dz = z[i + 1] - z[i]
                val d = sqrt(dy * dy + dz * dz).coerceAtLeast(0.0001f)
                val diff = (d - segment) / d
                if (i == 0) {
                    y[1] -= dy * diff
                    z[1] -= dz * diff
                } else {
                    y[i] += dy * diff * 0.5f
                    z[i] += dz * diff * 0.5f
                    y[i + 1] -= dy * diff * 0.5f
                    z[i + 1] -= dz * diff * 0.5f
                }
            }
            // Paper resists sharp bends: nudge each point toward its neighbours' midpoint.
            for (i in 1 until end) {
                val my = (y[i - 1] + y[i + 1]) * 0.5f - y[i]
                val mz = (z[i - 1] + z[i + 1]) * 0.5f - z[i]
                y[i] += my * Stiffness
                z[i] += mz * Stiffness
                if (i - 1 > 0) {
                    y[i - 1] -= my * Stiffness * 0.5f
                    z[i - 1] -= mz * Stiffness * 0.5f
                }
                y[i + 1] -= my * Stiffness * 0.5f
                z[i + 1] -= mz * Stiffness * 0.5f
            }
            for (i in 1 until count) collide(i)
        }
    }

    /** Keeps a point out of the calendar body, leaving by the side it came from. */
    private fun collide(i: Int) {
        if (y[i] <= topY || z[i] >= 0f || z[i] <= -depth) return
        when {
            oldZ[i] >= 0f -> z[i] = 0f
            oldZ[i] <= -depth -> z[i] = -depth
            else -> y[i] = topY
        }
    }

    companion object {
        const val Segments = 22
        private const val Iterations = 10
        private const val Stiffness = 0.12f
        private const val Damping = 0.975f
    }
}

/**
 * Draws [sheet] in perspective as textured strips. Strips facing the viewer show
 * [front]; reversed strips show [back]. [inFront] selects strips in front of the
 * calendar face, drawn over it, versus those behind, drawn before the body.
 */
internal fun DrawScope.drawPaperSheet(
    sheet: PaperSheet,
    front: GraphicsLayer,
    back: GraphicsLayer,
    inFront: Boolean,
    depth: Float,
) {
    sheet.frame // Observe simulation frames.
    val width = size.width
    val cx = width / 2f
    val cy = size.height / 2f
    val camera = size.height * 5f
    val matrix = android.graphics.Matrix()
    val src = FloatArray(8)
    val dst = FloatArray(8)
    for (i in 0 until PaperSheet.Segments) {
        val midZ = (sheet.z[i] + sheet.z[i + 1]) * 0.5f
        if ((midZ > -depth * 0.5f) != inFront) continue
        val s0 = i * sheet.segment
        val s1 = s0 + sheet.segment
        fun project(x: Float, y: Float, z: Float, into: Int) {
            val k = camera / (camera - z)
            dst[into] = cx + (x - cx) * k
            dst[into + 1] = cy + (y - cy) * k
        }
        project(0f, sheet.y[i], sheet.z[i], 0)
        project(width, sheet.y[i], sheet.z[i], 2)
        project(width, sheet.y[i + 1], sheet.z[i + 1], 4)
        project(0f, sheet.y[i + 1], sheet.z[i + 1], 6)
        val facing = dst[7] > dst[1]
        if (abs(dst[7] - dst[1]) < 0.25f) continue
        // Overlap strips by half a pixel so no seams show between them.
        src[0] = 0f; src[1] = s0 - 0.5f
        src[2] = width; src[3] = s0 - 0.5f
        src[4] = width; src[5] = s1 + 0.5f
        src[6] = 0f; src[7] = s1 + 0.5f
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) continue
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.concat(matrix)
            native.clipRect(0f, min(src[1], src[5]), width, max(src[1], src[5]))
            drawLayer(if (facing) front else back)
            native.restore()
        }
    }
}
