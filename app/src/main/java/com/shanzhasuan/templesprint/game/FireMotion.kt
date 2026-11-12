package com.shanzhasuan.templesprint.game

import kotlin.math.*

/** Small continuous 3D motions, independent of runner/camera movement. */
object FireMotion {
    const val UPDATE_INTERVAL=1f/24f
    data class Tongue(val sway:Float,val lift:Float,val yaw:Float,val width:Float,val height:Float)
    data class Spark(val x:Float,val y:Float,val z:Float,val scale:Float)
    fun tongue(time:Float,fireId:Int,index:Int):Tongue {
        val phase=time*5.4f+fireId*.37f+index*1.31f
        return Tongue(.055f*sin(phase*1.13f),.06f+.035f*sin(phase),
            index*67f+18f*sin(phase*.71f),.9f+.16f*sin(phase+.9f),.92f+.24f*sin(phase))
    }
    fun spark(time:Float,fireId:Int,index:Int):Spark {
        val cycle=time*.78f+fireId*.117f+index*.29f
        val phase=cycle-floor(cycle)
        val size=sin(PI.toFloat()*phase).coerceAtLeast(0f)
        return Spark((index-1.5f)*.42f+.15f*sin(cycle*2f),.15f+phase*1.2f,.09f*cos(cycle*3f),size)
    }
}
