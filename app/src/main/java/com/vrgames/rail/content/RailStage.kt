package com.vrgames.rail.content

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.TextureCreateOption
import com.pico.spatial.core.ecs.resource.TextureEncoding
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.toColor4
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.vrgames.rail.GUIDE_ID
import com.vrgames.rail.LEVELS_ID
import com.vrgames.rail.game.AutoPilot
import com.vrgames.rail.game.Game
import com.vrgames.rail.render.Music
import com.vrgames.rail.render.Sound
import com.vrgames.rail.render.RailView
import com.vrgames.rail.render.color
import com.vrgames.rail.render.model
import com.vrgames.rail.render.setPos
import com.vrgames.rail.render.setScale
import com.vrgames.rail.render.shared
import com.vrgames.rail.render.transform
import com.vrgames.rail.render.unlit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TAG = "RailStage"

/** The model railway in front of the seated player, in its workshop. A pinch on the board lays track, a pinch on a train or a switch works it. */
@Composable
fun RailStage() {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val navigator = LocalSpatialNavigator.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val head = remember { HMDTrackingProvider() }
    val subscription = remember { arrayOfNulls<Cancellable>(1) }
    var view by remember { mutableStateOf<RailView?>(null) }
    val seat = remember { Seat() }
    val panels = remember { HashMap<String, Entity>() }
    val skies = remember { arrayOfNulls<TextureResource>(1) }
    val appVersion = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }

    val toggleWindow: (String) -> Unit = { id ->
        val shown = Game.window
        if (shown != null) Game.closeWindow(shown)
        if (shown != id) {
            Game.windowShown(id)
            navigator.openWindowContainer(id)
        }
    }

    DisposableEffect(Unit) {
        Game.init(context)
        Game.closeWindow = { id ->
            navigator.closeWindowContainer(id)
            Game.windowGone(id)
        }
        val hd = runCatching { head.start() }
        Log.i(TAG, "head ${hd.getOrNull() ?: hd.exceptionOrNull()?.message}")
        onDispose {
            runCatching { head.stop() }
            subscription[0]?.cancel()
            Game.closeWindow(LEVELS_ID)
            Game.closeWindow(GUIDE_ID)
            Game.closeWindow = {}
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val music = Music(context).also {
            it.muted = !Game.musicOn
            Game.music = it
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) music.resume() else if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) music.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            music.release()
            Game.music = null
        }
    }
    // The land of the level.
    LaunchedEffect(Game.playVersion, view) {
        val v = view ?: return@LaunchedEffect
        val p = Game.play ?: return@LaunchedEffect
        synchronized(Game.lock) { v.build(p); v.receiveAll() }
    }
    LaunchedEffect(Game.trophies, view) { view?.trophies(Game.trophies) }
    LaunchedEffect(Game.faceRequest) { if (Game.faceRequest > 0) seat.again() }
    LaunchedEffect(Game.noteVersion) {
        val n = Game.note ?: return@LaunchedEffect
        kotlinx.coroutines.delay(if (n.length > 30) 7000 else 3500)
        if (Game.note == n) Game.note = null
    }
    LaunchedEffect(Game.debugOpen) {
        when (Game.debugOpen ?: return@LaunchedEffect) {
            "levels" -> if (Game.window != LEVELS_ID) toggleWindow(LEVELS_ID)
            "guide" -> if (Game.window != GUIDE_ID) toggleWindow(GUIDE_ID)
            "menu" -> Game.menuOpen = true
            "close" -> Game.window?.let { Game.closeWindow(it) }
        }
        Game.debugOpen = null
    }

    SideEffect {
        fun show(id: String, on: Boolean) = panels[id]?.let { if (it.enabled != on) it.enabled = on }
        val ph = Game.phase
        show(PANEL_CARD, ph == Game.Phase.CARD)
        show(PANEL_INTRO, ph == Game.Phase.INTRO)
        show(PANEL_RESULT, ph == Game.Phase.RESULT)
        show(PANEL_SHOW, ph == Game.Phase.SHOW)
        show(PANEL_HUD, ph == Game.Phase.PLAYING)
        show(PANEL_NOTE, Game.note != null)
    }

    SpatialView(
        Modifier.pointerInput(Unit) {
            detectSpatialPointerEvent(context, TargetEntity.any()) { infos ->
                val v = view
                if (v != null) for (i in infos) {
                    if (!i.isDownEvent()) continue
                    val t = v.target(i.targetedEntity) ?: continue
                    main.post { Game.tap(t) }
                }
                false
            }
        },
        attachments = {
            AttachmentPanel(PANEL_CARD) { CardPanel(converter) }
            AttachmentPanel(PANEL_INTRO) { IntroPanel(converter) }
            AttachmentPanel(PANEL_HUD) { HudPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_RESULT) { ResultPanel(converter, toggleWindow) }
            AttachmentPanel(PANEL_SHOW) { ShowPanel(converter) }
            AttachmentPanel(PANEL_NOTE) { NotePanel(converter) }
            AttachmentPanel(PANEL_MENU) {
                MenuPanel(converter, toggleWindow, faceMe = { Game.faceRequest++ }, quit = { context.findActivity()?.finishAndRemoveTask() }, version = appVersion)
            }
        },
        update = { _, attachments ->
            view?.let {
                attach(seat, attachments, panels)
                val ph = Game.phase
                panels[PANEL_CARD]?.enabled = ph == Game.Phase.CARD
                panels[PANEL_INTRO]?.enabled = ph == Game.Phase.INTRO
                panels[PANEL_RESULT]?.enabled = ph == Game.Phase.RESULT
                panels[PANEL_SHOW]?.enabled = ph == Game.Phase.SHOW
                panels[PANEL_HUD]?.enabled = ph == Game.Phase.PLAYING
                panels[PANEL_NOTE]?.enabled = Game.note != null
            }
        },
        initial = { content, attachments ->
            content.addEntity(Entity().apply {
                components.set(DirectionalLightComponent(Color(0xFFFFE9D0).toColor4(), 1000f, true))
                components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(-65f, 15f, 0f))
            })
            val tex = HashMap<String, TextureResource>()
            var ibl: TextureResource? = null
            withContext(Dispatchers.IO) {
                for (name in TEXTURES) {
                    runCatching { TextureResource.load("textures/$name.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }
                        .onSuccess { tex[name] = it }
                        .onFailure { Log.i(TAG, "texture $name: ${it.message}") }
                }
                skies[0] = runCatching { TextureResource.load("textures/sky_room.jpg", LoadType.FROM_ASSETS, TextureCreateOption()).shared() }.getOrNull()
                ibl = runCatching { TextureResource.load("textures/ibl_room.ktx", LoadType.FROM_ASSETS, TextureCreateOption().apply { textureEncoding = TextureEncoding.LINEAR }).shared() }.getOrNull()
            }
            val models = HashMap<String, Entity>()
            for (name in MODELS) {
                runCatching { Entity.loadSuspend("asset://models/$name.glb") }
                    .onSuccess { models[name] = it }
                    .onFailure { Log.i(TAG, "model $name: ${it.message}") }
            }
            // The panorama all around, centered on the player's eyes.
            val s = models["sky"]?.clone(Entity.CloneOptions(true, true))
                ?: model(com.pico.spatial.core.ecs.resource.MeshResource.createSphere(1f), unlit(color(0.1f, 0.08f, 0.14f)).apply { setCullingMode(MaterialCullingMode.FRONT) })
            s.setScale(if (models["sky"] != null) 9f / 12f else 9f)
            s.setPos(Vector3(0f, 1.2f, 0f))
            skies[0]?.let { t -> setSky(s, UnlitMaterial.create().apply { setBaseColorTexture(t) }) }
            val world = Entity()
            world.addChild(s)
            content.addEntity(world)
            val v = RailView(models, tex)
            seat.world = world
            seat.machine = v.root
            world.addChild(v.root)
            attach(seat, attachments, panels)
            ibl?.let { v.useIbl(it) }
            Game.view = v
            Game.sound = Sound(v.board).also { it.load() }
            view = v
            Game.play?.let { v.build(it) } ?: run { Game.intro(com.vrgames.rail.core.Stages.ALL[0]); Game.toCard(); Game.play?.let { v.build(it) } }
            AutoPilot.ready()
            subscription[0] = content.subscribe(SceneEvents.Update::class.java) { e ->
                val dt = e.deltaTime.toDouble().coerceIn(0.0, 0.1)
                val pose = runCatching { if (head.state == DataProvider.State.STARTED) head.latestData?.hmdPose else null }.getOrNull()
                seat.frame(pose?.position, pose?.rotation, dt)
                main.post { Game.tick(dt) }
                synchronized(Game.lock) {
                    val p = Game.play
                    if (p != null) v.sync(p, dt)
                }
            }
        },
    )
}

