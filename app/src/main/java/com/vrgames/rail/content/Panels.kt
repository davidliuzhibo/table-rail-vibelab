package com.vrgames.rail.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.rail.GUIDE_ID
import com.vrgames.rail.LEVELS_ID
import com.vrgames.rail.core.Play
import com.vrgames.rail.core.Stages
import com.vrgames.rail.game.Game

const val PANEL_CARD = "card"
const val PANEL_INTRO = "intro"
const val PANEL_HUD = "hud"
const val PANEL_RESULT = "result"
const val PANEL_SHOW = "show"
const val PANEL_MENU = "menu"
const val PANEL_NOTE = "note"

val Ink = Color(0xFFFFF8EC)
val Brass = Color(0xFFF2C14E)
val Sky = Color(0xFF9CD3E8)
val Mint = Color(0xFF9FE3A8)
val Dim = Color(0xFFCFC4B2)
val Alarm = Color(0xFFFF7B5C)
private val PanelBg = Color(0xEA2A1D14)
/** Window backdrop: the system glass can be light, and every text color here is meant for dark. */
val WindowBg = Color(0xF22A1D14)

@Composable
fun PhysicalLengthConverter.m(meters: Float): Dp = lengthToDp(meters, LengthUnit.Meters)

@Composable
fun PhysicalLengthConverter.sz(meters: Float) = m(meters).value.sp

@Composable
private fun PanelBox(c: PhysicalLengthConverter, width: Float, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.016f))
    Column(
        Modifier.width(c.m(width)).clip(shape).background(PanelBg).border(c.m(0.0015f), Color(0x88F2C14E), shape).padding(c.m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(c.m(0.008f)),
    ) { content() }
}

/** The step that moves the game on. */
@Composable
fun CallToAction(c: PhysicalLengthConverter, text: String, color: Color = Brass, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.014f))
    Text(
        text,
        Modifier.clip(shape).background(color).border(c.m(0.002f), Color(0xFFFFF4E0), shape)
            .clickable { onClick() }.padding(horizontal = c.m(0.024f), vertical = c.m(0.013f)),
        fontSize = c.sz(0.024f),
        color = Color(0xFF1A1420),
    )
}

@Composable
fun Pill(c: PhysicalLengthConverter, text: String, color: Color = Dim, onClick: () -> Unit) {
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(
        text,
        Modifier.clip(shape).border(c.m(0.0015f), color, shape).clickable { onClick() }.padding(horizontal = c.m(0.014f), vertical = c.m(0.008f)),
        fontSize = c.sz(0.017f),
        color = color,
    )
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

private fun clock(s: Double): String = "%d:%02d".format(s.toInt() / 60, s.toInt() % 60)

@Composable
fun CardPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.6f) {
        Game.version // Read in here: the same arguments, so the box would skip its content.
        Text("桌上铁路", fontSize = c.sz(0.046f), color = Brass)
        Text("一张桌子，一座小镇，你是调度员。订单一个接一个，有时限。铺轨道，派列车，别让它们在单线上堵死。", fontSize = c.sz(0.018f), color = Ink)
        Text("怎么玩", fontSize = c.sz(0.02f), color = Mint)
        Text("· 捏一格，再捏另一格：两格之间铺好轨道。铺好按「开张」。\n· 车停在站里，站里有货或客就自动装上。装好后捏一下车就发车，它知道去哪。\n· 货在别的站：捏那个站，空车开过去，自动装好，再捏车发车。", fontSize = c.sz(0.0165f), color = Ink)
        if (Game.stars > 0) Text("已得 ${Game.stars} / ${Game.maxStars} 颗星", fontSize = c.sz(0.016f), color = Mint)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, if (Game.stars > 0) "接着玩 →" else "开工 →") { Game.start() }
            if (Game.gold) Pill(c, "全线通车", Brass) { Game.show() }
        }
    }
}

@Composable
fun IntroPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.56f) {
        Game.version
        val s = Game.stage
        Text("第 ${Stages.ALL.indexOf(s) + 1} 关 · ${s.title}", fontSize = c.sz(0.034f), color = Brass)
        Text(Stages.ACTS[s.act] + "　本钱 ${s.money}　${s.orders.size} 单", fontSize = c.sz(0.016f), color = Sky)
        Text(s.ask, fontSize = c.sz(0.0185f), color = Ink)
        Text("信誉 ${Play.REPUTATION} 颗，超时一单少一颗，没了就输。", fontSize = c.sz(0.0165f), color = Mint)
        Text("三星：一单不超时，并且不撞车。开张前不计时。", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            CallToAction(c, "开始 →") { Game.begin() }
            Pill(c, "返回") { Game.toCard() }
        }
    }
}

