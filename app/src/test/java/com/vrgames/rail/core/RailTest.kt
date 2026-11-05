package com.vrgames.rail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class RailTest {
    private fun solve(s: Stage): Play {
        val p = Play(s)
        val bot = Dispatcher(p)
        var t = 0.0
        while (!p.over && t < 900) { p.step(Play.STEP) { bot.update() }; t += Play.STEP }
        return p
    }

    private fun laid(id: String): Play {
        val p = Play(Stages.byId(id))
        for ((a, b) in p.stage.plan) p.lay(a, b)
        return p
    }

    @Test
    fun everyLevelCanBeDoneWithThreeStars() {
        val bad = ArrayList<String>()
        for (s in Stages.ALL) {
            val p = solve(s)
            println("${s.id}: won=${p.won} stars=${p.stars()} time=${p.time.toInt()} done=${p.delivered}/${p.total} late=${p.late} crashes=${p.crashes} money=${p.money} start=${s.money} trains=${p.trains.size} rep=${p.reputation}")
            if (p.stars() < 3) bad += "${s.id} won=${p.won} stars=${p.stars()} done=${p.delivered}/${p.total} late=${p.late} crashes=${p.crashes} money=${p.money}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    // ----- Each level must force what it teaches. -----

    /** Serves only one of the two factories: it must not pass (the user's report on the old level 8). */
    @Test
    fun oneFactoryIsNotEnough() {
        val p = Play(Stages.byId("a4"))
        p.lay(Cell(1, 4), Cell(5, 4))
        p.lay(Cell(5, 4), Cell(10, 1))
        p.open()
        var t = 0.0
        while (!p.over && t < 400) {
            p.step(0.05) {
                for (o in p.orders.filter { it.open && it.taken == null && it.def.to == "north" }) p.trains.firstOrNull { it.idle }?.let { tr -> p.send(tr, p.stop("mine")) }
            }
            t += 0.05
        }
        assertFalse("late ${p.late} done ${p.delivered}", p.won && p.late == 0)
    }

    /** A single line down the valley with no loop: two trains running against each other must not get through clean. */
    @Test
    fun aValleyWithoutALoopDoesNotWork() {
        val s = Stages.byId("b4")
        // The same level, with the bot's plan stopping short of the loop.
        val noLoop = Stage(s.id, s.act, s.title, s.ask, s.stops, s.ground, s.trains, s.orders, s.money, plan = s.plan.take(3))
        val p = Play(noLoop)
        val bot = Dispatcher(p)
        var t = 0.0
        while (!p.over && t < 400) { p.step(0.05) { bot.update() }; t += 0.05 }
        println("valley without a loop: won=${p.won} late=${p.late} crashes=${p.crashes} done=${p.delivered}/${p.total}")
        assertTrue("won ${p.won} late ${p.late} crashes ${p.crashes}", !p.won || p.late > 0 || p.crashes > 0)
    }

    /** The old level 9 was two parallel lines. Here three separate lines must be more than the money can buy. */
    @Test
    fun theMountainPassMakesCargoShare() {
        val s = Stages.byId("c3")
        val p = Play(s)
        assertEquals("gaps in the wall", 3, (0 until Play.H).count { p.ground(Cell(5, it)) != Ground.ROCK })
        assertEquals("three cargoes", 3, s.trains.map { it.cargo }.toSet().size)
        // Each cargo on its own line, by the cheapest way there is.
        val lines = listOf(Cell(1, 1) to Cell(10, 1), Cell(1, 4) to Cell(10, 4), Cell(1, 7) to Cell(10, 7))
        val q = Play(Stage(s.id, s.act, s.title, s.ask, s.stops, s.ground, s.trains, s.orders, 999))
        for ((a, b) in lines) assertTrue(q.lay(a, b))
        val cost = 999 - q.money
        println("three separate lines would cost $cost; the level gives ${s.money}")
        assertTrue("$cost against ${s.money}", cost > s.money)
    }

    @Test
    fun aLateTunnelMissesTheHurriedOrders() {
        // The long way round only, never the tunnel.
        val s = Stages.byId("b2")
        val p = Play(s)
        for ((a, b) in s.plan.dropLast(1)) p.lay(a, b)
        var t = 0.0
        p.open()
        while (!p.over && t < 400) {
            p.step(0.05) { for (o in p.orders.filter { it.open && it.taken == null }) p.trains.firstOrNull { it.idle }?.let { tr -> p.send(tr, p.stop(o.def.from)) } }
            t += 0.05
        }
        assertTrue("late ${p.late}", p.late > 0)
    }

    // ----- Laying. -----

    @Test
    fun twoPinchesLayALine() {
        val p = Play(Stages.byId("a1"))
        p.tap(Cell(1, 3))
        assertEquals(Cell(1, 3), p.anchor)
        p.tap(Cell(10, 3))
        assertTrue((1..10).all { p.hasTrack(Cell(it, 3)) })
        assertEquals(p.stage.money - 8, p.money)
    }

    @Test
    fun parallelLinesSideBySideDoNotJoin() {
        val p = Play(Stages.byId("a1"))
        p.lay(Cell(1, 3), Cell(4, 3))
        p.lay(Cell(1, 4), Cell(4, 4))
        assertFalse(p.isJunction(Cell(3, 3)))
        assertEquals(setOf(Dir.E, Dir.W), p.ways(Cell(3, 3)).toSet())
    }

    @Test
    fun aLineBendsAsLittleAsItCan() {
        val p = Play(Stages.byId("a2"))
        val line = p.route(Cell(2, 5), Cell(9, 2))!!
        var bends = 0
        for (i in 1 until line.size - 1) {
            val d1 = Dir.entries.first { line[i - 1] + it == line[i] }
            val d2 = Dir.entries.first { line[i] + it == line[i + 1] }
            if (d1 != d2) bends++
        }
        assertEquals("no staircase: $line", 1, bends)
    }

    @Test
    fun tooDearIsRefusedWithAReason() {
        val p = Play(Stages.byId("b1"))
        assertFalse(p.lay(Cell(0, 0), Cell(11, 0)))
        assertEquals(p.stage.money, p.money)
        assertTrue(p.step(0.01).any { it is Ev.Refused })
    }

    /** The user could not get 拆除 to work: one pinch must take up the whole piece and pay it back. */
    @Test
    fun aPinchInEraseModeTakesUpThePieceOfLine() {
        val p = Play(Stages.byId("a1"))
        p.lay(Cell(1, 3), Cell(10, 3))
        val before = p.money
        p.setErasing(true)
        p.tap(Cell(5, 3))
        assertTrue("the whole run between the stops", (2..9).none { p.hasTrack(Cell(it, 3)) })
        assertEquals(before + 8, p.money)
        assertTrue("the stops stay", p.hasTrack(Cell(1, 3)) && p.hasTrack(Cell(10, 3)))
        assertTrue(p.step(0.01).any { it is Ev.Removed })
    }

    @Test
    fun eraseStopsAtAJunction() {
        val p = laid("a4")
        val j = p.links.keys.first { p.isJunction(it) }
        p.setErasing(true)
        p.tap(Cell(3, 4))
        assertTrue("junction stays", p.hasTrack(j))
        assertFalse(p.hasTrack(Cell(3, 4)))
    }

    @Test
    fun undoTakesBackAndPaysBack() {
        val p = Play(Stages.byId("a1"))
        p.lay(Cell(1, 3), Cell(5, 3))
        p.lay(Cell(5, 3), Cell(10, 3))
        val m = p.money
        p.undo()
        assertFalse(p.hasTrack(Cell(8, 3)))
        assertTrue(p.hasTrack(Cell(4, 3)))
        assertTrue(p.money > m)
    }

    // ----- Sending. -----

    @Test
    fun aPinchOnAStopSendsAFreeTrainAndItCarriesTheOrderThrough() {
        val p = laid("a1")
        p.open()
        repeat(60) { p.step(0.05) }
        assertEquals(1, p.orders.size)
        p.tap(Cell(1, 3))
        var t = 0.0
        while (p.delivered == 0 && t < 40) { p.step(0.05); t += 0.05 }
        assertEquals("delivered by itself once sent, in $t", 1, p.delivered)
        assertEquals("pay and the tip for being quick", p.stage.money - 8 + 5 + Play.TIP, p.money)
    }

    /** The one rule: a free train at a station loads by itself, and waits for a pinch to set off. */
    @Test
    fun aTrainAtAStationLoadsByItselfAndWaitsForAPinch() {
        val p = laid("a2")
        p.open()
        var t = 0.0
        while (!p.trains[0].ready && t < 10) { p.step(0.05); t += 0.05 }
        assertTrue("loaded by itself in $t", p.trains[0].ready)
        repeat(100) { p.step(0.05) }
        assertEquals("it does not leave by itself", 0, p.delivered)
        assertTrue(p.trains[0].ready)
        p.pinchTrain(0)
        t = 0.0
        while (p.delivered == 0 && t < 40) { p.step(0.05); t += 0.05 }
        assertEquals("delivered after one pinch, in $t", 1, p.delivered)
    }

    @Test
    fun aPinchOnTheDestinationOrItsOwnStationAlsoSetsItOff() {
        for (pinch in listOf("east", "west")) {
            val p = laid("a2")
            p.open()
            while (!p.trains[0].ready) p.step(0.05)
            p.tap(p.stops.first { it.id == pinch }.at)
            var t = 0.0
            while (p.delivered == 0 && t < 40) { p.step(0.05); t += 0.05 }
            assertEquals("pinching $pinch", 1, p.delivered)
        }
    }

    @Test
    fun aPinchWhileLoadingSetsItOffWhenDone() {
        val p = laid("a2")
        p.open()
        while (!p.trains[0].loading) p.step(0.05)
        p.pinchTrain(0)
        var t = 0.0
        while (p.delivered == 0 && t < 40) { p.step(0.05); t += 0.05 }
        assertEquals(1, p.delivered)
    }

    @Test
    fun whatWaitsWhereATrainJustDeliveredLoadsToo() {
        val p = laid("a2")
        p.open()
        while (!p.trains[0].ready) p.step(0.05)
        p.pinchTrain(0)
        var t = 0.0
        while (p.delivered == 0 && t < 40) { p.step(0.05); t += 0.05 }
        t = 0.0
        while (!p.trains[0].ready && t < 30) { p.step(0.05); t += 0.05 }
        assertTrue("the next passengers boarded at the east station", p.trains[0].ready)
        assertEquals("west", p.trains[0].order?.def?.to)
    }

    @Test
    fun aTrainFetchedToAStationLoadsThereAndAlsoWaits() {
        val p = laid("a2")
        p.open()
        val west = p.stops.first { it.id == "west" }
        val east = p.stops.first { it.id == "east" }
        p.pick(0)
        p.tap(east.at)
        var t = 0.0
        while (p.trains[0].cell != east.at && t < 40) { p.step(0.05); t += 0.05 }
        t = 0.0
        while (!p.trains[0].ready && t < 30) { p.step(0.05); t += 0.05 }
        assertTrue(p.trains[0].ready)
        assertEquals("the same rule at both ends", west.id, p.trains[0].order?.def?.to)
        repeat(60) { p.step(0.05) }
        assertTrue("still waiting for the pinch", p.trains[0].ready)
    }

    @Test
    fun aTrainSentEmptySaysWhereThePassengersAre() {
        val p = laid("a2")
        p.open()
        val west = p.stops.first { it.id == "west" }
        val east = p.stops.first { it.id == "east" }
        p.hold(0) // Held, it does not take what comes.
        while (p.waitingAt(west).isEmpty()) p.step(0.05)
        p.send(p.trains[0], east)
        val events = p.step(0.05)
        assertTrue("$events", events.any { it is Ev.Note && it.text.contains("西站") })
    }

    @Test
    fun anUnservedOrderCostsReputationAndThenTheLevel() {
        val p = laid("a1")
        p.open()
        var t = 0.0
        while (!p.over && t < 300) { p.step(0.05); t += 0.05 }
        assertTrue(p.over)
        assertFalse(p.won)
        assertEquals(Play.REPUTATION, p.late)
    }

    @Test
    fun theClockOnlyRunsOnceOpen() {
        val p = laid("a1")
        repeat(200) { p.step(0.05) }
        assertEquals(0.0, p.time, 1e-9)
        assertTrue(p.orders.isEmpty())
        p.open()
        repeat(100) { p.step(0.05) }
        assertEquals(5.0, p.time, 1e-6)
    }

    @Test
    fun theCountersAddUp() {
        for (id in listOf("a1", "a2", "c1")) {
            val p = solve(Stages.byId(id))
            assertEquals(id, p.total, p.delivered + p.late)
            assertEquals(id, p.delivered, p.orders.count { it.done })
        }
    }

    @Test
    fun noRouteSaysSoAndTheTrainStaysFree() {
        val p = Play(Stages.byId("a1"))
        p.open()
        p.send(p.trains[0], p.stop("factory"))
        assertTrue(p.step(0.01).any { it is Ev.Refused })
        assertTrue(p.trains[0].idle)
    }

    @Test
    fun aBoughtTrainComesToItsStop() {
        val p = Play(Stages.byId("b3"))
        assertFalse("not enough yet? start money ${p.money}", p.trains.size > 1)
        assertTrue(p.buy())
        assertEquals(2, p.trains.size)
        assertEquals(p.stop("mine2").at, p.trains[1].cell)
    }

    @Test
    fun aTrainTurnsBackWithoutJumping() {
        val p = laid("a2")
        p.open()
        val t = p.trains[0]
        val bot = Dispatcher(p)
        var last = p.along(t, 0.0)!!.first
        var flips = 0
        var was = t.pushing
        var clock = 0.0
        while (!p.over && clock < 200) {
            p.step(0.05) { bot.update() }
            clock += 0.05
            val h = p.along(t, 0.0)!!.first
            if (t.pushing != was) {
                flips++
                was = t.pushing
            } else assertTrue("smooth: $last to $h", hypot(h.x - last.x, h.y - last.y) < 0.2)
            last = h
        }
        assertTrue("it shuttled: $flips", flips >= 2)
    }

    @Test
    fun aHeldTrainStaysAndGoesOnAgain() {
        val p = laid("a1")
        p.open()
        repeat(60) { p.step(0.05) }
        p.tap(Cell(1, 3))
        repeat(60) { p.step(0.05) }
        p.hold(0)
        val at = p.trains[0].cell to p.trains[0].t
        repeat(40) { p.step(0.05) }
        assertEquals(at, p.trains[0].cell to p.trains[0].t)
        p.hold(0)
        repeat(40) { p.step(0.05) }
        assertTrue(p.trains[0].cell != at.first || p.trains[0].t != at.second)
    }

    @Test
    fun theTownGrowsWithDeliveries() {
        assertEquals(Play.GROWTH, solve(Stages.byId("a1")).grown)
    }

    @Test
    fun aPinchAnywhereOnAStationMeansItsDoor() {
        for (s in Stages.ALL) for (stop in s.stops) {
            val p = Play(s)
            p.tap(stop.building)
            assertEquals("${s.id} ${stop.id} building", stop.at, p.anchor)
            p.tap(stop.at)
            assertEquals("${s.id} ${stop.id} cancels", null, p.anchor)
        }
    }

    @Test
    fun theBuildingsOfTwoStationsLayTheLineBetweenTheirDoors() {
        val s = Stages.ALL.first { it.stops.size >= 2 }
        val p = Play(s)
        val a = s.stops[0]
        val b = s.stops[1]
        p.tap(a.building)
        p.tap(b.building)
        assertTrue("from the door", a.at in p.links)
        assertTrue("to the door", b.at in p.links)
    }

    @Test
    fun theWidthBesideAStationCountsButLaidTrackDoesNot() {
        val s = Stages.ALL.first { it.stops.size >= 2 }
        val p = Play(s)
        val stop = s.stops[0]
        val beside = if (stop.side == Dir.N || stop.side == Dir.S) Cell(stop.building.x + 1, stop.building.y) else Cell(stop.building.x, stop.building.y + 1)
        if (p.inside(beside) && p.ground(beside) != Ground.ROCK && !p.isBuilding(beside)) assertEquals(stop.at, p.snapToStop(beside))
        val far = Cell(Play.W / 2, Play.H / 2)
        assertEquals("open ground stays itself", far, p.snapToStop(far))
    }

    @Test
    fun trainsCannotBePinchedWhileLayingTrack() {
        val s = Stages.ALL.first()
        val p = Play(s)
        assertFalse("before opening", p.trainsPickable)
        p.open()
        assertTrue("dispatching", p.trainsPickable)
        p.tap(Cell(Play.W / 2, Play.H / 2))
        if (p.anchor != null) assertFalse("a line under way", p.trainsPickable)
    }

    /** From the seat: every cell spans enough of the view to aim at, every door can be seen past the buildings. */
    @Test
    fun everyCellAndDoorCanBeReachedFromTheSeat() {
        val eye = Vec3(0.0, 1.2, 0.0)
        fun deg(a: Vec3, b: Vec3, o: Vec3): Double {
            val u = (a - o).normalized()
            val v = (b - o).normalized()
            return Math.toDegrees(kotlin.math.acos((u.x * v.x + u.y * v.y + u.z * v.z).coerceIn(-1.0, 1.0)))
        }
        for (y in 0 until Play.H) for (x in 0 until Play.W) {
            val deep = deg(Board.world(x + 0.5, y.toDouble()), Board.world(x + 0.5, y + 1.0), eye)
            val wide = deg(Board.world(x.toDouble(), y + 0.5), Board.world(x + 1.0, y + 0.5), eye)
            assertTrue("cell $x,$y spans $deep deep", deep >= 2.0)
            assertTrue("cell $x,$y spans $wide wide", wide >= 3.0)
        }
        val tall = 0.06
        val ta = Math.toRadians(Board.TILT)
        for (s in Stages.ALL) for (stop in s.stops) for (o in listOf(eye, Vec3(-0.22, 0.85, -0.2), Vec3(0.22, 0.85, -0.2))) {
            val p = Play(s)
            val target = Board.world(stop.at.x + 0.5, stop.at.y + 0.5, 0.008)
            val d = target - o
            var u = 0.0
            while (u < 0.98) {
                val q = Vec3(o.x + d.x * u, o.y + d.y * u, o.z + d.z * u)
                val hit = Board.hit(q, Vec3(0.0, -kotlin.math.cos(ta), -kotlin.math.sin(ta)))
                if (hit != null) {
                    val c = Cell(kotlin.math.floor(hit.first).toInt(), kotlin.math.floor(hit.second).toInt())
                    val up = (q.y - Board.Y) * kotlin.math.cos(ta) + (q.z + Board.AHEAD) * kotlin.math.sin(ta)
                    assertFalse("${s.id} ${stop.id} from $o is behind the building at $c", p.isBuilding(c) && up in 0.0..tall)
                }
                u += 0.01
            }
        }
    }

    // ----- What makes it more than laying a line. -----

    private fun custom(orders: List<OrderDef>, washouts: List<Pair<Double, Cell>> = emptyList(), money: Int = 30): Play {
        val a = Stages.byId("a1")
        return Play(Stage("x", 0, "", "", a.stops, a.ground, a.trains, orders, money, washouts = washouts))
    }

    @Test
    fun aMissedRushJobCostsNoReputationAndTheLevelCanStillBeWon() {
        val p = custom(listOf(OrderDef(1.0, Cargo.COAL, "mine", "factory", 3.0, 20, rush = true)))
        p.lay(Cell(1, 3), Cell(10, 3))
        p.open()
        repeat(200) { p.step(0.05) }
        assertEquals(Play.REPUTATION, p.reputation)
        assertTrue(p.won)
        assertEquals("three stars: it was not a late order", 3, p.stars())
    }

    @Test
    fun aQuickDeliveryIsTipped() {
        val p = custom(listOf(OrderDef(1.0, Cargo.COAL, "mine", "factory", 200.0, 5)))
        p.lay(Cell(1, 3), Cell(10, 3))
        val m = p.money
        p.open()
        repeat(40) { p.step(0.05) }
        p.tap(Cell(1, 3))
        repeat(400) { p.step(0.05) }
        assertEquals(m + 5 + Play.TIP, p.money)
    }

    @Test
    fun aLandslideStopsTheLineUntilItIsDugOut() {
        val p = custom(listOf(OrderDef(1.0, Cargo.COAL, "mine", "factory", 400.0, 5)), washouts = listOf(4.0 to Cell(8, 3)))
        p.lay(Cell(1, 3), Cell(10, 3))
        p.open()
        repeat(40) { p.step(0.05) }
        p.tap(Cell(1, 3))
        repeat(60) { p.step(0.05) }
        assertTrue("buried", Cell(8, 3) in p.damaged)
        assertFalse(p.hasTrack(Cell(8, 3)))
        repeat(600) { p.step(0.05) }
        assertEquals("it can't get there", 0, p.delivered)
        assertTrue("and it waits at the gap", p.trains[0].cell.x in 2..7)
        val m = p.money
        p.tap(Cell(8, 3))
        assertEquals("costs double", m - Play.REPAIR, p.money)
        assertTrue(p.hasTrack(Cell(8, 3)))
        repeat(400) { p.step(0.05) }
        assertEquals("the train picks up where it left off", 1, p.delivered)
    }

    @Test
    fun aTrainCannotBeSentWhereThereIsNoLineYetAndItIsSaid() {
        val p = custom(listOf(OrderDef(1.0, Cargo.COAL, "mine", "factory", 400.0, 5)), washouts = listOf(0.5 to Cell(6, 3)))
        p.lay(Cell(1, 3), Cell(10, 3))
        p.open()
        repeat(40) { p.step(0.05) }
        p.tap(Cell(1, 3))
        repeat(200) { p.step(0.05) }
        // It loads at the mine it stands at and cannot leave: it says so, and nothing is lost.
        assertEquals(0, p.delivered)
        assertTrue(p.trains[0].ready)
        p.tap(Cell(6, 3))
        repeat(100) { p.step(0.05) }
        p.pinchTrain(0)
        repeat(400) { p.step(0.05) }
        assertEquals(1, p.delivered)
    }
}
