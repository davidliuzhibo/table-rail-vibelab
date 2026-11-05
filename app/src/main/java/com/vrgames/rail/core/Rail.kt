package com.vrgames.rail.core

import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A cell of the board: x to the right, y toward the player. */
data class Cell(val x: Int, val y: Int) {
    operator fun plus(d: Dir) = Cell(x + d.dx, y + d.dy)
}

enum class Dir(val dx: Int, val dy: Int, val bit: Int) {
    N(0, -1, 1), E(1, 0, 2), S(0, 1, 4), W(-1, 0, 8);

    val back get() = when (this) { N -> S; E -> W; S -> N; W -> E }
}

enum class Ground { GRASS, RIVER, HILL, ROCK }

enum class Cargo(val title: String) { COAL("煤"), GOODS("货物"), OIL("油"), PEOPLE("乘客"), WOOD("木材") }

/** A place on the line. Its building stands on [side] of its track cell. */
class Stop(val id: String, val at: Cell, val side: Dir, val model: String, val title: String) {
    val building get() = at + side
}

/** A train as the level gives it: where it starts, and what its wagons can carry. */
class TrainDef(val start: String, val cargo: Cargo, val cars: Int)

/** An order as the level schedules it: at [at] seconds, [from] has [cargo] that [to] wants within [window]. */
class OrderDef(
    val at: Double,
    val cargo: Cargo,
    val from: String,
    val to: String,
    val window: Double,
    val pay: Int,
    /** A rush job: well paid, short, and letting it go costs no reputation. Taking it is the player's gamble. */
    val rush: Boolean = false,
)

class Order(val id: Int, val def: OrderDef) {
    var left = def.window
    var taken: Train? = null
    var loaded = false
    var done = false
    var late = false
    val open get() = !done && !late
}

data class Pt(val x: Double, val y: Double)

/** One moment of a train's way: enough to run it back from there. */
class Sample(val cell: Cell, val from: Dir, val to: Dir, val t: Double, val at: Pt)

class Train(val id: Int, val def: TrainDef, var cell: Cell) {
    var from = Dir.W
    var to: Dir? = null
    /** Through the current cell, 0..1. */
    var t = 0.5
    /** Where it has been sent; null when it stands idle. */
    var dest: Stop? = null
    /** The exits to take, one for each cell after this one. */
    val path = ArrayDeque<Dir>()
    var order: Order? = null
    var wait = 0.0
    var held = false
    var crashed = 0.0
    /** The locomotive is at the far end, pushing: the train has turned back without turning round. */
    var pushing = false
    var blocked = 0.0
    /** Backing out of this stretch to let another through. */
    var retreat: Set<Cell>? = null
    /** Where the front has been, newest first: the rest of the train follows it. */
    val trail = ArrayDeque<Sample>()
    val length get() = Play.PITCH * def.cars + Play.PITCH / 2
    val loaded get() = order?.loaded == true
    val idle get() = dest == null && order == null && wait <= 0 && crashed <= 0
    /** Loaded at its station and waiting for the player to set it off. */
    val ready get() = order?.loaded == true && dest == null && wait <= 0 && crashed <= 0
    /** Loading right now: a pinch sets it off as soon as it is done. */
    val loading get() = order?.let { !it.loaded } == true && wait > 0 && dest?.id == order?.def?.from
    /** Standing at a station with nothing under way: others pass as they do an empty one. */
    val parked get() = idle || ready
    var goWhenLoaded = false
}

sealed interface Ev {
    data class Laid(val cells: Int, val cost: Int) : Ev
    data class Refused(val why: String) : Ev
    data class Removed(val cells: Int) : Ev
    data object Opened : Ev
    data class NewOrder(val order: Order) : Ev
    data class Picked(val train: Int) : Ev
    data class Sent(val train: Int, val stop: String) : Ev
    data class Loaded(val stop: String) : Ev
    data class Delivered(val stop: String, val pay: Int) : Ev
    data class Late(val order: Order) : Ev
    data class Crash(val at: Pt) : Ev
    data class Washout(val cell: Cell) : Ev
    data class Repaired(val cell: Cell, val cost: Int) : Ev
    data class Tip(val amount: Int) : Ev
    data class Bought(val train: Int) : Ev
    data class Grew(val index: Int) : Ev
    data class Note(val text: String) : Ev
    data object Won : Ev
    data object Lost : Ev
}

/** One level as data. */
class Stage(
    val id: String,
    val act: Int,
    val title: String,
    val ask: String,
    val stops: List<Stop>,
    val ground: Map<Cell, Ground>,
    val trains: List<TrainDef>,
    val orders: List<OrderDef>,
    /** The money at the start. */
    val money: Int,
    /** What another train costs and what it carries, if one can be bought. */
    val shop: TrainDef? = null,
    val price: Int = 0,
    /** At these times a landslide buries the track at this cell, if there is track there. */
    val washouts: List<Pair<Double, Cell>> = emptyList(),
    /** How the bot lays its line: pairs of anchors, done as the money comes. */
    val plan: List<Pair<Cell, Cell>> = emptyList(),
    /** How many trains the bot buys, as soon as it can. */
    val buys: Int = 0,
)

/**
 * A level being played. Orders come up at the stops, each with its time. The player lays the track,
 * and sends trains: a train goes to the stop it is sent to, finding its own way and throwing the
 * switches, loads what waits there and carries it to where it is wanted. Money comes from deliveries
 * and goes back into track and trains. A late order costs reputation; out of it, the level is lost.
 */
