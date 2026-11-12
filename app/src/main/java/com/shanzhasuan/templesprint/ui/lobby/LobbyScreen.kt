package com.shanzhasuan.templesprint.ui.lobby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import com.pico.spatial.ui.design.Button
import com.shanzhasuan.templesprint.ui.theme.templeButtonColors
import com.shanzhasuan.templesprint.ui.theme.TempleColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.shanzhasuan.templesprint.R
import com.shanzhasuan.templesprint.content.RunnerUi
import com.shanzhasuan.templesprint.game.Chapter
import com.shanzhasuan.templesprint.ui.lobby.components.ChapterCard
import com.shanzhasuan.templesprint.ui.lobby.components.ControlGuide

@Composable
fun LobbyScreen(ui:RunnerUi,onSelect:(Int)->Unit,onStart:()->Unit,onCalibrate:()->Unit,onComfort:()->Unit){
    val density=LocalDensity.current
    // design-style: design-surface chapter-selection
    Column(Modifier.fillMaxSize().onSizeChanged{android.util.Log.i("LobbyLayout","panelPx=$it density=${density.density} fontScale=${density.fontScale}")}.clip(RoundedCornerShape(32.dp)).background(TempleColors.templeSurface).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("神庙之空间逃亡",style=PicoTheme.typography.titleLarge.copy(fontSize=32.sp,lineHeight=44.sp),color=TempleColors.templeTextPrimary)
            Text("选择关卡，开始逃脱",style=PicoTheme.typography.bodyLarge.copy(fontSize=24.sp,lineHeight=34.sp),color=TempleColors.templeTextSecondary)
        }
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)){
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text("玩法介绍",style=PicoTheme.typography.titleMedium.copy(fontSize=26.sp,lineHeight=36.sp),color=TempleColors.templeTextPrimary)
                    Text("自动奔跑 · 收集金币 · 躲避机关 · 越跑越快",style=PicoTheme.typography.bodyLarge.copy(fontSize=24.sp,lineHeight=34.sp),color=TempleColors.templeTextSecondary)
                }
                Column(Modifier.fillMaxWidth().weight(1f).selectableGroup(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    for(row in 0..1)Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                        for(index in row*2..row*2+1){
                            val chapter=Chapter.entries[index]
                            ChapterCard(chapter,cover(index),chapter.description.replaceFirst("，","\n"),ui.selectedChapter==index,!ui.error,ui.ready&&!ui.error,{onSelect(index)},onStart,Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
            Column(Modifier.width(300.dp).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(14.dp)){
                Text("已选：${Chapter.entries[ui.selectedChapter].title}",maxLines=1,overflow=TextOverflow.Ellipsis,style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=34.sp),color=TempleColors.templeTextPrimary)
                Button(onClick=onStart,colors=templeButtonColors(primary=true),enabled=ui.ready&&!ui.error,modifier=Modifier.fillMaxWidth().height(60.dp).onGloballyPositioned{android.util.Log.i("LobbyLayout","startButtonBounds=${it.boundsInRoot()} ready=${ui.ready} selected=${ui.selectedChapter+1}")}){
                    Text("开始游戏",style=PicoTheme.typography.titleMedium.copy(fontSize=26.sp,lineHeight=36.sp))
                }
                Text(when{ui.error->"场景准备失败，请重新打开";!ui.ready->"场景准备中，稍后可开始";else->"点上方或卡片按钮开始"},style=PicoTheme.typography.bodyMedium.copy(fontSize=22.sp,lineHeight=30.sp),color=TempleColors.templeTextSecondary)
                ControlGuide(ui.comfort,onCalibrate,onComfort,Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

private fun cover(index:Int)=when(index){0->R.drawable.chapter_cover_0;1->R.drawable.chapter_cover_1;2->R.drawable.chapter_cover_2;else->R.drawable.chapter_cover_3}