/** The orders on the board, most urgent first. */
@Composable
private fun Orders(c: PhysicalLengthConverter, p: Play) {
    // Read here: the Play is the same object as it changes, so without this the list skips recomposing.
    Game.version
    for (o in p.orders.filter { it.open }.sortedBy { it.left }.take(5)) {
        val urgent = o.left < 10
        val state = when {
            o.loaded && o.taken?.ready == true -> "已装好，捏车发车"
            o.loaded -> "在路上"
            o.taken?.loading == true -> "正在装"
            o.taken != null -> "车去装了"
            else -> "待派车"
        }
        Text(
            (if (o.def.rush) "急单赏${o.def.pay} " else "") + "${o.left.toInt()} 秒　${p.stop(o.def.from).title}→${p.stop(o.def.to).title}　${o.def.cargo.title}　$state",
            fontSize = c.sz(0.0155f), color = if (o.def.rush) Brass else if (urgent) Alarm else if (o.taken == null) Ink else Mint,
        )
    }
}

@Composable
fun HudPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    PanelBox(c, 0.36f) {
        Game.version
        val p = Game.play ?: return@PanelBox
        Text("第 ${Stages.ALL.indexOf(p.stage) + 1} 关 · ${p.stage.title}", fontSize = c.sz(0.02f), color = Brass)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.014f)), verticalAlignment = Alignment.CenterVertically) {
            Text("钱 ${p.money}", fontSize = c.sz(0.022f), color = Sky)
            Text("信誉 " + "♥".repeat(p.reputation.coerceAtLeast(0)), fontSize = c.sz(0.02f), color = if (p.reputation <= 1) Alarm else Mint)
            Text("已送 ${p.delivered} / ${p.total}", fontSize = c.sz(0.02f), color = Ink)
        }
        if (!p.running) {
            Text("铺轨不计时。一格 1，桥和隧道一格 3。铺好就开张。", fontSize = c.sz(0.0155f), color = Ink)
            CallToAction(c, "开张 →") { Game.go() }
        } else {
            Text("开张 ${clock(p.time)}", fontSize = c.sz(0.0145f), color = Dim)
            Orders(c, p)
            if (p.orders.none { it.open }) Text("等下一单……", fontSize = c.sz(0.0155f), color = Dim)
            p.selected?.let { t ->
                Text("选中：空的${t.def.cargo.title}车。捏一个车站，派它过去。", fontSize = c.sz(0.0155f), color = Mint)
                Pill(c, if (t.held) "让它继续走" else "让它停下等", Mint) { Game.hold(t.id) }
            }
        }
        if (p.damaged.isNotEmpty()) Text("有 ${p.damaged.size} 处塌方：捏那一格修好", fontSize = c.sz(0.0165f), color = Alarm)
        if (p.crashes > 0) Text("撞车 ${p.crashes} 次", fontSize = c.sz(0.015f), color = Alarm)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, if (p.erasing) "拆除中：捏要拆的线（捏这里结束）" else "拆除", if (p.erasing) Alarm else Dim) { Game.erasing(!p.erasing) }
            if (p.canUndo) Pill(c, "撤销") { Game.undo() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            if (p.stage.shop != null) Pill(c, "买车 ${p.stage.price}", if (p.money >= p.stage.price) Brass else Dim) { Game.buy() }
            if (!p.running && p.money < p.stage.money) Pill(c, "全拆") { Game.clearTrack() }
            if (p.running) Pill(c, "重来") { Game.retry() }
            Pill(c, "关卡", Brass) { toggle(LEVELS_ID) }
            Pill(c, "说明") { toggle(GUIDE_ID) }
        }
    }
}

