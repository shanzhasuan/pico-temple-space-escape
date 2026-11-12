package com.shanzhasuan.templesprint.content

/** Each jump button has its own rising edge, even while another stays held. */
class JumpPressEdges {
    private var previous=0
    fun pressed(trigger:Boolean,a:Boolean,x:Boolean):Int {
        val current=(if(trigger)1 else 0)or(if(a)2 else 0)or(if(x)4 else 0)
        val rising=current and previous.inv();previous=current;return rising
    }
}