class Play(val stage: Stage) {
    /** Each cell with track, and which of its sides are joined: lines only meet where they were laid to. */
    val links = HashMap<Cell, Int>()
    val trains = ArrayList<Train>()
    val stops = stage.stops
    val orders = ArrayList<Order>()
    var trackVersion = 0; private set
    var money = stage.money; private set
    var reputation = REPUTATION; private set
    var time = 0.0; private set
    var crashes = 0; private set
    var late = 0; private set
    var delivered = 0; private set
    var over = false; private set
    var won = false; private set
    var anchor: Cell? = null; private set
    var running = false; private set
    var erasing = false; private set
    var selected: Train? = null; private set
    var grown = 0; private set
    /** Track buried by a landslide: the cell and the sides it joined, till it is dug out. */
    val damaged = HashMap<Cell, Int>()
    var tips = 0; private set
    private var nextOrder = 0
    private var nextWash = 0
    private var noRoute = 0.0
    private val events = ArrayList<Ev>()
    private val history = ArrayList<Triple<Map<Cell, Int>, Int, Int>>()
    val canUndo get() = history.isNotEmpty() && !over

    init {
        for (s in stops) links[s.at] = 0
        stage.trains.forEach { addTrain(it) }
    }

    private fun addTrain(d: TrainDef) {
        val t = Train(trains.size, d, stops.first { it.id == d.start }.at)
        trains += t
        rest(t)
    }

    fun ground(c: Cell) = stage.ground[c] ?: Ground.GRASS
    fun inside(c: Cell) = c.x in 0 until W && c.y in 0 until H
    fun hasTrack(c: Cell) = c in links
    fun cost(c: Cell) = when (ground(c)) { Ground.GRASS -> 1; Ground.RIVER, Ground.HILL -> 3; Ground.ROCK -> 99 }
    fun isBuilding(c: Cell) = stops.any { it.building == c }
    fun stopAt(c: Cell) = stops.firstOrNull { it.at == c }
    fun stop(id: String) = stops.first { it.id == id }
    fun waitingAt(s: Stop) = orders.filter { it.open && it.def.from == s.id && it.taken == null }
    fun wantedAt(s: Stop) = orders.filter { it.open && it.def.to == s.id }
    val total get() = stage.orders.size

    fun ways(c: Cell): List<Dir> {
        val m = links[c] ?: return emptyList()
        return Dir.entries.filter { m and it.bit != 0 }
    }

    fun isJunction(c: Cell) = ways(c).size >= 3

    // ----- Laying track -----

    /** The cheapest line from [a] to [b], in as few bends as it can, using what is there when it is on the way. */
    fun route(a: Cell, b: Cell): List<Cell>? {
        if (!inside(a) || !inside(b)) return null
        data class Node(val c: Cell, val d: Dir?)
        val dist = HashMap<Node, Int>()
        val prev = HashMap<Node, Node>()
        val open = PriorityQueue<Pair<Int, Node>>(compareBy { it.first })
        val first = Node(a, null)
        dist[first] = 0
        open += 0 to first
        var end: Node? = null
        while (open.isNotEmpty()) {
            val (d, n) = open.poll()
            if (d > (dist[n] ?: Int.MAX_VALUE)) continue
            if (n.c == b) { end = n; break }
            for (dir in Dir.entries) {
                val c = n.c + dir
                if (!inside(c) || ground(c) == Ground.ROCK || isBuilding(c)) continue
                // A bridge is not weighed at its full price: a line goes where the player pointed
                // rather than wander onto another line to save a little.
                val joined = (links[n.c] ?: 0) and dir.bit != 0
                val step = if (joined) 9 else if (c in links || cost(c) == 1) 10 else 20
                val bend = if (n.d != null && n.d != dir) 2 else 0
                val m = Node(c, dir)
                val nd = d + step + bend
                if (nd < (dist[m] ?: Int.MAX_VALUE)) {
                    dist[m] = nd
                    prev[m] = n
                    open += nd to m
                }
            }
        }
        var n = end ?: return null
        val path = ArrayList<Cell>()
        while (true) {
            path += n.c
            n = prev[n] ?: break
        }
        return path.reversed()
    }

    fun setErasing(on: Boolean) {
        erasing = on
        anchor = null
        selected = null
    }

    /**
     * A pinch on a cell. A pinch on a stop sends the picked train there, or the nearest free one.
     * Anywhere else the first marks where to lay from, the next lays the line there and goes on from
     * it, and the same cell again lets go.
     */
    fun tap(pinched: Cell) {
        if (over || !inside(pinched)) return
        if (pinched in damaged) { repair(pinched); return }
        if (erasing) { removeRun(pinched); return }
        val c = snapToStop(pinched)
        val a = anchor
        if (a == null) {
            val s = stopAt(c)
            if (s != null && running) { sendTo(s); return }
            if (ground(c) == Ground.ROCK || isBuilding(c)) {
                events += Ev.Refused(if (ground(c) == Ground.ROCK) "石头上铺不了，从旁边绕。" else "这里是建筑。捏它门口的轨道。")
                return
            }
            anchor = c
            return
        }
        if (a == c) { anchor = null; return }
        lay(a, c)
    }

