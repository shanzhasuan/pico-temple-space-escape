package com.shanzhasuan.templesprint.ui.lobby.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.shanzhasuan.templesprint.ui.theme.templeButtonColors
import com.shanzhasuan.templesprint.ui.theme.TempleColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.shanzhasuan.templesprint.game.Chapter

@Composable
fun ChapterCard(chapter:Chapter,cover:Int,summary:String,selected:Boolean,enabled:Boolean,canStart:Boolean,onSelect:()->Unit,onStart:()->Unit,modifier:Modifier=Modifier){
    Column(modifier.clip(RoundedCornerShape(20.dp)).spatialHoverEffect().selectable(selected=selected,enabled=enabled,role=Role.RadioButton,onClick=onSelect).padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Image(painterResource(cover),contentDescription="${chapter.title}场景",contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).onGloballyPositioned{android.util.Log.i("LobbyLayout","chapter=${chapter.ordinal+1} imageBounds=${it.boundsInRoot()}")})
        Text("${chapter.ordinal+1}. ${chapter.title}",modifier=Modifier.onGloballyPositioned{android.util.Log.i("LobbyLayout","chapter=${chapter.ordinal+1} titleBounds=${it.boundsInRoot()}")},maxLines=1,overflow=TextOverflow.Ellipsis,style=PicoTheme.typography.titleMedium.copy(fontSize=26.sp,lineHeight=34.sp),color=TempleColors.templeTextPrimary)
        Text(summary,modifier=Modifier.fillMaxWidth().height(64.dp).onGloballyPositioned{android.util.Log.i("LobbyLayout","chapter=${chapter.ordinal+1} summaryBounds=${it.boundsInRoot()}")},maxLines=2,overflow=TextOverflow.Clip,onTextLayout={android.util.Log.i("LobbyLayout","chapter=${chapter.ordinal+1} summaryOverflow=${it.hasVisualOverflow} summaryLines=${it.lineCount}")},style=PicoTheme.typography.bodyMedium.copy(fontSize=22.sp,lineHeight=30.sp),color=TempleColors.templeTextSecondary)
        Button(onClick={if(selected)onStart()else onSelect()},colors=templeButtonColors(primary=selected),enabled=if(selected)canStart else enabled,modifier=Modifier.fillMaxWidth().height(48.dp).onGloballyPositioned{android.util.Log.i("LobbyLayout","chapter=${chapter.ordinal+1} actionBounds=${it.boundsInRoot()} selected=$selected canStart=$canStart")}){
            Text(if(selected)"开始本关" else "选择本关",style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=32.sp))
        }
    }
}
