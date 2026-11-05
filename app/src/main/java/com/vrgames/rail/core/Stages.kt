package com.vrgames.rail.core

object Stages {
    val ACTS = listOf("第一章 · 开张", "第二章 · 过河穿山", "第三章 · 铁路网")

    private fun c(x: Int, y: Int) = Cell(x, y)
    private fun river(x: Int, from: Int = 0, to: Int = Play.H - 1) = (from..to).associate { Cell(x, it) to Ground.RIVER }
    private fun hills(vararg p: Pair<Int, Int>) = p.associate { Cell(it.first, it.second) to Ground.HILL }
    private fun rocks(vararg p: Pair<Int, Int>) = p.associate { Cell(it.first, it.second) to Ground.ROCK }
    /** A wall of rock down column [x], but for the gaps. */
    private fun wall(x: Int, vararg gaps: Int) = (0 until Play.H).filter { it !in gaps }.associate { Cell(x, it) to Ground.ROCK }
    private fun o(at: Double, cargo: Cargo, from: String, to: String, window: Double, pay: Int) = OrderDef(at, cargo, from, to, window, pay)
    /** A rush job: pays double, and no reputation is lost if it goes unserved. */
    private fun rush(at: Double, cargo: Cargo, from: String, to: String, window: Double, pay: Int) = OrderDef(at, cargo, from, to, window, pay, rush = true)