    /**
     * What a pinch means near a station: the whole station stands for its door. A station is what a line
     * starts and ends at, and it should not take a careful aim: its building and the width beside it
     * count, unless track already runs there.
     */
    fun snapToStop(c: Cell): Cell {
        if (c in links || stopAt(c) != null) return c
        for (s in stops) {
            if (s.building == c) return s.at
            val beside = if (s.side == Dir.N || s.side == Dir.S) listOf(Cell(s.building.x - 1, s.building.y), Cell(s.building.x + 1, s.building.y))
            else listOf(Cell(s.building.x, s.building.y - 1), Cell(s.building.x, s.building.y + 1))
            if (c in beside && ground(c) != Ground.ROCK && !isBuilding(c)) return s.at
        }
        return c
    }

    /** Trains can only be pinched while dispatching. Laying or taking up track, the pinch goes through them to the board. */
    val trainsPickable get() = running && anchor == null && !erasing && !over

    /** Lays the line from [a] to [b] if it can be paid for. */
    fun lay(a: Cell, b: Cell): Boolean {
        if (ground(b) == Ground.ROCK || isBuilding(b)) {
            events += Ev.Refused(if (ground(b) == Ground.ROCK) "石头上铺不了，从旁边绕。" else "这里是建筑。捏它门口的轨道。")
            return false
        }
        val path = route(a, b)
        if (path == null || path.size < 2) {
            events += Ev.Refused("这两处之间铺不过去。")
            anchor = null
            return false
        }
        val new = path.filter { it !in links }
        val price = new.sumOf { cost(it) }
        if (price > money) {
            events += Ev.Refused("钱不够：这段要 $price，现在有 $money。送货能挣钱，也可以拆掉用不着的轨道。")
            return false
        }
        history += Triple(HashMap(links), money, trackVersion)
        for (i in 0 until path.size - 1) join(path[i], path[i + 1])
        money -= price
        anchor = b
        changed()
        events += Ev.Laid(new.size, price)
        return true
    }

    /** Digs out a landslide: dearer than the track was, and the line is whole again. */
    fun repair(c: Cell) {
        val mask = damaged[c] ?: return
        val price = cost(c) * REPAIR
        if (price > money) {
            events += Ev.Refused("钱不够：修这段要 $price，现在有 $money。")
            return
        }
        money -= price
        damaged.remove(c)
        links[c] = mask
        for (d in Dir.entries) if (mask and d.bit != 0) links[c + d] = (links[c + d] ?: 0) or d.back.bit
        changed()
        events += Ev.Repaired(c, price)
    }

    private fun washOut(c: Cell) {
        // No track there any more, a stop, or a train on it: the hill holds a while longer.
        if (c !in links || stopAt(c) != null) return
        val mask = links[c] ?: return
        if (trains.any { c in occupied(it) }) return
        for (d in ways(c)) links[c + d] = (links[c + d] ?: 0) and d.back.bit.inv()
        links.remove(c)
        damaged[c] = mask
        history.clear()
        changed()
        events += Ev.Washout(c)
    }

    /** What a line would cost, without laying it. */
    fun priceOf(a: Cell, b: Cell): Int = route(a, b)?.filter { it !in links }?.sumOf { cost(it) } ?: Int.MAX_VALUE

    private fun join(a: Cell, b: Cell) {
        val d = Dir.entries.first { a + it == b }
        links[a] = (links[a] ?: 0) or d.bit
        links[b] = (links[b] ?: 0) or d.back.bit
    }

    private fun changed() {
        trackVersion++
        for (t in trains) if (t.dest == null) rest(t) else replan(t)
    }

    /** Takes up the piece of line under a pinch: from this cell out to the nearest stop or junction each way. */
    fun removeRun(c: Cell) {
        if (over || c !in links) return
        val run = run(c)
        if (run.isEmpty()) {
            events += Ev.Refused("这里只有车站，没有可拆的轨道。")
            return
        }
        if (trains.any { t -> occupied(t).any { it in run } }) {
            events += Ev.Refused("列车正压在这段轨道上。")
            return
        }
        for (x in run) {
            for (d in ways(x)) links[x + d] = (links[x + d] ?: 0) and d.back.bit.inv()
            links.remove(x)
            money += cost(x)
        }
        history.clear()
        changed()
        events += Ev.Removed(run.size)
    }

    /** The cells of plain line around [c], not counting stops and junctions. A stop's or junction's own cell gives its spurs. */
    fun run(c: Cell): Set<Cell> {
        val out = LinkedHashSet<Cell>()
        val todo = ArrayDeque<Cell>()
        fun plain(x: Cell) = x in links && stopAt(x) == null && !isJunction(x)
        if (plain(c)) todo += c else return emptySet()
        while (todo.isNotEmpty()) {
            val x = todo.removeFirst()
            if (!out.add(x)) continue
            for (d in ways(x)) if (plain(x + d) && (x + d) !in out) todo += x + d
        }
        return out
    }

    /** Takes back the last line laid, unless a train stands on it. */
    fun undo() {
        val (before, paid) = history.lastOrNull() ?: return
        val gone = links.keys.filter { it !in before }
        if (over || trains.any { t -> occupied(t).any { it in gone } }) {
            if (!over) events += Ev.Refused("列车正压在这段轨道上。")
            return
        }
        history.removeAt(history.size - 1)
        links.clear()
        links.putAll(before)
        money = paid
        anchor = null
        changed()
    }

