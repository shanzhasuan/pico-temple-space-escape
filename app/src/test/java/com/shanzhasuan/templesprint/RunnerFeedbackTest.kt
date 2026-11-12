package com.shanzhasuan.templesprint

import com.shanzhasuan.templesprint.content.JumpPressEdges
import com.shanzhasuan.templesprint.game.*
import org.junit.Assert.*
import org.junit.Test

class RunnerFeedbackTest {
    @Test fun aPressIsNotSwallowedByAHeldTrigger(){
        val edges=JumpPressEdges();assertEquals(1,edges.pressed(true,false,false))
        assertEquals(2,edges.pressed(true,true,false));assertEquals(0,edges.pressed(true,true,false))
        edges.pressed(true,false,false);assertEquals(2,edges.pressed(true,true,false))
    }
    @Test fun heldButtonsDoNotRepeatAndSimultaneousPressesHaveOneMask(){
        val edges=JumpPressEdges();assertEquals(7,edges.pressed(true,true,true))
        repeat(20){assertEquals(0,edges.pressed(true,true,true))}
        edges.pressed(false,false,false);assertEquals(4,edges.pressed(false,false,true))
    }
    @Test fun comfortJumpHasVisibleButBoundedCameraMovement(){
        assertEquals(0f,RunnerMotion.height(0f,-1f,true),0f)
        assertEquals(.22f,RunnerMotion.height(1.05f,-1f,true),.001f)
        assertEquals(1.05f,RunnerMotion.height(1.05f,-1f,false),.001f)
    }
    @Test fun slideStartsAndEndsSmoothlyAndLowersTheEyeBelowTheBeam(){
        assertEquals(0f,RunnerMotion.height(0f,0f,true),.001f)
        assertEquals(0f,RunnerMotion.height(0f,RunnerGame.SLIDE_DURATION,true),.001f)
        val middle=RunnerMotion.height(0f,.4f,true)
        assertTrue(1.55f+middle<1.25f)
        assertTrue(kotlin.math.abs(RunnerMotion.height(0f,.01f,true))<.01f)
    }
    @Test fun aSingleSlidePressNearJumpEndIsBuffered(){
        val events=mutableListOf<Event>();val g=RunnerGame{events+=it};g.start();g.move(Move.JUMP)
        repeat(80){g.tick(.01f)};g.move(Move.SLIDE);assertFalse(g.sliding)
        repeat(18){g.tick(.01f)};assertTrue(g.sliding);assertFalse(g.jumping);assertEquals(1,events.count{it==Event.SLIDE})
    }
    @Test fun anEarlyLockedActionDoesNotBecomeAnUnwantedLaterMove(){
        val g=RunnerGame();g.start();g.move(Move.JUMP);g.tick(.1f);g.move(Move.SLIDE)
        repeat(90){g.tick(.01f)};assertFalse(g.sliding)
    }
}