    val ALL = listOf(
        Stage(
            "a1", 0, "第一单生意", "铺一条线把矿场和工厂连起来：捏矿场门口，再捏工厂门口。然后开张。订单来了，停在矿场的车会自动装煤。装好后捏一下车，它就开去工厂。",
            listOf(Stop("mine", c(1, 3), Dir.N, "mine", "矿场"), Stop("factory", c(10, 3), Dir.N, "factory", "工厂")),
            emptyMap(), listOf(TrainDef("mine", Cargo.COAL, 2)),
            listOf(o(2.0, Cargo.COAL, "mine", "factory", 30.0, 5), o(22.0, Cargo.COAL, "mine", "factory", 30.0, 5), o(42.0, Cargo.COAL, "mine", "factory", 30.0, 5)),
            10, plan = listOf(c(1, 3) to c(10, 3)),
        ),
        Stage(
            "a2", 0, "两头都有客", "这次两个车站都有客人要走。车在客人那站：客人自动上车，捏一下车就发车。客人在另一头：捏那个站，车开过去接，上好客再捏车发车。",
            listOf(Stop("west", c(2, 5), Dir.N, "station", "西站"), Stop("east", c(9, 2), Dir.N, "station", "东站")),
            emptyMap(), listOf(TrainDef("west", Cargo.PEOPLE, 2)),
            listOf(
                o(2.0, Cargo.PEOPLE, "west", "east", 22.0, 4), o(16.0, Cargo.PEOPLE, "east", "west", 20.0, 4), o(30.0, Cargo.PEOPLE, "east", "west", 30.0, 4),
                o(44.0, Cargo.PEOPLE, "west", "east", 26.0, 4), o(60.0, Cargo.PEOPLE, "east", "west", 22.0, 4),
            ),
            11, plan = listOf(c(2, 5) to c(9, 2)),
        ),
        Stage(
            "a3", 0, "绕开石头", "中间有石头，铺轨时会自动绕开。钱只够一条线。",
            listOf(Stop("farm", c(1, 2), Dir.N, "farm", "农场"), Stop("town", c(10, 5), Dir.N, "house2", "商店")),
            rocks(5 to 1, 5 to 2, 5 to 3, 5 to 4, 6 to 3), listOf(TrainDef("farm", Cargo.GOODS, 2)),
            listOf(o(2.0, Cargo.GOODS, "farm", "town", 30.0, 5), o(24.0, Cargo.GOODS, "farm", "town", 30.0, 5), o(46.0, Cargo.GOODS, "farm", "town", 30.0, 5)),
            12, plan = listOf(c(1, 2) to c(10, 5)),
        ),
        Stage(
            "a4", 0, "两家工厂", "北厂和南厂各有各的订单，都要按时送到。线路要分叉。列车会自己转道岔，你只管派车。",
            listOf(Stop("mine", c(1, 4), Dir.N, "mine", "矿场"), Stop("north", c(10, 1), Dir.N, "factory", "北厂"), Stop("south", c(10, 6), Dir.N, "factory", "南厂")),
            emptyMap(), listOf(TrainDef("mine", Cargo.COAL, 2)),
            listOf(
                o(2.0, Cargo.COAL, "mine", "north", 30.0, 5), o(24.0, Cargo.COAL, "mine", "south", 30.0, 5), o(46.0, Cargo.COAL, "mine", "north", 30.0, 5),
                o(68.0, Cargo.COAL, "mine", "south", 30.0, 5),
            ),
            17, plan = listOf(c(1, 4) to c(5, 4), c(5, 4) to c(10, 1), c(5, 4) to c(10, 6)),
        ),
        Stage(
            "b1", 1, "架桥", "一条河挡在中间，架桥一格要 3。过了河才有钱挣。最后会来一张急单：钱多，时间短，赶不上也不扣信誉。",
            listOf(Stop("mine", c(2, 4), Dir.N, "mine", "矿场"), Stop("factory", c(9, 4), Dir.N, "factory", "工厂")),
            river(5), listOf(TrainDef("mine", Cargo.COAL, 3)),
            listOf(o(2.0, Cargo.COAL, "mine", "factory", 24.0, 6), o(20.0, Cargo.COAL, "mine", "factory", 24.0, 6), o(38.0, Cargo.COAL, "mine", "factory", 24.0, 6), o(56.0, Cargo.COAL, "mine", "factory", 24.0, 6), rush(70.0, Cargo.COAL, "mine", "factory", 16.0, 14)),
            9, plan = listOf(c(2, 4) to c(9, 4)),
        ),
        Stage(
            "b2", 1, "绕路还是穿山", "一开始钱不够打隧道，只能绕远路。后面的单子越来越急，赚了钱就要打通隧道，不然赶不上。",
            listOf(Stop("oil", c(1, 3), Dir.N, "tower", "油站"), Stop("factory", c(10, 3), Dir.N, "factory", "工厂")),
            hills(5 to 0, 5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5, 5 to 6, 6 to 0, 6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5, 6 to 6), listOf(TrainDef("oil", Cargo.OIL, 2)),
            listOf(
                o(2.0, Cargo.OIL, "oil", "factory", 40.0, 8), o(30.0, Cargo.OIL, "oil", "factory", 40.0, 8), o(60.0, Cargo.OIL, "oil", "factory", 26.0, 8),
                o(84.0, Cargo.OIL, "oil", "factory", 18.0, 8), o(104.0, Cargo.OIL, "oil", "factory", 18.0, 8), o(124.0, Cargo.OIL, "oil", "factory", 18.0, 8),
            ),
            18, plan = listOf(c(1, 3) to c(1, 7), c(1, 7) to c(10, 7), c(10, 7) to c(10, 3), c(1, 3) to c(10, 3)),
        ),
        Stage(
            "b3", 1, "再买一辆", "南边那条线没有车，得赚够了按「买车」。",
            listOf(Stop("mine", c(1, 1), Dir.N, "mine", "矿场"), Stop("factory", c(10, 1), Dir.N, "factory", "工厂"), Stop("mine2", c(1, 6), Dir.N, "mine", "南矿"), Stop("factory2", c(10, 6), Dir.N, "factory", "南厂")),
            emptyMap(), listOf(TrainDef("mine", Cargo.COAL, 2)),
            listOf(
                o(2.0, Cargo.COAL, "mine", "factory", 26.0, 6), o(20.0, Cargo.COAL, "mine2", "factory2", 40.0, 6), o(40.0, Cargo.COAL, "mine", "factory", 24.0, 6),
                o(42.0, Cargo.COAL, "mine2", "factory2", 24.0, 6), o(60.0, Cargo.COAL, "mine", "factory", 22.0, 6), o(62.0, Cargo.COAL, "mine2", "factory2", 22.0, 6),
                o(80.0, Cargo.COAL, "mine", "factory", 22.0, 6), o(82.0, Cargo.COAL, "mine2", "factory2", 22.0, 6),
            ),
            20, shop = TrainDef("mine2", Cargo.COAL, 2), price = 8, plan = listOf(c(1, 1) to c(10, 1), c(1, 6) to c(10, 6)), buys = 1,
        ),
        Stage(
            "b4", 1, "会让线", "一条窄山谷。两列车对开，在单线上碰头谁也过不去。在中间修一段并排的会让线（要比列车长），列车就会自己错开。",
            listOf(Stop("mine", c(1, 3), Dir.W, "mine", "矿场"), Stop("factory", c(10, 3), Dir.E, "factory", "工厂"), Stop("farm", c(10, 5), Dir.E, "farm", "农场"), Stop("town", c(1, 5), Dir.W, "house2", "商店")),
            (0 until Play.H).filter { it != 4 && it != 3 }.associate { Cell(5, it) to Ground.ROCK } + (0 until Play.H).filter { it != 4 && it != 3 }.associate { Cell(6, it) to Ground.ROCK },
            listOf(TrainDef("mine", Cargo.COAL, 2), TrainDef("farm", Cargo.GOODS, 2)),
            (0 until 6).flatMap { k ->
                val at = 2.0 + k * 19
                listOf(o(at, Cargo.COAL, "mine", "factory", 26.0, 7), o(at + 1, Cargo.GOODS, "farm", "town", 26.0, 7))
            },
            16, plan = listOf(c(1, 3) to c(10, 3), c(1, 5) to c(1, 3), c(10, 5) to c(10, 3), c(4, 3) to c(4, 4), c(4, 4) to c(7, 4), c(7, 4) to c(7, 3)),
        ),
        Stage(
            "c1", 2, "一个圈", "两列车、四个地方。铺成一个圈，同向跑就不会碰头，但圈要钱，先铺哪一段你来定。",
            listOf(Stop("mine", c(2, 1), Dir.N, "mine", "矿场"), Stop("factory", c(9, 1), Dir.N, "factory", "工厂"), Stop("farm", c(9, 6), Dir.E, "farm", "农场"), Stop("town", c(2, 6), Dir.W, "house2", "商店")),
            emptyMap(), listOf(TrainDef("mine", Cargo.COAL, 2), TrainDef("farm", Cargo.GOODS, 2)),
            listOf(
                o(3.0, Cargo.COAL, "mine", "factory", 26.0, 6), o(4.0, Cargo.GOODS, "farm", "town", 26.0, 6), o(24.0, Cargo.COAL, "mine", "factory", 26.0, 6), o(25.0, Cargo.GOODS, "farm", "town", 26.0, 6),
                o(44.0, Cargo.GOODS, "farm", "town", 24.0, 6), o(46.0, Cargo.COAL, "mine", "factory", 24.0, 6), o(64.0, Cargo.COAL, "mine", "factory", 22.0, 6), o(66.0, Cargo.GOODS, "farm", "town", 22.0, 6),
            ),
            21, plan = listOf(c(2, 1) to c(9, 1), c(9, 1) to c(9, 6), c(9, 6) to c(2, 6), c(2, 6) to c(2, 1)),
        ),
        Stage(
            "c2", 2, "一座桥", "河上架桥很贵，钱只够一座。两边的货都要从这座桥过：桥两头各修一段会让线（要比列车长）。半路还会塌方：捏塌的那一格就能修好，留点钱。",
            listOf(Stop("mine", c(1, 1), Dir.N, "mine", "矿场"), Stop("factory", c(10, 1), Dir.N, "factory", "工厂"), Stop("farm", c(10, 6), Dir.E, "farm", "农场"), Stop("town", c(1, 6), Dir.W, "house2", "商店")),
            river(5) + river(6), listOf(TrainDef("mine", Cargo.COAL, 2), TrainDef("farm", Cargo.GOODS, 2)),
            listOf(
                o(2.0, Cargo.COAL, "mine", "factory", 34.0, 8), o(4.0, Cargo.GOODS, "farm", "town", 34.0, 8), o(36.0, Cargo.COAL, "mine", "factory", 32.0, 8), o(38.0, Cargo.GOODS, "farm", "town", 32.0, 8),
                o(70.0, Cargo.GOODS, "farm", "town", 30.0, 8), o(72.0, Cargo.COAL, "mine", "factory", 30.0, 8), o(102.0, Cargo.COAL, "mine", "factory", 30.0, 8), o(104.0, Cargo.GOODS, "farm", "town", 30.0, 8),
            ),
            27, washouts = listOf(60.0 to c(3, 3)), plan = listOf(c(1, 1) to c(1, 3), c(1, 6) to c(1, 3), c(1, 3) to c(10, 3), c(10, 3) to c(10, 1), c(10, 6) to c(10, 3), c(2, 3) to c(2, 4), c(2, 4) to c(4, 4), c(4, 4) to c(4, 3), c(7, 3) to c(7, 4), c(7, 4) to c(9, 4), c(9, 4) to c(9, 3)),
        ),
        Stage(
            "c3", 2, "山口", "一道石墙，北边一个山口，南边一个两格宽的山口。钱不够修三条线，有两种货得共用南线：修一段穿过南山口的会让线，它们才错得开。",
            listOf(
                Stop("mine", c(1, 1), Dir.N, "mine", "矿场"), Stop("factory", c(10, 1), Dir.N, "factory", "工厂"), Stop("farm", c(10, 4), Dir.E, "farm", "农场"), Stop("town", c(1, 4), Dir.W, "house2", "商店"),
                Stop("west", c(1, 7), Dir.W, "station", "西站"), Stop("east", c(10, 7), Dir.E, "station", "东站"),
            ),
            wall(5, 2, 5, 6), listOf(TrainDef("mine", Cargo.COAL, 2), TrainDef("farm", Cargo.GOODS, 2), TrainDef("west", Cargo.PEOPLE, 2)),
            listOf(
                o(3.0, Cargo.COAL, "mine", "factory", 40.0, 8), o(4.0, Cargo.GOODS, "farm", "town", 40.0, 8), o(5.0, Cargo.PEOPLE, "west", "east", 40.0, 8),
                o(40.0, Cargo.PEOPLE, "east", "west", 36.0, 8), o(42.0, Cargo.COAL, "mine", "factory", 36.0, 8), o(44.0, Cargo.GOODS, "farm", "town", 36.0, 8),
                o(80.0, Cargo.GOODS, "farm", "town", 34.0, 8), o(82.0, Cargo.PEOPLE, "west", "east", 34.0, 8), o(84.0, Cargo.COAL, "mine", "factory", 34.0, 8),
                rush(60.0, Cargo.PEOPLE, "east", "west", 20.0, 18),
            ).sortedBy { it.at },
            28, plan = listOf(c(1, 1) to c(1, 2), c(1, 2) to c(10, 2), c(10, 2) to c(10, 1), c(1, 4) to c(1, 5), c(1, 5) to c(10, 5), c(10, 5) to c(10, 4), c(1, 7) to c(1, 5), c(10, 7) to c(10, 5), c(3, 5) to c(3, 6), c(3, 6) to c(8, 6), c(8, 6) to c(8, 5)),
        ),
        Stage(
            "c4", 2, "全城通车", "最后一关：一条河，钱只够两座桥，三种货，还有急单和塌方。赚了钱就修会让线，或者再买一辆车。",
            listOf(
                Stop("mine", c(1, 1), Dir.N, "mine", "矿场"), Stop("factory", c(10, 2), Dir.N, "factory", "工厂"), Stop("farm", c(1, 5), Dir.W, "farm", "农场"), Stop("town", c(10, 5), Dir.E, "house2", "商店"),
                Stop("west", c(3, 7), Dir.W, "station", "西站"), Stop("east", c(8, 7), Dir.E, "station", "东站"),
            ),
            river(5) + hills(8 to 2, 8 to 1, 8 to 3), listOf(TrainDef("mine", Cargo.COAL, 3), TrainDef("farm", Cargo.GOODS, 2), TrainDef("west", Cargo.PEOPLE, 2)),
            listOf(
                o(3.0, Cargo.COAL, "mine", "factory", 34.0, 8), o(5.0, Cargo.GOODS, "farm", "town", 34.0, 8), o(7.0, Cargo.PEOPLE, "west", "east", 34.0, 8),
                o(34.0, Cargo.PEOPLE, "east", "west", 30.0, 8), o(36.0, Cargo.COAL, "mine", "factory", 30.0, 8), o(38.0, Cargo.GOODS, "farm", "town", 30.0, 8),
                o(64.0, Cargo.GOODS, "farm", "town", 28.0, 8), o(66.0, Cargo.PEOPLE, "west", "east", 28.0, 8), o(68.0, Cargo.COAL, "mine", "factory", 28.0, 8),
                o(92.0, Cargo.COAL, "mine", "factory", 26.0, 8), o(94.0, Cargo.PEOPLE, "east", "west", 26.0, 8), o(96.0, Cargo.GOODS, "farm", "town", 26.0, 8),
                rush(50.0, Cargo.COAL, "mine", "factory", 18.0, 18), rush(80.0, Cargo.PEOPLE, "west", "east", 18.0, 18),
            ).sortedBy { it.at },
            30, washouts = listOf(75.0 to c(7, 5)), shop = TrainDef("mine", Cargo.COAL, 2), price = 16, plan = listOf(c(1, 1) to c(10, 2), c(1, 5) to c(10, 5), c(3, 7) to c(3, 5), c(8, 7) to c(8, 5), c(6, 5) to c(6, 4), c(6, 4) to c(9, 4), c(9, 4) to c(9, 5)),
        ),
    )

