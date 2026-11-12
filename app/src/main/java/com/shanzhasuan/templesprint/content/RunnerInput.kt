package com.shanzhasuan.templesprint.content

import com.pico.spatial.tracking.controller.ControllerAction
import com.pico.spatial.tracking.controller.ControllerActionData
import java.util.concurrent.ConcurrentLinkedQueue

enum class Command { LEFT, RIGHT, JUMP, SLIDE, PAUSE, CALIBRATE }

/** Controller callbacks run on the data-source thread. No Entity or UI writes here. */
class RunnerInput {
    val queue=ConcurrentLinkedQueue<Command>()
    private val previous=arrayOf(Edge(),Edge())
    private class Edge {var stickArmed=true;val jump=JumpPressEdges();var slide=false;var pause=false}
    @Synchronized fun observe(data:ControllerActionData){sample(data.left,previous[0]);sample(data.right,previous[1])}
    private fun sample(a:ControllerAction,e:Edge) {
        val x=a.thumbstickValue.x;val y=a.thumbstickValue.y
        if(kotlin.math.abs(x)<.25f&&kotlin.math.abs(y)<.25f)e.stickArmed=true
        if(e.stickArmed&&maxOf(kotlin.math.abs(x),kotlin.math.abs(y))>.65f){
            queue.add(if(kotlin.math.abs(x)>kotlin.math.abs(y)){if(x<0)Command.LEFT else Command.RIGHT}else{if(y>0)Command.JUMP else Command.SLIDE});e.stickArmed=false
        }
        val jump=a.jumpButtons(e)
        val slide=a.gripPressed
        val pause=a.bButtonPressed||a.yButtonPressed
        if(jump!=0){queue.add(Command.JUMP);android.util.Log.i("RunnerInput","jump pressed source=${when{jump and 2!=0->"A";jump and 4!=0->"X";else->"trigger"}}")}
        if(slide&&!e.slide)queue.add(Command.SLIDE)
        if(pause&&!e.pause)queue.add(Command.PAUSE)
        e.slide=slide;e.pause=pause
    }
    private fun ControllerAction.jumpButtons(edge:Edge)=edge.jump.pressed(triggerPressed,aButtonPressed,xButtonPressed)
}
