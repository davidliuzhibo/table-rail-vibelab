package com.vrgames.rail.game

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pico.spatial.core.math.Vector3
import com.vrgames.rail.core.Cell
import com.vrgames.rail.core.Ev
import com.vrgames.rail.core.Play
import com.vrgames.rail.core.Dispatcher
import com.vrgames.rail.core.Stage
import com.vrgames.rail.core.Stages
import com.vrgames.rail.render.Music
import com.vrgames.rail.render.RailView
import com.vrgames.rail.render.Sfx
import com.vrgames.rail.render.Sound
import com.vrgames.rail.render.Target

/**
 * State the stage, its panels and the windows share. [play] is the level being played; the frame
 * loop steps it and turns its events into notes, sounds and bursts. Best stars per level are saved.
 */
object Game {
    private const val PREFS = "rail"
    private const val TAG = "RailGame"

    enum class Phase { CARD, INTRO, PLAYING, RESULT, SHOW }

    val lock = Any()
    private var prefs: android.content.SharedPreferences? = null
    val best = LinkedHashMap<String, Int>()
    var play: Play? = null; private set
    /** A new [play]: the view builds its land. */
    var playVersion by mutableIntStateOf(0); private set
    var stage by mutableStateOf(Stages.ALL[0]); private set
    var phase by mutableStateOf(Phase.CARD)
    var version by mutableIntStateOf(0); private set
    var note by mutableStateOf<String?>(null)
    var noteVersion by mutableIntStateOf(0); private set
    var window by mutableStateOf<String?>(null); private set
    private var windowCopy: Any? = null
    var closeWindow: (String) -> Unit = {}
    var menuOpen by mutableStateOf(false)
    var confirmRestart by mutableStateOf(false)
    var musicOn by mutableStateOf(true); private set
    var faceRequest by mutableIntStateOf(0)
    var view: RailView? = null
    var sound: Sound? = null
    var music: Music? = null
    var trophiesBefore = 0
    /** The test hook, and the show's driver. */
    var bot: Dispatcher? = null
    var timeScale = 1.0
    var debugView by mutableStateOf<String?>(null)
    var debugOpen by mutableStateOf<String?>(null)
    private var lastSecond = -1

