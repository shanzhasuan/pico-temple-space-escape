package com.shanzhasuan.templesprint.game

import kotlin.math.min

/** Smooth visible motion without a full-height camera jump in comfort mode. */
object RunnerMotion {
    fun height(jumpHeight:Float,slideTime:Float,comfort:Boolean):Float {
        if(slideTime>=0f){
            val edge=min(slideTime/.20f,(RunnerGame.SLIDE_DURATION-slideTime)/.20f).coerceIn(0f,1f)
            val smooth=edge*edge*(3f-2f*edge)
            return -(if(comfort).38f else .55f)*smooth
        }
        return jumpHeight*(if(comfort).22f/1.05f else 1f)
    }
}
