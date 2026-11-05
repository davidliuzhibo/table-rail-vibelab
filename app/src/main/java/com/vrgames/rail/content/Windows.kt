package com.vrgames.rail.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.vrgames.rail.GUIDE_ID
import com.vrgames.rail.LEVELS_ID
import com.vrgames.rail.core.Stages
import com.vrgames.rail.game.Game

private val CardBg = Color(0x26FFF8EC)

/** 关卡: every level with its stars (pick one to play), and the three trophies. */
@Composable
fun LevelsWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WindowHeader(LEVELS_ID, "关卡", 32.sp)
        Text("总星数 ${Game.stars} / ${Game.maxStars}。点一关就能玩。", fontSize = 22.sp, color = Ink)
        Text(
            (if (Game.bronze) "🥉 铜奖杯：十二关全过　" else "铜奖杯：十二关全过（未得）　") +
                (if (Game.silver) "🥈 银奖杯　" else "银奖杯：三分之二的星（未得）　") +
                (if (Game.gold) "🥇 金奖杯：全线通车" else "金奖杯：全部三星（未得）"),
            fontSize = 21.sp, color = Mint,
        )
        for ((k, s) in Stages.ALL.withIndex()) {
            if (k == 0 || Stages.ALL[k - 1].act != s.act) Text(Stages.ACTS[s.act], fontSize = 25.sp, color = Sky)
            val open = Game.open(s)
            val st = Game.best[s.id] ?: 0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg)
                    .clickable(enabled = open) { Game.closeWindow(LEVELS_ID); Game.intro(s) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${k + 1}. ${s.title}", fontSize = 24.sp, color = if (open) Ink else Dim)
                    Text("本钱 ${s.money}　${s.orders.size} 单" + (if (s.shop != null) "　能买车" else ""), fontSize = 19.sp, color = Dim)
                }
                Spacer(Modifier.weight(1f))
                Text(if (open) "★".repeat(st) + "☆".repeat(3 - st) else "未开放", fontSize = 23.sp, color = Brass)
            }
        }
    }
}

/** 铁路说明: what is on the board and how it works. */
@Composable
fun GuideWindow() {
    Game.version
    Column(Modifier.fillMaxSize().background(WindowBg).padding(28.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WindowHeader(GUIDE_ID, "铁路说明", 32.sp)
        for ((t, d) in GUIDE) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(14.dp)) {
                Text(t, fontSize = 24.sp, color = Brass)
                Text(d, fontSize = 20.sp, color = Ink)
            }
        }
    }
}

private val GUIDE = listOf(
    "你的工作" to "你是调度员。各站不断来订单，每单都有时限。铺轨、派车，把货按时送到。送到挣钱，超时扣信誉，信誉没了就输。",
    "铺轨" to "看向一格捏一下，那一格亮起来，再看向另一格捏一下，轨道自动铺过去，会绕开石头和建筑。接着捏下一格可以继续往前铺。捏同一格是松手。开张后也能铺。",
    "钱" to "草地一格 1，河上架桥、山里打隧道一格 3。车站门口那一格不要钱。钱不够时先铺便宜的，送货赚了钱再改线。",
    "拆除" to "按「拆除」后，捏一段轨道：从那里到两头的车站或岔口，整段都拆掉，钱全退。再按一下结束。「撤销」是退回上一次铺的线。",
    "装货和发车" to "规则只有一条：车停在站里，站里有它能运的，就自动装上。装好后捏一下车就发车（捏它的目的地也行），它知道去哪，到了自动卸。货在别的站：捏那个站，最近的空车开过去，到了自动装好，再捏车发车。想指定用哪列空车：先捏空车（脚下出现绿圈），再捏车站。",
    "道岔" to "不用手动扳。列车到岔口会自己选路。",
    "列车种类" to "每列车只运一种货：煤车、货车、油车、客车。标牌颜色和列车头上的小灯是货的颜色。",
    "单线会车" to "两列车在单线上对开就过不去。在中间修一段并排的会让线，一列会在岔口外等另一列开过去。会让线要比列车长，太短列车躲不进去，两边就都堵死了。也可以选中列车，让它停下等。",
    "撞车" to "两列车迎面相撞：赔钱，还耽误 5 秒，这一关也拿不到三星。",
    "买车" to "有的关卡面板上有「买车」。单子同时来、一辆车跑不过来时，就买一辆。",
    "急单" to "金色的急单钱多、时间短，赶不上也不扣信誉。要不要为它打乱别的单子，你来决定。",
    "小费" to "时限还剩一半以上就送到，客户多给 2 块。",
    "塌方" to "有的关卡轨道会被山石埋住。捏埋住的那一格就能修好，要花原价两倍。列车会等，修好后继续走。留点钱。",
    "小镇" to "送得越多，车站周围长出的房子越多。",
    "星星" to "过关一星；一单不超时再加一星；一次没撞车再加一星。",
    "奖杯" to "十二关全过：铜奖杯。拿到三分之二的星：银奖杯。全部三星：金奖杯，解锁「全线通车」。",
)