    /** Clears everything laid: before opening for business. */
    fun clear() {
        if (running || over) return
        links.clear()
        for (s in stops) links[s.at] = 0
        history.clear()
        money = stage.money
        anchor = null
        changed()
    }

    fun buy(): Boolean {
        val d = stage.shop
        if (d == null || over) return false
        if (money < stage.price) {
            events += Ev.Refused("钱不够：一辆车 ${stage.price}，现在有 $money。")
            return false
        }
        money -= stage.price
        addTrain(d)
        events += Ev.Bought(trains.last().id)
        return true
    }

    // ----- Sending trains -----

    /** Opens for business: the clock starts, the orders come. */
    fun open() {
        if (over || running) return
        running = true
        anchor = null
        erasing = false
        events += Ev.Opened
    }

    /** A pinch on a train: pick it, so the next pinch on a stop sends it. Again: drop it. */
    fun pick(id: Int) {
        val t = trains.getOrNull(id) ?: return
        if (over) return
        selected = if (selected === t) null else t
        if (selected != null) events += Ev.Picked(id)
    }

    /** Holds the picked train where it is, or lets it go on. */
    fun hold(id: Int) {
        val t = trains.getOrNull(id) ?: return
        t.held = !t.held
        events += Ev.Note(if (t.held) "这列车停下等待。再按一下继续走。" else "继续走。")
    }

    /** Sends the picked train to [s], or if none is picked the nearest free one that can do what waits there. */
    fun sendTo(s: Stop) {
        if (over) return
        // A pinch on where a loaded train stands, or where its load is going, sets it off.
        if (selected == null) {
            val loaded = trains.filter { t -> (t.ready || t.loading) && t.order?.let { it.def.to == s.id || t.cell == s.at } == true }.minByOrNull { it.order?.left ?: 0.0 }
            if (loaded != null) {
                go(loaded)
                return
            }
        }
        val w = waitingAt(s)
        val t = selected ?: trains.filter { it.idle && (w.isEmpty() || w.any { o -> o.def.cargo == it.def.cargo }) }
            .minByOrNull { cellsTo(it, s.at) }
        if (t == null) {
            events += Ev.Refused("没有空着的车。先捏一列车，再捏车站，可以改派。")
            return
        }
        send(t, s)
    }

    /** A pinch on a train: a loaded one sets off, any other is picked for the next pinch on a station. */
    fun pinchTrain(id: Int) {
        val t = trains.getOrNull(id) ?: return
        if (t.ready || t.loading) go(t) else pick(id)
    }

    /** Sets a loaded train off to where its load is going, or as soon as it has finished loading. */
    fun go(t: Train) {
        val o = t.order ?: return
        if (!o.loaded) {
            t.goWhenLoaded = true
            return
        }
        t.goWhenLoaded = false
        val to = stop(o.def.to)
        t.dest = to
        t.held = false
        selected = null
        if (!plan(t, true)) {
            t.dest = null
            t.path.clear()
            events += Ev.Refused("装好了，可是没有轨道通到${to.title}。")
            return
        }
        events += Ev.Sent(t.id, to.id)
    }

    /** A free train standing at a station takes what it can carry there. It always works this way. */
    private fun autoLoad(t: Train) {
        if (!t.idle || t.retreat != null || t.held) return
        val s = stopAt(t.cell) ?: return
        val o = waitingAt(s).filter { it.def.cargo == t.def.cargo }.minByOrNull { it.left } ?: return
        o.taken = t
        t.order = o
        t.dest = s
        t.path.clear()
        t.wait = WORK
    }

    /** Sends a train: if it is free and something it can carry waits there, it takes that order. */
    fun send(t: Train, s: Stop) {
        if (over) return
        selected = null
        // What is on board decides where it goes.
        if (t.ready || t.loading) {
            go(t)
            return
        }
        if (t.order != null && !t.loaded) {
            // Sent somewhere else: it gives up what it was going to fetch.
            t.order?.taken = null
            t.order = null
        }
        if (t.order == null) {
            val o = waitingAt(s).filter { it.def.cargo == t.def.cargo }.minByOrNull { it.left }
            if (o != null) {
                o.taken = t
                t.order = o
            }
        }
        // Going empty is allowed, but the player should know, and know where the work is.
        val empty = t.order == null
        val elsewhere = if (empty) orders.firstOrNull { it.open && it.taken == null && it.def.cargo == t.def.cargo } else null
        t.dest = s
        t.held = false
        if (!plan(t, turnAllowed = t.wait <= 0)) {
            events += Ev.Refused("这列车到不了${s.title}：还没有轨道连过去。")
            t.dest = null
            t.order?.let { if (!it.loaded) { it.taken = null; t.order = null } }
            return
        }
        events += Ev.Sent(t.id, s.id)
        if (elsewhere != null) events += Ev.Note("空车开往${s.title}。${stop(elsewhere.def.from).title}还有${elsewhere.def.cargo.title}在等，要去${stop(elsewhere.def.to).title}：捏${stop(elsewhere.def.from).title}才会去接。")
    }

    private fun cellsTo(t: Train, c: Cell): Int {
        val to = t.to ?: return 999
        return search(t.cell, to, c, t)?.size ?: 500
    }