private fun setSky(e: Entity, m: com.pico.spatial.core.ecs.resource.Material) {
    e.components[com.pico.spatial.core.ecs.ModelComponent::class.java]?.materials?.let { mats -> for (i in 0 until mats.size) mats[i] = m }
    e.getChildren().forEach { setSky(it, m) }
}

private val TEXTURES = listOf("grass", "water")
private val MODELS = listOf("sky", "loco", "tender", "freight", "tanker", "coach", "station", "tower", "tunnel", "bridge", "house1", "house2", "factory", "mine", "farm", "tree", "rock", "trophy")

private fun attach(seat: Seat, a: SpatialViewAttachments, panels: HashMap<String, Entity>) {
    val world = seat.world ?: return
    fun put(id: String, pos: Vector3, pitch: Float, yaw: Float = 0f) {
        val e = a.entity(id) ?: return
        if (panels[id] === e) return
        panels[id] = e
        world.addChild(e)
        e.setPos(pos)
        e.transform().eulerAngles = EulerAngles(pitch, yaw, 0f)
    }
    // Cards above the board's far edge, the panel with the buttons at the left, turned toward the player.
    put(PANEL_CARD, Vector3(0f, 1.3f, -1.2f), -4f)
    put(PANEL_INTRO, Vector3(0f, 1.3f, -1.2f), -4f)
    put(PANEL_RESULT, Vector3(0f, 1.3f, -1.2f), -4f)
    put(PANEL_SHOW, Vector3(0f, 1.42f, -1.2f), -4f)
    put(PANEL_HUD, Vector3(-0.76f, 1.02f, -0.72f), -14f, 42f)
    put(PANEL_NOTE, Vector3(0f, 1.2f, -1.25f), -4f)
    put(PANEL_MENU, Vector3(-0.72f, 0.66f, -0.56f), -35f, 38f)
}