    val stars get() = best.values.sum()
    val maxStars get() = Stages.ALL.size * 3
    val bronze get() = Stages.ALL.all { (best[it.id] ?: 0) > 0 }
    val silver get() = stars >= maxStars * 2 / 3
    val gold get() = stars >= maxStars
    val trophies get() = if (gold) 3 else if (silver) 2 else if (bronze) 1 else 0

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { p ->
            musicOn = p.getBoolean("music", true)
            p.getString("best", "")!!.split(",").filter { it.contains(":") }.forEach {
                val (k, v) = it.split(":")
                best[k] = v.toIntOrNull() ?: 0
            }
        }
        Log.i(TAG, "stars $stars/$maxStars")
        AutoPilot.register(context)
    }

    fun say(text: String) {
        note = text
        noteVersion++
    }

    fun open(s: Stage): Boolean {
        val k = Stages.ALL.indexOf(s)
        return k == 0 || (best[Stages.ALL[k - 1].id] ?: 0) > 0 || (best[s.id] ?: 0) > 0
    }

    /** From the card: the first level not cleared yet. */
    fun start() = intro(Stages.ALL.firstOrNull { (best[it.id] ?: 0) == 0 } ?: Stages.ALL.last())

    private fun fresh(s: Stage) {
        stage = s
        synchronized(lock) { play = Play(s) }
        bot = null
        playVersion++
    }

    fun intro(s: Stage) {
        if (!open(s)) {
            say("这一关还没开放：先过前面的。")
            return
        }
        fresh(s)
        phase = Phase.INTRO
        version++
    }

    fun begin() {
        fresh(stage)
        phase = Phase.PLAYING
        music?.resume()
        Log.i(TAG, "begin ${stage.id}")
        say("捏线路的起点，再捏终点，轨道就铺好了。铺好按「开张」。")
        version++
    }

    fun retry() = begin()

    fun next() {
        val k = Stages.ALL.indexOf(stage)
        if (k + 1 < Stages.ALL.size) intro(Stages.ALL[k + 1]) else toCard()
    }

    fun toCard() {
        phase = Phase.CARD
        version++
    }

    /** A pinch on the board or on a train. */
    fun tap(t: Target) {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        synchronized(lock) {
            when (t) {
                is Target.OnCell -> tapCell(p, t.c)
                is Target.OnTrain -> {
                    val train = p.trains.getOrNull(t.id) ?: return
                    // Laying or taking up a line, the pinch means the track under it. Before opening, too.
                    if (p.anchor != null || p.erasing || !p.running) tapCell(p, train.cell) else p.pinchTrain(t.id)
                }
            }
        }
        version++
    }

    private fun tapCell(p: Play, c: Cell) {
        val before = p.anchor
        p.tap(c)
        if (p.anchor != before && before == null) {
            sound?.play(Sfx.MARK, view?.boardPoint(c.x + 0.5, c.y + 0.5) ?: Vector3(0f, 0.05f, 0f))
            say("再捏线路的终点。捏同一格是取消。")
        }
    }

    fun go() {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        synchronized(lock) { p.open() }
        version++
    }

    fun buy() {
        synchronized(lock) { play?.buy() }
        version++
    }

    fun hold(id: Int) {
        synchronized(lock) { play?.hold(id) }
        version++
    }

    fun erasing(on: Boolean) {
        synchronized(lock) { play?.setErasing(on) }
        if (on) say("拆除：捏哪段轨道就拆哪段。")
        version++
    }

    fun undo() {
        synchronized(lock) { play?.undo() }
        version++
    }

    fun clearTrack() {
        synchronized(lock) { play?.clear() }
        version++
    }

    fun tick(dt0: Double) {
        val dt = dt0 * timeScale
        val p = play ?: return
        if (phase != Phase.PLAYING && phase != Phase.SHOW) return
        if (phase == Phase.SHOW) view?.show(dt)
        val b = bot
        val evs = synchronized(lock) { if (b != null) p.step(dt) { b.update() } else p.step(dt) }
        var changed = false
        for (e in evs) {
            changed = true
            when (e) {
                is Ev.Note -> say(e.text)
                is Ev.Refused -> { say(e.why); sound?.play(Sfx.REFUSE) }
                is Ev.Laid -> sound?.play(Sfx.LAY)
                is Ev.Removed -> { sound?.play(Sfx.LAY); say("拆了 ${e.cells} 格，钱退回来了。") }
                Ev.Opened -> { sound?.play(Sfx.WHISTLE); say("开张！订单来了就捏发货的车站。") }
                is Ev.NewOrder -> { sound?.play(Sfx.STEAM); say((if (e.order.def.rush) "急单！赏 ${e.order.def.pay}，赶不上也不扣信誉：" else "新单：") + "${p.stop(e.order.def.from).title}有${e.order.def.cargo.title}，送到${p.stop(e.order.def.to).title}。") }
                is Ev.Picked -> say("选中了这列空车。再捏一个车站，就派它去。")
                is Ev.Sent -> sound?.play(Sfx.SWITCH)
                is Ev.Loaded -> sound?.play(Sfx.LOAD)
                is Ev.Delivered -> onDelivered(p, e)
                is Ev.Late -> { sound?.play(Sfx.LOSE); say(if (e.order.def.rush) "急单飞了，不扣信誉。" else "超时了：${p.stop(e.order.def.to).title}的${e.order.def.cargo.title}没送到，信誉少一颗。") }
                is Ev.Crash -> { sound?.play(Sfx.CRASH); view?.burst(e.at.x, e.at.y, 40) }
                is Ev.Bought -> { sound?.play(Sfx.WHISTLE); say("新车到了。") }
                is Ev.Grew -> sound?.play(Sfx.GROW)
                is Ev.Washout -> { sound?.play(Sfx.CRASH); view?.burst(e.cell.x + 0.5, e.cell.y + 0.5, 30); say("塌方！轨道被埋了：捏那一格修好，要 ${p.cost(e.cell) * Play.REPAIR}。") }
                is Ev.Repaired -> { sound?.play(Sfx.LAY); say("修好了，花了 ${e.cost}。") }
                is Ev.Tip -> say("提前送到，客户给了小费 +${e.amount}。")
                Ev.Won -> finish(true)
                Ev.Lost -> finish(false)
            }
        }
        val second = p.time.toInt()
        if (changed || second != lastSecond) version++
        lastSecond = second
    }

    private fun onDelivered(p: Play, e: Ev.Delivered) {
        val s = p.stops.firstOrNull { it.id == e.stop }
        sound?.play(Sfx.DELIVER, s?.let { view?.boardPoint(it.at.x + 0.5, it.at.y + 0.5) } ?: Vector3(0f, 0.05f, 0f))
        s?.let { view?.burst(it.at.x + 0.5, it.at.y + 0.5) }
    }

    private fun finish(won: Boolean) {
        val p = play ?: return
        if (phase != Phase.PLAYING) return
        trophiesBefore = trophies
        if (won) {
            val s = p.stars()
            if (s > (best[p.stage.id] ?: 0)) best[p.stage.id] = s
        }
        save()
        phase = Phase.RESULT
        sound?.play(if (won) Sfx.FANFARE else Sfx.LOSE)
        Log.i(TAG, "finish ${p.stage.id} won=$won stars=${p.stars()} time=${"%.1f".format(p.time)} done=${p.delivered}/${p.total} late=${p.late} crashes=${p.crashes}")
        version++
    }

    /** The gold trophy's show: three trains round the whole board, the town full, fireworks over it. */
    fun show() {
        if (!gold && !AutoPilot.debug) return
        stage = Stages.SHOW
        synchronized(lock) { play = Play(Stages.SHOW).also { bot = Dispatcher(it) } }
        playVersion++
        phase = Phase.SHOW
        sound?.play(Sfx.FANFARE)
        say("全线通车！")
        version++
    }

    fun endShow() {
        stage = Stages.ALL[0]
        fresh(stage)
        toCard()
    }

    private fun save() {
        prefs?.edit()?.putString("best", best.entries.joinToString(",") { "${it.key}:${it.value}" })?.apply()
    }

    fun restart() {
        best.clear()
        save()
        fresh(Stages.ALL[0])
        phase = Phase.CARD
        version++
    }

    fun toggleMusic() {
        musicOn = !musicOn
        music?.muted = !musicOn
        prefs?.edit()?.putBoolean("music", musicOn)?.apply()
    }

    fun grant(n: Int) {
        for (s in Stages.ALL) best[s.id] = n
        save()
        version++
    }

    fun windowShown(id: String, copy: Any? = null) {
        window = id
        if (copy != null) windowCopy = copy
        version++
    }

    fun windowGone(id: String, copy: Any? = null) {
        if (copy != null && copy !== windowCopy) return
        if (window == id) window = null
        version++
    }
}
