package com.shanzhasuan.templesprint.ui.lobby.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.shanzhasuan.templesprint.ui.theme.templeButtonColors
import com.shanzhasuan.templesprint.ui.theme.TempleColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.shanzhasuan.templesprint.R

@Composable
fun ControlGuide(comfort:Boolean,onCalibrate:()->Unit,onComfort:()->Unit,modifier:Modifier=Modifier){
    Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            Text("操作说明",style=PicoTheme.typography.titleMedium.copy(fontSize=28.sp,lineHeight=38.sp),color=TempleColors.templeTextPrimary)
            Image(painterResource(R.drawable.guardian_preview),contentDescription="追击守卫模型预览",modifier=Modifier.size(80.dp))
        }
        val controls=listOf("移动／路口转弯" to "摇杆左右","跳跃" to "扳机 · A · X","滑铲" to "握键／摇杆向下","暂停" to "B · Y")
        for((title,control)in controls)Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(title,style=PicoTheme.typography.bodyMedium.copy(fontSize=22.sp,lineHeight=30.sp),color=TempleColors.templeTextSecondary)
            Text(control,style=PicoTheme.typography.bodyLarge.copy(fontSize=26.sp,lineHeight=36.sp),color=TempleColors.templeTextPrimary)
        }
        Text("到路口按箭头转弯\n轻擦碰会让守卫逼近",style=PicoTheme.typography.bodyLarge.copy(fontSize=24.sp,lineHeight=34.sp),color=TempleColors.templeTextPrimary)
        Button(onClick=onCalibrate,colors=templeButtonColors(),modifier=Modifier.fillMaxWidth().height(54.dp)){Text("坐姿校准",style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=32.sp))}
        Button(onClick=onComfort,colors=templeButtonColors(),modifier=Modifier.fillMaxWidth().height(54.dp)){Text(if(comfort)"舒适模式：开" else "舒适模式：关",style=PicoTheme.typography.titleMedium.copy(fontSize=24.sp,lineHeight=32.sp))}
        Text((if(comfort)"稳定视点与转弯遮罩\n3.8 → 5.4 米／秒" else "更快的逃脱节奏\n4.8 → 8.5 米／秒")+"\n坐姿游玩，无需真实跳跃",style=PicoTheme.typography.bodyMedium.copy(fontSize=22.sp,lineHeight=30.sp),color=TempleColors.templeTextSecondary)
    }
}