/**
 * Where the player sits: the world turned once to the mean heading of the first second of tracking
 * (and on 「摆到我正前方」), the machine [AHEAD] meters in front.
 */
private class Seat {
    var world: Entity? = null
    var machine: Entity? = null
    private val facing = com.vrgames.rail.core.Facing()
    private var waited = 0.0
    private var shown = false

    fun again() = facing.again()

    /** The scene is placed once and then stays, whatever the head does. It stays hidden until then. */
    fun frame(eye: Vector3?, rot: Quat?, dt: Double) {
        if (!shown) {
            waited += dt
            if (facing.placed || waited >= com.vrgames.rail.core.Facing.GIVE_UP_S) shown = true
            world?.let { if (it.enabled != shown) it.enabled = shown }
        }
        val d = Game.debugView?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }
        if (d != null && d.size >= 6) {
            machine?.setPos(Vector3(d[0], d[1], d[2]))
            machine?.transform()?.eulerAngles = EulerAngles(d[5], d[3], 0f)
            machine?.transform()?.setScaleVector(Vector3(d[4]))
            return
        }
        if (rot == null || eye == null) return
        val f = rot.rotateVector(Vector3(0f, 0f, -1f))
        // Looking nearly straight up or down says nothing about the heading.
        val head = if (hypot(f.x, f.z) < 0.2f) null else com.vrgames.rail.core.Facing.heading(f.x.toDouble(), f.z.toDouble())
        if (facing.update(head, dt)) {
            shown = true
            world?.enabled = true
            world?.let {
                it.setPos(Vector3(eye.x, 0f, eye.z))
                it.transform().eulerAngles = EulerAngles(0f, facing.yaw.toFloat(), 0f)
            }
            machine?.setPos(Vector3(0f, 0f, -AHEAD))
            machine?.transform()?.eulerAngles = EulerAngles(0f, 0f, 0f)
            machine?.transform()?.setScaleVector(Vector3(1f))
            Log.i(TAG, "facing placed at ${facing.yaw}, eye $eye")
        }
    }

    companion object {
        // The board's near edge stands about half a meter out.
        const val AHEAD = com.vrgames.rail.core.Board.AHEAD.toFloat()
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