    /** A train not on its way faces along the line under it. */
    private fun rest(t: Train) {
        val ws = ways(t.cell)
        val keep = t.to?.takeIf { it in ws }
        val h = keep ?: ws.firstOrNull()
        t.to = h
        if (h != null) t.from = h.back
        t.t = 0.5
    }

    /**
     * The way there from where the front is heading. Weighted to keep off what other trains mean to run
     * on against this one, so where a passing loop exists they take a side each.
     */
    private fun search(start: Cell, heading: Dir, goal: Cell, t: Train): List<Dir>? {
        val against = HashSet<Pair<Cell, Dir>>()
        for (o in trains) if (o !== t && o.to != null) {
            var c = o.cell
            var d = o.to!!
            against += c to d
            for (e in o.path) {
                c += d
                d = e
                against += c to d
            }
        }
        data class Node(val c: Cell, val out: Dir)
        // A node is a cell and the exit it leaves by. The path holds the exits after the first.
        val dist = HashMap<Node, Int>()
        val prev = HashMap<Node, Node>()
        val open = PriorityQueue<Pair<Int, Node>>(compareBy { it.first })
        if (heading !in ways(start)) return if (start == goal) emptyList() else null
        val first = Node(start, heading)
        dist[first] = 0
        open += 0 to first
        var end: Node? = null
        while (open.isNotEmpty()) {
            val (d, n) = open.poll()
            if (d > (dist[n] ?: Int.MAX_VALUE)) continue
            val c = n.c + n.out
            if (c == goal) { end = n; break }
            for (e in ways(c)) {
                if (e == n.out.back) continue
                val m = Node(c, e)
                // Going the other way on another train's stretch: only if nothing else will do.
                val pen = if ((c + e) to e.back in against) 8 else 0
                val nd = d + 1 + pen
                if (nd < (dist[m] ?: Int.MAX_VALUE)) {
                    dist[m] = nd
                    prev[m] = n
                    open += nd to m
                }
            }
        }
        var n = end ?: return null
        val rev = ArrayList<Dir>()
        while (true) {
            val p = prev[n] ?: break
            rev += n.out
            n = p
        }
        return rev.reversed()
    }

    /**
     * Works out the train's path to its stop. Standing at a stop or a junction it may leave by any way
     * out, or turn back the way it came, whichever is shortest.
     */
    private fun plan(t: Train, turnAllowed: Boolean): Boolean {
        val goal = t.dest?.at ?: return false
        t.path.clear()
        if (t.cell == goal && t.t <= 0.5) return true
        val standing = turnAllowed && t.t <= 0.5
        var best: List<Dir>? = null
        var bestOut: Dir? = null
        if (standing) {
            for (e in ways(t.cell)) {
                if (e == t.from && t.trail.isNotEmpty()) continue // The way it came: that is turning back, below.
                val r = search(t.cell, e, goal, t) ?: continue
                if (best == null || r.size < best.size) {
                    best = r
                    bestOut = e
                }
            }
        } else {
            t.to?.let { to ->
                best = search(t.cell, to, goal, t)
                bestOut = to
            }
        }
        if (turnAllowed) {
            // What the other end of the train has ahead of it.
            val (cell, heading) = tailState(t)
            val behind = if (heading != null && t.trail.isNotEmpty()) search(cell, heading, goal, t) else null
            val a = best?.size ?: Int.MAX_VALUE
            val b = behind?.let { it.size + 2 } ?: Int.MAX_VALUE
            if (b < a) {
                turnBack(t)
                t.path.clear()
                t.path.addAll(behind!!)
                return true
            }
        }
        val way = best ?: return false
        if (bestOut != null && bestOut != t.to) t.to = bestOut
        t.path.addAll(way)
        return true
    }

    private fun replan(t: Train) {
        if (t.dest != null && !plan(t, turnAllowed = t.wait <= 0)) t.path.clear()
    }

    /** Where the far end of the train is and which way it would run. */
    private fun tailState(t: Train): Pair<Cell, Dir?> {
        var d = 0.0
        var last = head(t)
        var tail: Sample? = null
        for (s in t.trail) {
            d += hypot(s.at.x - last.x, s.at.y - last.y)
            if (d > t.length) break
            tail = s
            last = s.at
        }
        val s = tail ?: return t.cell to (t.to?.back ?: t.from).takeIf { it in ways(t.cell) }
        return s.cell to s.from.takeIf { it in ways(s.cell) }
    }

    // ----- Where things are -----

    fun exitOf(c: Cell, from: Dir): Dir {
        val out = ways(c).filter { it != from }.sortedBy { if (it == from.back) 0 else 1 }
        return out.firstOrNull() ?: from.back
    }

    /** Where in the cell: a straight line, or a quarter round the corner between the two edges. */
    fun pos(c: Cell, from: Dir, to: Dir, t: Double): Pt {
        val cx = c.x + 0.5
        val cy = c.y + 0.5
        val ax = cx + from.dx * 0.5
        val ay = cy + from.dy * 0.5
        val bx = cx + to.dx * 0.5
        val by = cy + to.dy * 0.5
        if (from == to.back || from == to) return Pt(ax + (bx - ax) * t, ay + (by - ay) * t)
        val ox = cx + (from.dx + to.dx) * 0.5
        val oy = cy + (from.dy + to.dy) * 0.5
        val a0 = atan2(ay - oy, ax - ox)
        var d = atan2(by - oy, bx - ox) - a0
        while (d > Math.PI) d -= 2 * Math.PI
        while (d < -Math.PI) d += 2 * Math.PI
        val a = a0 + d * t
        return Pt(ox + 0.5 * cos(a), oy + 0.5 * sin(a))
    }

