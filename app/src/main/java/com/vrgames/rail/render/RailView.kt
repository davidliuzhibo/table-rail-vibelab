package com.vrgames.rail.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import com.vrgames.rail.core.Board
import com.vrgames.rail.core.Cargo
import com.vrgames.rail.core.Cell
import com.vrgames.rail.core.Dir
import com.vrgames.rail.core.Ground
import com.vrgames.rail.core.Play
import com.vrgames.rail.core.Stop
import com.vrgames.rail.core.Train
import com.vrgames.rail.core.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

sealed interface Target {
    data class OnCell(val c: Cell) : Target
    data class OnTrain(val id: Int) : Target
}

/**
 * The model railway: a board on a workbench, tilted a little toward the player. Everything on it is
 * in [board]'s frame (x right, z toward the player, y out of the board). [build] makes one level's
 * land; the track is one mesh, rebuilt when it changes; [sync] follows the play every frame.
 */
class RailView(private val models: Map<String, Entity>, private val tex: Map<String, TextureResource>) {
    val root = Entity()
    val board = Entity()
    private var land = Entity().also { board.addChild(it) }
    private var trackRoot = Entity().also { board.addChild(it) }
    private var decor = Entity().also { board.addChild(it) }
    private var trainRoot = Entity().also { board.addChild(it) }
    private val iblSource = Entity().also { root.addChild(it) }

    private val w = (Play.W * Board.CELL).toFloat()
    private val d = (Play.H * Board.CELL).toFloat()
    private val cell = Board.CELL.toFloat()

    private val steel = pbr(color(0.55f, 0.56f, 0.6f), metallic = 0.9f, roughness = 0.3f, emissive = color(0.06f, 0.06f, 0.07f)).shared()
    private val sleepers = pbr(color(0.3f, 0.2f, 0.13f), roughness = 0.85f).shared()
    private val ballast = pbr(color(0.36f, 0.34f, 0.32f), roughness = 1f).shared()
    private val wood = pbr(color(0.34f, 0.2f, 0.11f), roughness = 0.55f).shared()
    private val darkWood = pbr(color(0.16f, 0.09f, 0.05f), roughness = 0.5f).shared()
    private val bank = pbr(color(0.3f, 0.23f, 0.15f), roughness = 1f).shared()
    private val platform = pbr(color(0.62f, 0.6f, 0.55f), roughness = 0.9f).shared()
    private val bufferRed = pbr(color(0.8f, 0.14f, 0.1f), roughness = 0.5f).shared()
    private val grass = texMat("grass", 0.3f, 0.95f) ?: pbr(color(0.3f, 0.5f, 0.22f), roughness = 0.95f).shared()
    private val hillGrass = texMat("grass", 0.2f, 1f) ?: pbr(color(0.26f, 0.42f, 0.2f), roughness = 1f).shared()
    private val water = texMat("water", 0.28f, 0.5f) ?: pbr(color(0.15f, 0.4f, 0.55f), roughness = 0.15f).shared()
    private val sphere = MeshResource.createSphere(1f).shared()
    private val cylinder = MeshResource.createCylinder(1f, 1f).shared()
    private val hoverMat = unlit(color(1f, 1f, 1f, 0.018f), BlendingMode.TRANSPARENT).shared()
    private val cellMesh = flat(cell * 0.97f, cell * 0.97f, "cell").shared()
    private val anchorMat = glow(color(1f, 0.82f, 0.3f, 0.85f)).shared()
    private val eraseMat = glow(color(1f, 0.25f, 0.2f, 0.5f)).shared()
    private val beads = mapOf(
        Cargo.COAL to glow(color(0.75f, 0.75f, 0.8f, 0.9f)).shared(), Cargo.GOODS to glow(color(1f, 0.7f, 0.25f, 0.95f)).shared(), Cargo.OIL to glow(color(0.45f, 0.65f, 1f, 0.95f)).shared(),
        Cargo.PEOPLE to glow(color(1f, 0.45f, 0.6f, 0.95f)).shared(), Cargo.WOOD to glow(color(0.5f, 1f, 0.45f, 0.95f)).shared(),
    )
    private val steam = Sparks("rail_steam", 90, sphere, glow(color(0.75f, 0.75f, 0.75f, 0.5f)).shared()).also { board.addChild(it.entity) }
    private val dust = Sparks("rail_dust", 120, sphere, glow(color(1f, 0.75f, 0.35f, 1f)).shared()).also { board.addChild(it.entity) }
    private val fireworks = Sparks("rail_show", 260, sphere, glow(color(0.55f, 0.8f, 1f, 1f)).shared()).also { root.addChild(it.entity) }

