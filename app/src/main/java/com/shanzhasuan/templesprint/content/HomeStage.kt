package com.shanzhasuan.templesprint.content

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pico.spatial.core.ecs.System as SpatialSystem
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.design.Button
import com.shanzhasuan.templesprint.ui.theme.templeButtonColors
import com.shanzhasuan.templesprint.ui.theme.TempleColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.shanzhasuan.templesprint.game.Phase
import com.shanzhasuan.templesprint.game.Chapter
import com.shanzhasuan.templesprint.game.RunnerPage
import com.shanzhasuan.templesprint.ui.lobby.LobbyScreen
import com.shanzhasuan.templesprint.ui.lobby.ResultScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun HomeStage() {
    val context=LocalContext.current
    val density=LocalDensity.current
    val hudSize=with(density){IntSize(760.dp.roundToPx(),160.dp.roundToPx())}
    val threatSize=with(density){IntSize(650.dp.roundToPx(),64.dp.roundToPx())}
    val scope=rememberCoroutineScope()
    var ui by remember{mutableStateOf(RunnerUi())}
    // This ROM clips an attachment surface at 1440px while its Compose layout can be taller.
    // Rasterize below that height, then restore the same physical size on the panel Entity.
    val selectionRasterScale=minOf(1f,1320f/(RunnerMenuPanel.SELECTION.heightDp*density.density))
    val selectionDensity=Density(density.density*selectionRasterScale,density.fontScale)
    val menuPanel=RunnerMenuPanel.forState(ui.page,ui.phase)
    val menuDensity=if(menuPanel==RunnerMenuPanel.SELECTION)selectionDensity else density
    val menuSize=with(menuDensity){IntSize(menuPanel.widthDp.dp.roundToPx(),menuPanel.heightDp.dp.roundToPx())}
    val scene=remember{RunnerScene(context.applicationContext){ui=it}}
    val owner=LocalLifecycleOwner.current
    val hmd=remember{HMDTrackingProvider()}
    val controllers=remember{ControllerTrackingProvider()}
    LaunchedEffect(hmd){
        var gotPacket=false
        val watchdog=launch{while(isActive){delay(2000);if(!gotPacket&&hmd.state==DataProvider.State.CREATED){hmd.stop();hmd.start()}}}
        try{hmd.start();hmd.dataFlow.collect{gotPacket=true;scene.observeHead(it.hmdPose)}}
        finally{watchdog.cancel();hmd.stop()}
    }
    LaunchedEffect(controllers){
        val listener=ControllerTrackingProvider.ControllerActionListener{scene.input.observe(it)}
        controllers.addControllerActionListener(listener)
        var gotPacket=false
        val watchdog=launch{while(isActive){delay(2000);if(!gotPacket&&controllers.state==DataProvider.State.CREATED){controllers.stop();controllers.start()}}}
        try{controllers.start();controllers.dataFlow.collect{if(!gotPacket){gotPacket=true;android.util.Log.i("RunnerInput","controller tracking active")}}}
        finally{watchdog.cancel();controllers.removeControllerActionListener(listener);controllers.stop()}
    }
    DisposableEffect(scene,owner){
        val needsRegistration=RunnerSystem.active==null
        RunnerSystem.active=scene
        if(needsRegistration)SpatialSystem.register(RunnerSystem::class.java)
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)scene.pause()}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer);if(RunnerSystem.active===scene){RunnerSystem.active=null;SpatialSystem.unregister(RunnerSystem::class.java)};scene.close()}
    }
    SpatialView(
        modifier=Modifier.fillMaxSize(),
        initial={content,attachments->scene.attachPanels(content,attachments,selectionRasterScale);scope.launch{scene.load(content,attachments)}},
        update={content,attachments->scene.attachPanels(content,attachments,selectionRasterScale)},
        attachments={
            if(ui.ready&&ui.page==RunnerPage.GAME&&ui.phase==Phase.RUNNING){
              AttachmentPanel("runner-hud",size=hudSize){
                // design-style: design-surface hud
                Row(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(TempleColors.templeSurface).padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{
                        Text("第${ui.chapter+1}关 · ${Chapter.entries[ui.chapter].title}",style=PicoTheme.typography.titleMedium.copy(fontSize=22.sp,lineHeight=28.sp),color=TempleColors.templeTextPrimary)
                        Text("${ui.distance} 米 · ${ui.coins} 金币",style=PicoTheme.typography.bodyMedium.copy(fontSize=20.sp,lineHeight=26.sp),color=TempleColors.templeTextPrimary)
                        Text("速度 ${"%.1f".format(ui.speed)} 米/秒",style=PicoTheme.typography.bodyMedium.copy(fontSize=20.sp,lineHeight=26.sp),color=TempleColors.templeTextPrimary)
                        Text(if(ui.pursuerClose)"守卫正在逼近！" else "守卫在身后追赶",style=PicoTheme.typography.bodyMedium.copy(fontSize=20.sp,lineHeight=26.sp),color=TempleColors.templeTextPrimary)
                    }
                    Text(ui.hint,style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=32.sp),color=TempleColors.templeTextPrimary)
                    Button(onClick=scene::togglePause,colors=templeButtonColors(),enabled=ui.ready){Text("暂停",style=PicoTheme.typography.titleMedium.copy(fontSize=22.sp))}
                }
            }
            }
            if(ui.showThreat){
              AttachmentPanel("runner-threat",size=threatSize){
                // design-style: design-surface pursuit
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(TempleColors.templeSurface).padding(12.dp),contentAlignment=Alignment.Center){
                    Text(if(ui.caught)"被神庙守卫抓住了" else if(ui.phase==Phase.ENDED)"守卫停在身后，可回头查看" else "守卫逼近，稳住！",style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=32.sp),color=TempleColors.templeTextPrimary)
                }
            }
            }
            if(ui.showMenu){
                AttachmentPanel(menuPanel.id,size=menuSize){
                    CompositionLocalProvider(LocalDensity provides menuDensity){
                        key(menuPanel.id){
                            if(menuPanel==RunnerMenuPanel.SELECTION)LobbyScreen(ui,scene::chooseChapter,scene::startOrResume,scene::calibrate,scene::toggleComfort)
                            else ResultScreen(ui,scene::startOrResume,scene::returnToSelection)
                        }
                    }
                }
            }
        }
    )
}