    private fun pathLen(from: Dir, to: Dir) = if (from == to.back || from == to) 1.0 else Math.PI / 4

    fun head(t: Train): Pt = pos(t.cell, t.from, t.to ?: t.from.back, t.t)

    /** The cells under a whole train. */
    fun occupied(t: Train): Set<Cell> {
        val out = HashSet<Cell>()
        out += t.cell
        var d = 0.0
        var last = head(t)
        for (s in t.trail) {
            d += hypot(s.at.x - last.x, s.at.y - last.y)
            if (d > t.length) break
            out += s.cell
            last = s.at
        }
        return out
    }

    /** A point [back] behind the front along where the train has been, and the way it runs there; null when it hasn't come that far. */
    fun along(t: Train, back: Double): Pair<Pt, Double>? {
        var d = 0.0
        var last = head(t)
        if (back <= 0.0) {
            val to = t.to ?: t.from.back
            val p = pos(t.cell, t.from, to, (t.t + 0.04).coerceAtMost(1.0))
            val q = pos(t.cell, t.from, to, (t.t - 0.04).coerceAtLeast(0.0))
            return last to atan2(p.y - q.y, p.x - q.x)
        }
        for (s in t.trail) {
            val l = hypot(s.at.x - last.x, s.at.y - last.y)
            if (d + l >= back && l > 1e-9) {
                val u = (back - d) / l
                val p = Pt(last.x + (s.at.x - last.x) * u, last.y + (s.at.y - last.y) * u)
                return p to atan2(last.y - s.at.y, last.x - s.at.x)
            }
            d += l
            last = s.at
        }
        return null
    }

    // ----- The clock -----

    fun step(dt0: Double, between: (() -> Unit)? = null): List<Ev> {
        var left = dt0
        while (left > 1e-9 && !over) {
            val dt = minOf(left, STEP)
            between?.invoke()
            if (running) tick(dt)
            left -= dt
        }
        return events.toList().also { events.clear() }
    }

    private fun tick(dt: Double) {
        time += dt
        while (nextOrder < stage.orders.size && stage.orders[nextOrder].at <= time) {
            val o = Order(nextOrder, stage.orders[nextOrder])
            orders += o
            nextOrder++
            events += Ev.NewOrder(o)
        }
        for (o in orders) if (o.open) {
            o.left -= dt
            if (o.left <= 0) lateOrder(o)
        }
        while (nextWash < stage.washouts.size && stage.washouts[nextWash].first <= time) {
            val before = damaged.size
            washOut(stage.washouts[nextWash].second)
            // Under a train it could not fall: the next moment will do.
            if (damaged.size == before && stage.washouts[nextWash].second in links && trains.any { stage.washouts[nextWash].second in occupied(it) }) break
            nextWash++
        }
        for (t in trains) autoLoad(t)
        for (t in trains) move(t, dt)
        collide()
        val g = if (total == 0) 0 else delivered * GROWTH / total
        if (g > grown) {
            grown = g
            events += Ev.Grew(g)
        }
        if (reputation <= 0) {
            over = true
            events += Ev.Note("信誉没了，小镇不再找你运货。")
            events += Ev.Lost
        } else if (nextOrder >= stage.orders.size && orders.none { it.open }) {
            over = true
            won = true
            events += Ev.Won
        }
    }

    private fun lateOrder(o: Order) {
        o.late = true
        if (!o.def.rush) {
            late++
            reputation--
        }
        o.taken?.let { t ->
            t.order = null
            if (t.dest?.id == o.def.from || t.dest?.id == o.def.to) {
                t.dest = null
                t.path.clear()
            }
        }
        o.taken = null
        events += Ev.Late(o)
    }

    private fun move(t: Train, dt: Double) {
        if (t.crashed > 0) { t.crashed -= dt; return }
        if (t.held) return
        if (t.retreat != null) {
            retreat(t, dt)
            return
        }
        if (t.wait > 0) {
            t.wait -= dt
            if (t.wait <= 0) work(t)
            return
        }
        val dest = t.dest ?: return
        if (t.to == null) {
            rest(t)
            if (t.to == null || !plan(t, true)) return
        }
        val to = t.to ?: return
        val ws = ways(t.cell)
        val next = t.cell + to
        // The line ahead is gone (a landslide, or it was taken up): stand at the middle of the cell, keep looking for a way.
        if (to !in ws && t.t >= 0.5 && t.cell != dest.at) {
            t.t = 0.5
            noRoute -= dt
            if (noRoute <= 0) {
                noRoute = 0.5
                plan(t, true)
            }
            return
        }
        val opp = if (t.t >= HOLD_AT && to in ws) comingAtUs(t) else null
        val mustStop = t.t >= HOLD_AT && to in ws && (occupiedAhead(t, next) || opp != null)
        if (mustStop) {
            t.blocked += dt
            // Face to face on a single line, or in one junction: the higher number backs out.
            val facing = opp ?: trains.firstOrNull { o -> blocks(o, t) && (o.cell == t.cell || o.cell == next) && o.to == to.back }
            if (facing != null && facing.blocked > 0 && t.id > facing.id && t.blocked > FACE_OFF) {
                startRetreat(t)
                return
            }
            if (t.blocked > GIVE_WAY) {
                t.blocked = 0.0
                events += Ev.Note("堵住了：这段单线上两车谁也过不去。修一段会让线，或者让一列车等一等。")
                turnBack(t)
                replan(t)
            }
            return
        }
        t.blocked = 0.0
        t.t += dt * SPEED / pathLen(t.from, to)
        if (t.t >= 0.5 && t.cell == dest.at) {
            t.t = 0.5
            t.wait = WORK
            record(t)
            return
        }
        if (t.t >= 0.5 && to !in ways(t.cell)) {
            // The end of the line: back the way it came, and find the way from there.
            t.t = 0.5
            record(t)
            if (!plan(t, true)) t.path.clear()
            return
        }
        if (t.t >= 1) {
            val rest = t.t - 1
            t.cell = next
            t.from = to.back
            t.to = if (t.path.isNotEmpty()) t.path.removeFirst() else exitOf(next, t.from)
            t.t = rest
            // The junction: another look at which side of a loop is free.
            if (isJunction(next) && t.path.isNotEmpty()) replanForward(t)
        }
        record(t)
    }

