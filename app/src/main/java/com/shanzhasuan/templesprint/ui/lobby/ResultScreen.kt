package com.shanzhasuan.templesprint.ui.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.shanzhasuan.templesprint.ui.theme.templeButtonColors
import com.shanzhasuan.templesprint.ui.theme.TempleColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.shanzhasuan.templesprint.content.RunnerUi
import com.shanzhasuan.templesprint.game.Phase

@Composable
fun ResultScreen(ui:RunnerUi,onPrimary:()->Unit,onSelect:()->Unit){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        // design-style: design-surface result
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(TempleColors.templeSurface).padding(32.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
            Text(when(ui.phase){Phase.PAUSED->"游戏暂停";Phase.CLEARED->"逃脱成功";else->"游戏结束"},style=PicoTheme.typography.titleLarge.copy(fontSize=32.sp,lineHeight=44.sp),color=TempleColors.templeTextPrimary)
            if(ui.phase!=Phase.PAUSED){
                Text("${ui.distance} 米 · ${ui.coins} 金币",style=PicoTheme.typography.bodyLarge.copy(fontSize=26.sp,lineHeight=36.sp),color=TempleColors.templeTextPrimary)
                Text("得分 ${ui.score}",style=PicoTheme.typography.bodyLarge.copy(fontSize=26.sp,lineHeight=36.sp),color=TempleColors.templeTextPrimary)
                Text(ui.reason,style=PicoTheme.typography.bodyMedium.copy(fontSize=24.sp,lineHeight=34.sp),color=TempleColors.templeTextSecondary)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){
                Button(onClick=onPrimary,colors=templeButtonColors(primary=true),modifier=Modifier.height(56.dp)){Text(if(ui.phase==Phase.PAUSED)"继续游戏" else "再玩一次",style=PicoTheme.typography.titleMedium.copy(fontSize=26.sp,lineHeight=36.sp))}
                Button(onClick=onSelect,colors=templeButtonColors(),modifier=Modifier.height(56.dp)){Text("返回选关",style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=34.sp))}
            }
        }
    }
}
