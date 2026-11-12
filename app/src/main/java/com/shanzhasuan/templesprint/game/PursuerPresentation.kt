package com.shanzhasuan.templesprint.game

import kotlin.math.sin
import kotlin.math.cos

object PursuerPresentation {
    fun visible(phase:Phase)=phase==Phase.RUNNING||phase==Phase.ENDED
    fun defeatGap(initial:Float,time:Float):Float {
        val start=initial.coerceAtLeast(2.4f);val t=(time/.8f).coerceIn(0f,1f)
        return start+(2.4f-start)*t*t*(3f-2f*t)
    }
    fun pose(game:RunnerGame,gap:Float):TrackPose {
        val player=game.renderPose();val angle=Math.toRadians(player.yaw.toDouble())
        return TrackPose(player.x+sin(angle).toFloat()*gap,player.z+cos(angle).toFloat()*gap,player.yaw)
    }
}
