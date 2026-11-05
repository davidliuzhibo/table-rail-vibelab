package com.vrgames.rail.render

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.pico.spatial.core.math.Vector3

/** Effects, all made by tools/sfx_synth.py. */
enum class Sfx(val file: String, val volume: Float) {
    LAY("lay", 0.7f),
    MARK("mark", 0.5f),
    REFUSE("refuse", 0.6f),
    SWITCH("switch_", 0.75f),
    WHISTLE("whistle", 0.75f),
    STEAM("steam", 0.45f),
    LOAD("load", 0.6f),
    DELIVER("deliver", 0.8f),
    CRASH("crash", 0.85f),
    GROW("grow", 0.6f),
    FANFARE("fanfare", 0.9f),
    LOSE("lose", 0.8f),
}

/** One emitter per clip, on [parent]: the same sound again restarts it. Positions in the board's frame. */
class Sound(private val parent: Entity) {
    private val emitters = HashMap<Sfx, Entity>()
    private val controllers = HashMap<Sfx, AudioPlayerController>()

    fun load() {
        for (s in Sfx.entries) {
            runCatching {
                val res = AudioResource.load(s.file, "asset://audio/${s.file}.wav", LoadType.FROM_ASSETS)
                val e = Entity()
                e.components.set(ObjectAudioComponent(s.volume))
                parent.addChild(e)
                val c = e.prepareAudio(res)
                if (c.valid) {
                    emitters[s] = e
                    controllers[s] = c
                }
            }.onFailure { android.util.Log.w("RailSound", "sfx ${s.file}: ${it.message}") }
        }
    }

    fun play(s: Sfx, at: Vector3 = Vector3(0f, 0.05f, 0f)) {
        val c = controllers[s] ?: return
        emitters[s]?.setPos(at)
        runCatching {
            if (c.isPlaying()) c.stop()
            c.seekTo(0)
            c.play()
        }
    }
}