@Composable
fun ResultPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit) {
    PanelBox(c, 0.56f) {
        Game.version
        val p = Game.play ?: return@PanelBox
        if (p.won) {
            Text("${p.stage.title}　${stars(p.stars())}", fontSize = c.sz(0.034f), color = Brass)
            Text("送到 ${p.delivered} 单，超时 ${p.late} 单，撞车 ${p.crashes} 次，手里还有 ${p.money}。", fontSize = c.sz(0.018f), color = Ink)
            if (p.stars() < 3) Text("三星：一单不超时，并且不撞车。", fontSize = c.sz(0.015f), color = Dim)
        } else {
            Text("${p.stage.title}　信誉没了", fontSize = c.sz(0.034f), color = Alarm)
            Text("超时了 ${p.late} 单。试试换条近路，修会让线，或者早点买车。", fontSize = c.sz(0.018f), color = Ink)
        }
        val t = Game.trophies
        if (t > Game.trophiesBefore) Text(
            when (t) {
                1 -> "得到铜奖杯！十二关全过。"
                2 -> "得到银奖杯！"
                else -> "得到金奖杯！解锁「全线通车」。"
            },
            fontSize = c.sz(0.019f), color = Mint,
        )
        Text("总星数 ${Game.stars} / ${Game.maxStars}", fontSize = c.sz(0.015f), color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.01f)), verticalAlignment = Alignment.CenterVertically) {
            if (p.won && Stages.ALL.indexOf(p.stage) < Stages.ALL.size - 1) CallToAction(c, "下一关 →") { Game.next() }
            Pill(c, "再来一次", Brass) { Game.retry() }
            Pill(c, "关卡", Sky) { toggle(LEVELS_ID) }
            if (Game.gold) Pill(c, "全线通车", Brass) { Game.show() }
        }
    }
}

@Composable
fun ShowPanel(c: PhysicalLengthConverter) {
    PanelBox(c, 0.4f) {
        Text("全线通车", fontSize = c.sz(0.034f), color = Brass)
        Text("十二关全部三星。今晚全镇放烟花。", fontSize = c.sz(0.018f), color = Ink)
        Pill(c, "结束") { Game.endShow() }
    }
}

@Composable
fun NotePanel(c: PhysicalLengthConverter) {
    val n = Game.note ?: return
    val shape = RoundedCornerShape(c.m(0.012f))
    Text(n, Modifier.widthIn(max = c.m(0.7f)).clip(shape).background(PanelBg).padding(c.m(0.012f)), fontSize = c.sz(0.024f), color = Brass)
}

@Composable
fun MenuPanel(c: PhysicalLengthConverter, toggle: (String) -> Unit, faceMe: () -> Unit, quit: () -> Unit, version: String) {
    Game.version
    if (!Game.menuOpen) {
        Pill(c, "菜单", Brass) { Game.menuOpen = true }
        return
    }
    PanelBox(c, 0.28f) {
        Game.version
        Pill(c, if (Game.window == LEVELS_ID) "收起关卡" else "关卡（选关、奖杯）", Brass) { toggle(LEVELS_ID) }
        Pill(c, if (Game.window == GUIDE_ID) "收起说明" else "铁路说明") { toggle(GUIDE_ID) }
        if (Game.gold) Pill(c, "全线通车", Brass) { Game.menuOpen = false; Game.show() }
        Pill(c, "把沙盘摆到我正前方") { faceMe() }
        Pill(c, if (Game.musicOn) "音乐：开" else "音乐：关") { Game.toggleMusic() }
        RestartPill(c)
        Pill(c, "退出") { quit() }
        Text("进度自动保存。版本 $version", fontSize = c.sz(0.0125f), color = Dim)
        Pill(c, "收起菜单") { Game.menuOpen = false }
    }
}

@Composable
private fun RestartPill(c: PhysicalLengthConverter) {
    if (Game.confirmRestart) {
        Row(horizontalArrangement = Arrangement.spacedBy(c.m(0.006f))) {
            Pill(c, "确定清空进度", Alarm) { Game.confirmRestart = false; Game.menuOpen = false; Game.restart() }
            Pill(c, "算了") { Game.confirmRestart = false }
        }
    } else Pill(c, "清空进度从头开始") { Game.confirmRestart = true }
}

@Composable
fun WindowHeader(id: String, title: String, size: TextUnit) {
    val copy = remember { Any() }
    DisposableEffect(copy) {
        Game.windowShown(id, copy)
        onDispose { Game.windowGone(id, copy) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = size, color = Brass)
        Spacer(Modifier.weight(1f))
        Button(onClick = { Game.closeWindow(id) }) { Text("关闭", fontSize = 22.sp) }
    }
}