    private val cellOf = HashMap<Entity, Cell>()
    private val cellViews = HashMap<Cell, Entity>()
    private val trainOf = HashMap<Entity, Int>()
    private val trainViews = ArrayList<TrainView>()
    private val anchorMark = model(cylinder, anchorMat).apply { transform().setScaleVector(Vector3(cell * 0.42f, 0.002f, cell * 0.42f)); enabled = false }.also { board.addChild(it) }
    private var trackVersion = -1
    private var grownShown = -1
    private var played: Play? = null
    private var clock = 0.0
    private var showClock = 0.0
    private val trophyViews = ArrayList<Entity>()
    private val trophyMats = listOf(
        pbr(color(0.72f, 0.45f, 0.22f), metallic = 1f, roughness = 0.3f), pbr(color(0.8f, 0.82f, 0.86f), metallic = 1f, roughness = 0.22f), pbr(color(1f, 0.78f, 0.3f), metallic = 1f, roughness = 0.2f),
    ).map { it.shared() }
    private val trophyOff = pbr(color(0.08f, 0.08f, 0.1f), roughness = 0.8f).shared()

    private class TrainView(val cars: List<Entity>, val bead: Entity)
    private class StopLabel(val stop: Stop, val at: Vector3) {
        var text: String? = null
        var entity: Entity? = null
    }
    private val labels = ArrayList<StopLabel>()
    private val pickRing = model(MeshResource.createTorus(cell * 0.62f, 0.003f), glow(color(0.3f, 1f, 0.55f, 1f)).shared()).apply { enabled = false }.also { board.addChild(it) }

    init {
        root.addChild(board)
        board.setPos(Vector3(0f, Board.Y.toFloat(), 0f))
        board.transform().eulerAngles = EulerAngles(Board.TILT.toFloat(), 0f, 0f)
        bench()
        cells()
    }

    private fun texMat(name: String, glowShare: Float, roughness: Float): Material? = tex[name]?.let { t ->
        PhysicallyBasedMaterial.create().apply {
            setBaseColorTexture(t)
            setEmissiveTexture(t)
            setEmissiveColor(color(glowShare, glowShare, glowShare))
            setRoughness(roughness)
        }.shared()
    }

    private fun flat(fw: Float, fd: Float, name: String): MeshResource {
        val x = fw / 2
        val z = fd / 2
        return MeshResource.createWithMeshModel(
            MeshModel(
                listOf(Vector3(-x, 0f, z), Vector3(x, 0f, z), Vector3(x, 0f, -z), Vector3(-x, 0f, -z)), listOf(0, 1, 2, 0, 2, 3),
                List(4) { Vector3(0f, 1f, 0f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 1f), Vector2(1f, 1f), Vector2(1f, 0f), Vector2(0f, 0f)),
            ),
            BoundingBox(Vector3(-x, -0.001f, -z), Vector3(x, 0.001f, z)), name,
        )
    }

    private fun clone(name: String): Entity? = models[name]?.clone(Entity.CloneOptions(true, true))