    /** The gold trophy's show: three trains round the whole board, no orders to worry about. */
    val SHOW = Stage(
        "show", 2, "全线通车", "",
        listOf(
            Stop("west", c(1, 3), Dir.W, "station", "西站"), Stop("east", c(10, 4), Dir.E, "station", "东站"),
            Stop("factory", c(5, 1), Dir.N, "factory", "工厂"), Stop("town", c(6, 6), Dir.N, "house2", "商店"),
        ),
        emptyMap(), listOf(TrainDef("west", Cargo.PEOPLE, 3), TrainDef("factory", Cargo.GOODS, 2), TrainDef("town", Cargo.COAL, 3)),
        (0..60).flatMap { k ->
            val at = 2.0 + k * 9
            listOf(
                OrderDef(at, Cargo.PEOPLE, if (k % 2 == 0) "west" else "east", if (k % 2 == 0) "east" else "west", 9999.0, 0),
                OrderDef(at + 1, Cargo.GOODS, if (k % 2 == 0) "factory" else "town", if (k % 2 == 0) "town" else "factory", 9999.0, 0),
            )
        },
        999, plan = listOf(c(1, 1) to c(10, 1), c(10, 1) to c(10, 6), c(10, 6) to c(1, 6), c(1, 6) to c(1, 1)),
    )

    fun byId(id: String) = ALL.first { it.id == id }
}
