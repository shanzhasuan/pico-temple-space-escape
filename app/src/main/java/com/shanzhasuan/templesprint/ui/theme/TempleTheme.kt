package com.shanzhasuan.templesprint.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pico.spatial.ui.design.ButtonColors
import com.pico.spatial.ui.design.ButtonDefaults

/** App-owned moss/stone palette requested by the user; native tokens stay intact. */
object TempleColors {
    val templeSurface = Color(0xFF142D26) // design-style: fixed-figma-color user-requested game palette; reference art/selection-page-V0.5.0.png
    val templeControl = Color(0xFF2E4D40) // design-style: fixed-figma-color user-requested game palette; reference art/selection-page-V0.5.0.png
    val templeGold = Color(0xFFD7B56D) // design-style: fixed-figma-color user-requested game palette; reference art/selection-page-V0.5.0.png
    val templeTextPrimary = Color(0xFFF5EDD8) // design-style: fixed-figma-color user-requested game palette; reference art/selection-page-V0.5.0.png
    val templeTextSecondary = Color(0xFFD0DBCB) // design-style: fixed-figma-color user-requested game palette; reference art/selection-page-V0.5.0.png
}

@Composable
fun templeButtonColors(primary:Boolean=false):ButtonColors=ButtonDefaults.buttonColors(
    containerColor=if(primary)TempleColors.templeGold else TempleColors.templeControl,
    contentColor=if(primary)TempleColors.templeSurface else TempleColors.templeTextPrimary
)