    private fun setMaterials(e: Entity, m: Material) {
        e.components[ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
        e.getChildren().forEach { setMaterials(it, m) }
    }

    /** What stays from level to level: the frame of the board, the bench under it, the trophy shelf. */
    private fun bench() {
        val fw = w + 0.07f
        val fd = d + 0.07f
        val a = Math.toRadians(Board.TILT)
        board.addChild(model(MeshResource.createBox(Vector3(fw, 0.05f, fd), 0.006f), wood).apply { setPos(Vector3(0f, -0.046f, 0f)) })
        for (s in listOf(-1f, 1f)) {
            board.addChild(model(MeshResource.createBox(Vector3(0.03f, 0.03f, fd), 0.004f), darkWood).apply { setPos(Vector3(s * (fw / 2 - 0.015f), 0f, 0f)) })
            board.addChild(model(MeshResource.createBox(Vector3(fw, 0.03f, 0.03f), 0.004f), darkWood).apply { setPos(Vector3(0f, 0f, s * (fd / 2 - 0.015f))) })
        }
        // Legs to the floor.
        val y = Board.Y.toFloat()
        val nearY = y - (fd / 2 * sin(a)).toFloat() - 0.05f
        val farY = y + (fd / 2 * sin(a)).toFloat() - 0.05f
        val nz = (fd / 2 * cos(a)).toFloat()
        for (s in listOf(-1f, 1f)) {
            root.addChild(model(MeshResource.createBox(Vector3(0.05f, nearY, 0.05f), 0.006f), darkWood).apply { setPos(Vector3(s * (fw / 2 - 0.05f), nearY / 2, nz - 0.05f)) })
            root.addChild(model(MeshResource.createBox(Vector3(0.05f, farY, 0.05f), 0.006f), darkWood).apply { setPos(Vector3(s * (fw / 2 - 0.05f), farY / 2, -nz + 0.05f)) })
        }
        // The shelf on the right, with the trophies.
        val sx = fw / 2 + 0.2f
        root.addChild(model(MeshResource.createBox(Vector3(0.34f, 0.02f, 0.14f), 0.004f), darkWood).apply { setPos(Vector3(sx, 0.84f, -0.3f)) })
        root.addChild(model(MeshResource.createBox(Vector3(0.03f, 0.84f, 0.03f), 0.004f), darkWood).apply { setPos(Vector3(sx, 0.42f, -0.3f)) })
        for (k in 0 until 3) {
            val t = clone("trophy") ?: model(MeshResource.createCone(0.1f, 0.03f), steel)
            t.setScale(0.1f)
            t.setPos(Vector3(sx - 0.11f + k * 0.11f, 0.85f, -0.3f))
            t.transform().eulerAngles = EulerAngles(0f, -90f, 0f)
            root.addChild(t)
            trophyViews += t
        }
        trophies(0)
    }

    fun trophies(earned: Int) = trophyViews.forEachIndexed { k, t -> setMaterials(t, if (k < earned) trophyMats[k] else trophyOff) }

    private fun cx(x: Double) = Board.localX(x).toFloat()
    private fun cz(y: Double) = Board.localZ(y).toFloat()
    private fun center(c: Cell) = Vector3(cx(c.x + 0.5), 0f, cz(c.y + 0.5))

    /** A pinch box and a hover glow for every cell. */
    private fun cells() {
        for (y in 0 until Play.H) for (x in 0 until Play.W) {
            val c = Cell(x, y)
            val e = model(cellMesh, hoverMat)
            e.setPos(Vector3(cx(x + 0.5), 0.009f, cz(y + 0.5)))
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(cell, 0.012f, cell))), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            e.components.set(HoverEffectComponent())
            board.addChild(e)
            cellOf[e] = c
            cellViews[c] = e
        }
    }

    /** What a pinch on [e] means. */
    fun target(e: Entity?): Target? {
        var cur = e
        while (cur != null) {
            cellOf[cur]?.let { return Target.OnCell(it) }
            trainOf[cur]?.let { return Target.OnTrain(it) }
            cur = cur.getParent()
        }
        return null
    }

    /** Makes the land of [p]'s level. */
    fun build(p: Play) {
        played = p
        land.destroy()
        trackRoot.destroy()
        decor.destroy()
        trainRoot.destroy()
        land = Entity().also { board.addChild(it) }
        trackRoot = Entity().also { board.addChild(it) }
        decor = Entity().also { board.addChild(it) }
        trainRoot = Entity().also { board.addChild(it) }
        trainOf.clear()
        trainViews.clear()
        carUnit.clear()
        trainsHit = false
        labels.clear()
        rubble.clear()
        steam.clear()
        trackVersion = -1
        grownShown = -1
        // Cells under a building or on a hill reach up to the model.
        for ((c, e) in cellViews) {
            val tall = p.isBuilding(c)
            val hill = p.ground(c) == Ground.HILL
            val h = if (tall) 0.06f else if (hill) 0.055f else 0.012f
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(cell, h, cell))), PhysicsMaterialResource()))
            e.setPos(Vector3(e.transform().position.x, h / 2 + 0.003f, e.transform().position.z))
        }
        ground(p)
        hills(p)
        for ((c, g) in p.stage.ground) if (g == Ground.ROCK) rock(c)
        for (s in p.stops) stop(p, s)
        p.trains.forEach { train(it) }
    }

    private fun ground(p: Play) {
        val uv = 1f / w
        val g = MeshBuilder()
        val riverBed = MeshBuilder()
        val banks = MeshBuilder()
        val drop = 0.016f
        for (y in 0 until Play.H) for (x in 0 until Play.W) {
            val c = Cell(x, y)
            val xa = cx(x.toDouble())
            val xb = cx(x + 1.0)
            val za = cz(y.toDouble())
            val zb = cz(y + 1.0)
            if (p.ground(c) != Ground.RIVER) {
                g.ground(xa, xb, za, zb, 0f, uv)
                continue
            }
            riverBed.ground(xa, xb, za, zb, -drop, uv)
            fun open(o: Cell) = p.inside(o) && p.ground(o) != Ground.RIVER
            if (open(c + Dir.N)) banks.tie((xa + xb) / 2, za, 0f, cell, 0.002f, 0f, drop)
            if (open(c + Dir.S)) banks.tie((xa + xb) / 2, zb, 0f, cell, 0.002f, 0f, drop)
            if (open(c + Dir.W)) banks.tie(xa, (za + zb) / 2, 0f, 0.002f, cell, 0f, drop)
            if (open(c + Dir.E)) banks.tie(xb, (za + zb) / 2, 0f, 0.002f, cell, 0f, drop)
        }
        land.addChild(model(g.build("ground"), grass))
        if (!riverBed.empty) land.addChild(model(riverBed.build("river"), water))
        if (!banks.empty) land.addChild(model(banks.build("banks"), bank))
    }

    /** One smooth mound over each run of hill cells, high in the middle, down to the grass at its edge. */
    private fun hills(p: Play) {
        val hill = p.stage.ground.filterValues { it == Ground.HILL }.keys
        if (hill.isEmpty()) return
        val n = 7
        fun inHill(px: Double, py: Double) = Cell(kotlin.math.floor(px).toInt(), kotlin.math.floor(py).toInt()) in hill
        /** Height at a point, by how far it is from the nearest cell that isn't hill. */
        fun height(px: Double, py: Double): Float {
            if (!inHill(px, py)) return 0f
            var best = 1.0
            val cxx = kotlin.math.floor(px).toInt()
            val cyy = kotlin.math.floor(py).toInt()
            for (yy in cyy - 2..cyy + 2) for (xx in cxx - 2..cxx + 2) {
                if (Cell(xx, yy) in hill) continue
                val dx = maxOf(xx - px, 0.0, px - (xx + 1))
                val dy = maxOf(yy - py, 0.0, py - (yy + 1))
                best = minOf(best, hypot(dx, dy))
            }
            val u = (best / 0.62).coerceIn(0.0, 1.0)
            val s = u * u * (3 - 2 * u)
            val noise = 0.06 * sin(px * 9.1 + py * 3.7) * sin(py * 7.3 - px * 2.1)
            return ((0.062 * s) * (1 + noise)).toFloat()
        }
        val b = MeshBuilder()
        val uv = 1f / w
        fun v(px: Double, py: Double) = Vector3(cx(px), height(px, py) + 0.0006f, cz(py))
        fun nor(px: Double, py: Double): Vector3 {
            val e = 0.04
            val dx = (height(px + e, py) - height(px - e, py)) / (2 * e * Board.CELL)
            val dz = (height(px, py + e) - height(px, py - e)) / (2 * e * Board.CELL)
            val l = kotlin.math.sqrt(dx * dx + dz * dz + 1)
            return Vector3((-dx / l).toFloat(), (1 / l).toFloat(), (-dz / l).toFloat())
        }
        fun t(q: Vector3) = Vector2(q.x * uv + 0.5f, q.z * uv + 0.5f)
        val inset = 1e-3
        for (c in hill) for (j in 0 until n) for (i in 0 until n) {
            val x0 = c.x + i.toDouble() / n + inset
            val x1 = c.x + (i + 1.0) / n - inset
            val y0 = c.y + j.toDouble() / n + inset
            val y1 = c.y + (j + 1.0) / n - inset
            val a = v(x0, y1); val bb = v(x1, y1); val cc = v(x1, y0); val dd = v(x0, y0)
            b.smooth(a, bb, cc, dd, nor(x0, y1), nor(x1, y1), nor(x1, y0), nor(x0, y0), t(a), t(bb), t(cc), t(dd))
        }
        land.addChild(model(b.build("hills"), hillGrass))
    }

    private fun rock(c: Cell) {
        val e = clone("rock") ?: return
        e.setScale(Looks.ROCK)
        e.setPos(center(c))
        e.transform().eulerAngles = EulerAngles(0f, (c.x * 73 + c.y * 41).toFloat(), 0f)
        land.addChild(e)
    }

    /** The station: its building, facing the door cell's track, a platform and its name. */
    private fun stop(p: Play, s: Stop) {
        val at = center(s.building)
        val face = s.side.back
        val yaw = when (face) { Dir.E -> 0f; Dir.S -> -90f; Dir.W -> 180f; Dir.N -> 90f }
        clone(s.model)?.let { e ->
            e.setScale(Looks.scale(s.model))
            e.setPos(at)
            e.transform().eulerAngles = EulerAngles(0f, yaw + Looks.doorTurn(s.model), 0f)
            land.addChild(e)
        }
        val door = center(s.at)
        land.addChild(model(MeshResource.createBox(Vector3(if (s.side == Dir.N || s.side == Dir.S) cell * 0.96f else cell * 0.26f, 0.008f, if (s.side == Dir.N || s.side == Dir.S) cell * 0.26f else cell * 0.96f), 0.001f), platform).apply {
            setPos(Vector3(door.x + s.side.dx * cell * 0.36f, 0.004f, door.z + s.side.dy * cell * 0.36f))
        })
        labels += StopLabel(s, Vector3(at.x, Looks.height(s.model) + 0.024f, at.z))
    }

    private val rubble = HashMap<Cell, Entity>()

    /** A landslide shows as a heap of rock with a red glow on the buried track. */
    private fun syncRubble(p: Play) {
        for (c in p.damaged.keys) if (c !in rubble) {
            val holder = Entity()
            holder.setPos(center(c))
            clone("rock")?.let { r ->
                r.setScale(Looks.ROCK * 0.7f)
                r.transform().eulerAngles = EulerAngles(0f, (c.x * 37).toFloat(), 0f)
                holder.addChild(r)
            }
            holder.addChild(model(cylinder, eraseMat).apply {
                setPos(Vector3(0f, 0.006f, 0f))
                transform().setScaleVector(Vector3(cell * 0.46f, 0.002f, cell * 0.46f))
            })
            land.addChild(holder)
            rubble[c] = holder
            receive(holder)
        }
        for (c in rubble.keys.toList()) if (c !in p.damaged) {
            rubble[c]?.destroy()
            rubble.remove(c)
        }
    }

    /** A stop's tag says what is waiting there, what is wanted there, in the color of the cargo. */
    private fun syncLabels(p: Play) {
        for (l in labels) {
            val w = p.waitingAt(l.stop)
            val want = p.wantedAt(l.stop).filter { it.def.from != l.stop.id }
            val ready = p.trains.firstOrNull { it.ready && it.cell == l.stop.at }
            val text = l.stop.title + when {
                ready != null -> "：装好了，捏车发车"
                w.isNotEmpty() -> "：${w.size} 单${w[0].def.cargo.title}待运"
                want.isNotEmpty() -> "：要${want[0].def.cargo.title}"
                else -> ""
            }
            if (text != l.text) {
                l.text = text
                l.entity?.destroy()
                val e = tag(text, w.firstOrNull()?.def?.cargo ?: want.firstOrNull()?.def?.cargo)
                e.setPos(l.at)
                e.transform().eulerAngles = EulerAngles(-28f, 0f, 0f)
                land.addChild(e)
                l.entity = e
            }
        }
    }

    private fun tag(text: String, cargo: Cargo?): Entity {
        val px = 112
        val ink = Paint().apply { isAntiAlias = true; color = 0xFF2A1A10.toInt(); textSize = px * 0.7f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val b = Bitmap.createBitmap((ink.measureText(text) + px * 0.8f).toInt(), px, Bitmap.Config.ARGB_8888)
        val fill = when (cargo) {
            Cargo.COAL -> 0xFFD8D8DE; Cargo.GOODS -> 0xFFFFD9A0; Cargo.OIL -> 0xFFB8D0FF; Cargo.PEOPLE -> 0xFFFFC4D4; Cargo.WOOD -> 0xFFC8F0B8; null -> 0xFFFDF6E3
        }.toInt()
        Canvas(b).apply {
            drawRoundRect(0f, 0f, b.width.toFloat(), px.toFloat(), px / 2f, px / 2f, Paint().apply { isAntiAlias = true; color = fill })
            drawText(text, b.width / 2f, px * 0.73f, ink)
        }
        val m = UnlitMaterial.create(BlendingMode.MASKED).apply { setBaseColorTexture(TextureResource.create(b)); setOpacityThreshold(0.3f) }
        val h = 0.026f
        val tw = h * b.width / px
        val x = tw / 2
        val y = h / 2
        val mesh = MeshResource.createWithMeshModel(
            MeshModel(
                listOf(Vector3(-x, -y, 0f), Vector3(x, -y, 0f), Vector3(x, y, 0f), Vector3(-x, y, 0f)), listOf(0, 1, 2, 0, 2, 3),
                List(4) { Vector3(0f, 0f, 1f) }, List(4) { Vector4(1f, 0f, 0f, 1f) }, listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)),
            ),
            BoundingBox(Vector3(-x, -y, -0.001f), Vector3(x, y, 0.001f)), "tag$text",
        )
        return model(mesh, m)
    }

    private fun carModel(cargo: Cargo) = when (cargo) {
        Cargo.COAL -> "tender"
        Cargo.GOODS, Cargo.WOOD -> "freight"
        Cargo.OIL -> "tanker"
        Cargo.PEOPLE -> "coach"
    }

    private val carUnit = HashMap<Entity, Float>()
    private var trainsHit = false

    /** A train's pinch box, smaller than a cell so the track and stations around it can still be aimed at. */
    private fun carBox(e: Entity, on: Boolean) {
        if (on) {
            val u = carUnit[e] ?: return
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(u * 0.75f, u * 0.5f, u * 0.75f))), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            e.components.set(HoverEffectComponent())
        } else {
            e.components.remove(HoverEffectComponent::class.java)
            e.components.remove(InteractableComponent::class.java)
            e.components.remove(CollisionComponent::class.java)
        }
    }

    private fun train(t: Train) {
        val cars = ArrayList<Entity>()
        val names = listOf("loco") + List(t.def.cars) { carModel(t.def.cargo) }
        for (n in names) {
            val e = clone(n) ?: model(MeshResource.createBox(Vector3(0.05f, 0.02f, 0.02f), 0.002f), steel)
            e.setScale(Looks.scale(n))
            e.enabled = false
            carUnit[e] = cell / Looks.scale(n)
            if (trainsHit) carBox(e, true)
            trainOf[e] = t.id
            trainRoot.addChild(e)
            cars += e
        }
        val bead = model(sphere, beads[t.def.cargo]!!).apply { setScale(0.008f); enabled = false }
        trainRoot.addChild(bead)
        trainViews += TrainView(cars, bead)
    }

    private fun piecePath(from: Dir?, to: Dir?, n: Int): List<Pair<Float, Float>> {
        val half = cell / 2
        // A stub: from the edge to the middle.
        if (from == null || to == null) {
            val e = from ?: to!!
            return List(n + 1) { k -> val u = 1f - k.toFloat() / n; e.dx * half * u to e.dy * half * u }
        }
        if (from == to.back) return List(n + 1) { k ->
            val u = k.toFloat() / n
            from.dx * half * (1 - 2 * u) to from.dy * half * (1 - 2 * u)
        }
        val ox = (from.dx + to.dx) * half
        val oz = (from.dy + to.dy) * half
        val a0 = atan2(from.dy * half - oz, from.dx * half - ox)
        var da = atan2(to.dy * half - oz, to.dx * half - ox) - a0
        while (da > PI) da -= (2 * PI).toFloat()
        while (da < -PI) da += (2 * PI).toFloat()
        return List(n + 1) { k ->
            val a = a0 + da * k / n
            ox + half * cos(a) to oz + half * sin(a)
        }
    }

    private fun addPiece(c: Cell, from: Dir?, to: Dir?, rails: MeshBuilder, ties: MeshBuilder, gravel: MeshBuilder) {
        val at = center(c)
        val n = if (from != null && to != null && from != to.back) 8 else 6
        val path = piecePath(from, to, n).map { (x, z) -> x + at.x to z + at.z }
        gravel.strip(path, 0f, GAUGE * 2.0f, 0.0028f, 0f)
        for (off in listOf(-GAUGE / 2, GAUGE / 2)) rails.strip(path, off, 0.0014f, 0.0062f, 0.0022f)
        // Sleepers across the path, at even steps along it.
        var acc = 0f
        var next = TIE_STEP / 2
        for (k in 0 until path.size - 1) {
            val (ax, az) = path[k]
            val (bx, bz) = path[k + 1]
            val l = hypot(bx - ax, bz - az)
            while (acc + l >= next) {
                val u = (next - acc) / l
                val yaw = atan2(bz - az, bx - ax) + (PI / 2).toFloat()
                ties.tie(ax + (bx - ax) * u, az + (bz - az) * u, yaw, GAUGE * 1.7f, 0.0042f, 0.0042f, 0.0016f)
                next += TIE_STEP
            }
            acc += l
        }
    }

    /** All the track as three meshes, bridges and tunnel mouths with it, and the switch arrows. */
    private fun buildTrack(p: Play) {
        trackRoot.destroy()
        trackRoot = Entity().also { board.addChild(it) }
        val rails = MeshBuilder()
        val ties = MeshBuilder()
        val gravel = MeshBuilder()
        for (c in p.links.keys) {
            val ws = p.ways(c)
            when (ws.size) {
                // A stop with no line to it yet: just its platform.
                0 -> {}
                1 -> addPiece(c, ws[0], null, rails, ties, gravel)
                2 -> addPiece(c, ws[0], ws[1], rails, ties, gravel)
                else -> for (i in ws.indices) for (j in i + 1 until ws.size) addPiece(c, ws[i], ws[j], rails, ties, gravel)
            }
            if (ws.size == 1) trackRoot.addChild(model(MeshResource.createBox(Vector3(0.012f, 0.012f, 0.012f), 0.002f), bufferRed).apply {
                setPos(Vector3(center(c).x - ws[0].dx * 0.012f, 0.009f, center(c).z - ws[0].dy * 0.012f))
            })
            if (p.ground(c) == Ground.RIVER && ws.isNotEmpty()) bridge(c, ws)
            if (p.ground(c) != Ground.HILL) for (dd in ws) if (p.ground(c + dd) == Ground.HILL) mouth(c, dd)
        }
        if (!gravel.empty) trackRoot.addChild(model(gravel.build("gravel"), ballast))
        if (!ties.empty) trackRoot.addChild(model(ties.build("ties"), sleepers))
        if (!rails.empty) trackRoot.addChild(model(rails.build("rails"), steel))
    }

    private fun bridge(c: Cell, ws: List<Dir>) {
        val along = if (ws.any { it == Dir.N || it == Dir.S }) 90f else 0f
        val e = clone("bridge") ?: return
        e.transform().setScaleVector(Vector3(Looks.BRIDGE_X, Looks.BRIDGE_Y, Looks.BRIDGE_Z))
        e.setPos(Vector3(center(c).x, -0.016f, center(c).z))
        e.transform().eulerAngles = EulerAngles(0f, along + Looks.BRIDGE_TURN, 0f)
        trackRoot.addChild(e)
    }

    /** A stone mouth where a line goes into a hill: at the hill's edge, facing out over the line. */
    private fun mouth(c: Cell, into: Dir) {
        val e = clone("tunnel") ?: return
        e.setScale(Looks.TUNNEL)
        e.setPos(Vector3(center(c).x + into.dx * cell * 0.5f, 0f, center(c).z + into.dy * cell * 0.5f))
        val out = into.back
        val yaw = when (out) { Dir.E -> 0f; Dir.S -> -90f; Dir.W -> 180f; Dir.N -> 90f }
        e.transform().eulerAngles = EulerAngles(0f, yaw + Looks.TUNNEL_TURN, 0f)
        trackRoot.addChild(e)
    }

    /** Trees on free grass, and the town: more houses the more has been delivered. Free cells only. */
    private fun buildDecor(p: Play) {
        decor.destroy()
        decor = Entity().also { board.addChild(it) }
        val free = ArrayList<Cell>()
        for (y in 0 until Play.H) for (x in 0 until Play.W) {
            val c = Cell(x, y)
            if (!p.hasTrack(c) && !p.isBuilding(c) && p.ground(c) == Ground.GRASS) free += c
        }
        val near = free.sortedBy { c -> p.stops.minOf { s -> hypot((c.x - s.building.x).toDouble(), (c.y - s.building.y).toDouble()) } }
        val houses = near.take(p.grown).toSet()
        for (c in houses) clone(if ((c.x + c.y) % 2 == 0) "house1" else "house2")?.let { e ->
            val m = if ((c.x + c.y) % 2 == 0) "house1" else "house2"
            e.setScale(Looks.scale(m) * 0.72f)
            e.setPos(center(c))
            e.transform().eulerAngles = EulerAngles(0f, -90f + Looks.doorTurn(m), 0f)
            decor.addChild(e)
        }
        for (c in free) {
            if (c in houses) continue
            val h = (c.x * 7 + c.y * 13 + p.stage.id.hashCode()) and 15
            if (h > 2) continue
            clone("tree")?.let { e ->
                e.setScale(Looks.scale("tree") * (0.8f + h * 0.14f))
                e.setPos(Vector3(center(c).x + (h - 1) * 0.006f, 0f, center(c).z - (h - 1) * 0.008f))
                e.transform().eulerAngles = EulerAngles(0f, (c.x * 53 + c.y * 97).toFloat(), 0f)
                decor.addChild(e)
            }
        }
        receive(decor)
    }

    fun sync(p: Play, dt: Double) {
        if (p !== played) return
        clock += dt
        if (p.trackVersion != trackVersion) {
            trackVersion = p.trackVersion
            buildTrack(p)
            receive(trackRoot)
            buildDecor(p)
            grownShown = p.grown
        } else if (p.grown != grownShown) {
            grownShown = p.grown
            buildDecor(p)
        }
        val a = p.anchor
        if (a != null) {
            val pulse = 1f + 0.12f * sin(clock * 5).toFloat()
            anchorMark.setPos(Vector3(center(a).x, 0.012f, center(a).z))
            anchorMark.transform().setScaleVector(Vector3(cell * 0.42f * pulse, 0.002f, cell * 0.42f * pulse))
        }
        if (anchorMark.enabled != (a != null)) anchorMark.enabled = a != null
        syncLabels(p)
        syncRubble(p)
        // A train bought just now gets its models.
        while (trainViews.size < p.trains.size) {
            train(p.trains[trainViews.size])
            receive(trainRoot)
        }
        p.trains.forEachIndexed { k, t -> trainViews.getOrNull(k)?.let { placeTrain(p, t, it, dt) } }
        if (p.trainsPickable != trainsHit) {
            trainsHit = p.trainsPickable
            for (v in trainViews) for (e in v.cars) carBox(e, trainsHit)
        }
        // The ring marks the picked train, or the one waiting to be set off.
        val sel = p.selected ?: p.trains.firstOrNull { it.ready }
        if (pickRing.enabled != (sel != null)) pickRing.enabled = sel != null
        if (sel != null) {
            val h = p.head(sel)
            pickRing.setPos(Vector3(cx(h.x), 0.012f, cz(h.y)))
        }
        steam.update(dt, 0.4)
        dust.update(dt)
        fireworks.update(dt)
    }

    private fun placeTrain(p: Play, t: Train, v: TrainView, dt: Double) {
        val n = v.cars.size
        for (k in 0 until n) {
            // The locomotive at the front, or last when it is pushing.
            val slot = if (t.pushing) n - 1 - k else k
            val at = p.along(t, slot * Play.PITCH)
            val e = v.cars[k]
            if (at == null) {
                if (e.enabled) e.enabled = false
                continue
            }
            if (!e.enabled) e.enabled = true
            val (pt, dir) = at
            e.setPos(Vector3(cx(pt.x), RAIL_TOP, cz(pt.y)))
            // A model runs along +x with yaw 0 (see Looks); board y is z here, so yaw is minus the angle. Pushing, it faces back along the run.
            val run = -Math.toDegrees(dir).toFloat() + (if (t.pushing) 180f else 0f)
            e.transform().eulerAngles = EulerAngles(0f, run + Looks.turn(if (k == 0) "loco" else "car"), 0f)
        }
        val head = p.head(t)
        val locoSlot = if (t.pushing) n - 1 else 0
        val lp = p.along(t, locoSlot * Play.PITCH)?.first ?: head
        val moving = p.running && t.dest != null && t.wait <= 0 && !t.held && t.crashed <= 0
        if (moving && (clock * 6).toInt() != ((clock - dt) * 6).toInt()) {
            steam.emit(Vec3(cx(lp.x).toDouble(), 0.042, cz(lp.y).toDouble()), 1, 0.012, 1.1, 0.0045f, Vec3(0.0, 0.03, 0.0))
        }
        if (v.bead.enabled != t.loaded) v.bead.enabled = t.loaded
        if (t.loaded) v.bead.setPos(Vector3(cx(lp.x), 0.052f + 0.003f * sin(clock * 4).toFloat(), cz(lp.y)))
    }

    /** A little burst where something happened, in cells. */
    fun burst(x: Double, y: Double, count: Int = 24) {
        dust.emit(Vec3(cx(x).toDouble(), 0.03, cz(y).toDouble()), count, 0.09, 0.8, 0.0035f, Vec3(0.0, 0.04, 0.0))
    }

    fun boardPoint(x: Double, y: Double) = Vector3(cx(x), 0.03f, cz(y))

    /** The gold trophy's show: fireworks over the board while the trains run. */
    fun show(dt: Double) {
        val before = showClock
        showClock += dt
        if ((showClock * 1.4).toInt() != (before * 1.4).toInt()) {
            val k = (showClock * 1.4).toInt()
            val x = ((k * 37) % 100) / 100.0 * 0.9 - 0.45
            val z = -Board.AHEAD - ((k * 53) % 100) / 100.0 * 0.5
            fireworks.emit(Vec3(x, 1.15 + (k % 5) * 0.09, z), 60, 0.34, 1.4, 0.006f)
        }
    }

    fun useIbl(t: TextureResource) {
        iblSource.components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(t), 7f))
        receive(root)
    }

    private fun receive(e: Entity) {
        if (iblSource.components[ImageBasedLightComponent::class.java] == null) return
        if (e.components[ModelComponent::class.java] != null && e.components[ImageBasedLightReceiverComponent::class.java] == null) {
            e.components.set(ImageBasedLightReceiverComponent(iblSource))
            e.components.set(EnvironmentLightingSettingsComponent(0f))
        }
        e.getChildren().forEach { receive(it) }
    }

    /** After [build]: everything just made takes the room's light. */
    fun receiveAll() = receive(root)

    companion object {
        const val GAUGE = 0.0125f
        const val TIE_STEP = 0.0105f
        const val RAIL_TOP = 0.0065f
    }
}