    private fun replanForward(t: Train) {
        val goal = t.dest?.at ?: return
        val to = t.to ?: return
        search(t.cell, to, goal, t)?.let {
            t.path.clear()
            t.path.addAll(it)
        }
    }

    /** A free train standing at a stop is no obstacle: it is taken to have drawn aside on a platform road. */
    private fun occupiedAhead(t: Train, next: Cell) = trains.any { o -> o !== t && !o.parked && o.retreat == null && next in footprint(o) }

    /**
     * What a train blocks. Standing at a stop it blocks only the stop: a stop's spur may be a single
     * cell long, and a tail lying back over the main line would shut the whole network.
     */
    private fun footprint(o: Train): Set<Cell> = if (stopAt(o.cell) != null && o.t <= HOLD_AT + 0.05) setOf(o.cell) else occupied(o)

    private fun controlPoint(c: Cell) = stopAt(c) != null || isJunction(c)

    /**
     * The signal: the stretch ahead, up to the next stop or junction beyond, must be clear of anyone
     * coming the other way. A train waiting at it stands short of the junction, leaving that free for
     * the other to come through and take the other side of a loop. Returns who it is waiting for.
     */
    private fun comingAtUs(t: Train): Train? {
        val first = t.to ?: return null
        var c = t.cell
        var d = first
        val it = t.path.iterator()
        // The next stop or junction just ahead doesn't end the look: what counts is the stretch past it.
        var passed = controlPoint(c)
        var k = 0
        while (k < 40) {
            k++
            if (d !in ways(c)) return null
            c += d
            if (c !in links) return null
            // Inside the stretch. Someone standing at its far end has not come in yet: see below.
            if (!controlPoint(c)) for (o in trains) if (blocks(o, t) && o.cell == c && o.to == d.back) return o
            if (controlPoint(c)) {
                if (passed) {
                    // At the far end. Whoever already stands on it goes first: the other could not get
                    // past until it has left, and it can only leave this way. If both stand on theirs,
                    // or neither does yet, the lower number goes first.
                    val here = controlPoint(t.cell)
                    for (o in trains) if (blocks(o, t)) {
                        val oTo = o.to ?: continue
                        val standing = o.cell == c && oTo == d.back
                        val coming = o.cell + oTo == c && o.path.firstOrNull() == d.back
                        if (standing && (!here || o.id < t.id)) return o
                        if (coming && !here && o.id < t.id) return o
                    }
                    return null
                }
                passed = true
            }
            d = if (it.hasNext()) it.next() else exitOf(c, d.back)
        }
        return null
    }

    /** Who counts on the line: a train shaken in a crash or held by the player is still in the way. */
    private fun blocks(o: Train, t: Train) = o !== t && !o.parked && o.retreat == null

    /** Two already face to face on a single line: one backs right out of the stretch. */
    private fun startRetreat(t: Train) {
        val cells = run(t.cell).ifEmpty { setOf(t.cell) }
        turnBack(t)
        t.retreat = cells
        t.blocked = 0.0
    }

    /** Backing away, out of the stretch it has to leave; then it looks for the way again, which may be back the same way once clear. */
    private fun retreat(t: Train, dt: Double) {
        val to = t.to ?: return
        val clear = occupied(t).none { it in (t.retreat ?: emptySet()) }
        val end = t.t >= 0.5 && to !in ways(t.cell)
        if (clear || end) {
            t.retreat = null
            t.t = t.t.coerceAtMost(1.0)
            if (!plan(t, true)) t.path.clear()
            return
        }
        t.t += dt * SPEED / pathLen(t.from, to)
        if (t.t >= 1) {
            val rest = t.t - 1
            val next = t.cell + to
            t.cell = next
            t.from = to.back
            t.to = exitOf(next, t.from)
            t.t = rest
        }
        record(t)
    }

    private fun record(t: Train) {
        val to = t.to ?: return
        t.trail.addFirst(Sample(t.cell, t.from, to, t.t, head(t)))
        while (t.trail.size > 240) t.trail.removeLast()
    }

    /** The other end of the train becomes the front: nothing jumps, the locomotive now pushes. */
    private fun turnBack(t: Train) {
        var d = 0.0
        var last = head(t)
        val kept = ArrayList<Sample>()
        for (s in t.trail) {
            d += hypot(s.at.x - last.x, s.at.y - last.y)
            if (d > t.length) break
            kept += s
            last = s.at
        }
        val to = t.to ?: t.from.back
        val tail = kept.lastOrNull() ?: Sample(t.cell, t.from, to, t.t, head(t))
        t.cell = tail.cell
        t.from = tail.to
        t.to = tail.from
        t.t = 1 - tail.t
        t.trail.clear()
        for (s in kept.reversed().drop(1)) t.trail.addLast(Sample(s.cell, s.to, s.from, 1 - s.t, s.at))
        t.pushing = !t.pushing
        t.path.clear()
    }

    /** Done at the stop it was sent to: loads and goes on, or unloads and stands free. */
    private fun work(t: Train) {
        val s = t.dest ?: return
        val o = t.order
        if (o != null && o.open) {
            if (!o.loaded && o.def.from == s.id) {
                o.loaded = true
                events += Ev.Loaded(s.id)
                t.dest = null
                t.path.clear()
                if (t.goWhenLoaded) go(t) else events += Ev.Note("${s.title}装好了，要去${stop(o.def.to).title}。捏一下车就发车。")
                return
            }
            if (o.loaded && o.def.to == s.id) {
                o.done = true
                delivered++
                money += o.def.pay
                // Delivered with half its time still in hand: the customer tips.
                val tip = if (o.left > o.def.window / 2) TIP else 0
                if (tip > 0) {
                    money += tip
                    tips += tip
                    events += Ev.Tip(tip)
                }
                t.order = null
                events += Ev.Delivered(s.id, o.def.pay)
            }
        } else if (o == null) {
            // It came with nothing in mind: perhaps something it can carry has come up here since.
            waitingAt(s).filter { it.def.cargo == t.def.cargo }.minByOrNull { it.left }?.let {
                it.taken = t
                t.order = it
                t.wait = WORK
                return
            }
        }
        t.dest = null
        t.path.clear()
    }

    private fun nextCell(t: Train) = t.to?.let { t.cell + it }

    /** Fronts coming at each other, this cell or the next. */
    private fun headOn(a: Train, b: Train) = nextCell(a) == b.cell && nextCell(b) == a.cell

    /** Fronts meeting: both shaken, one backs away. */
    private fun collide() {
        for (i in trains.indices) for (j in i + 1 until trains.size) {
            val a = trains[i]
            val b = trains[j]
            if (a.crashed > 0 || b.crashed > 0 || a.parked || b.parked || a.retreat != null || b.retreat != null) continue
            val pa = head(a)
            val pb = head(b)
            val close = hypot(pa.x - pb.x, pa.y - pb.y) < MEET
            if (!close || !(a.cell == b.cell || headOn(a, b))) continue
            a.crashed = CRASH_SECONDS
            b.crashed = CRASH_SECONDS
            a.held = false
            b.held = false
            // The one nearer its own way out backs out of the stretch, or they would only meet again.
            startRetreat(a)
            crashes++
            money = (money - CRASH_COST).coerceAtLeast(0)
            events += Ev.Crash(pa)
            events += Ev.Note("撞车了！赔 $CRASH_COST，还耽误 ${CRASH_SECONDS.toInt()} 秒。")
        }
    }

    /** Stars: won; also with nothing late; also with no crash. */
    fun stars(): Int {
        if (!won) return 0
        return 1 + (if (late == 0) 1 else 0) + (if (crashes == 0) 1 else 0)
    }

    companion object {
        const val W = 12
        const val H = 8
        const val SPEED = 1.5
        const val WORK = 1.2
        const val STEP = 0.05
        const val CRASH_SECONDS = 5.0
        const val CRASH_COST = 3
        const val GROWTH = 8
        const val PITCH = 0.88
        const val HOLD_AT = 0.72
        const val GIVE_WAY = 15.0
        const val FACE_OFF = 1.5
        const val MEET = 0.3
        const val REPUTATION = 3
        const val TIP = 2
        const val REPAIR = 2
    }
}

/** A dispatcher who knows the plan: lays it as the money comes, buys trains, sends the nearest free train to the most urgent order. */
class Dispatcher(private val p: Play) {
    private var laid = 0
    private var bought = 0

    fun update() {
        val plan = p.stage.plan
        while (laid < plan.size) {
            val (a, b) = plan[laid]
            if (p.priceOf(a, b) > p.money || !p.lay(a, b)) break
            laid++
        }
        p.damaged.keys.toList().forEach { p.repair(it) }
        if (bought < p.stage.buys && laid >= plan.size && p.buy()) bought++
        if (!p.running) {
            // Opens once the first line is down, or when it cannot lay more until it earns.
            p.open()
        }
        p.trains.filter { it.ready }.forEach { p.go(it) }
        for (o in p.orders.filter { it.open && it.taken == null }.sortedBy { it.left }) {
            val s = p.stop(o.def.from)
            val t = p.trains.filter { it.idle && it.def.cargo == o.def.cargo }.minByOrNull { d ->
                kotlin.math.abs(d.cell.x - s.at.x) + kotlin.math.abs(d.cell.y - s.at.y)
            } ?: continue
            p.send(t, s)
        }
    }
}
