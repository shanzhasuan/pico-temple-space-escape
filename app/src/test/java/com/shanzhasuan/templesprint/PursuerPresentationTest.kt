package com.shanzhasuan.templesprint

import com.shanzhasuan.templesprint.game.*
import org.junit.Assert.*
import org.junit.Test

class PursuerPresentationTest {
    @Test fun ordinaryFailureKeepsThePursuerUntilLeavingTheResult(){
        val g=RunnerGame();g.start();repeat(200){g.tick(.1f)}
        assertEquals(Phase.ENDED,g.phase);assertFalse(g.caught)
        repeat(200){g.tick(.25f);assertTrue(PursuerPresentation.visible(g.phase))}
        g.chooseChapter(0);assertFalse(PursuerPresentation.visible(g.phase))
    }
    @Test fun aStoppedPursuerIsBehindThePlayerAndClearlyWithinViewDistance(){
        val g=RunnerGame();g.start();repeat(200){g.tick(.1f)}
        val player=g.renderPose();val gap=PursuerPresentation.defeatGap(g.pursuerGap,30f)
        val pursuer=PursuerPresentation.pose(g,gap)
        assertEquals(2.4f,gap,.001f);assertEquals(player.x,pursuer.x,.001f)
        assertTrue("forward is -Z, so pursuer must remain behind",pursuer.z>player.z)
        assertEquals(2.4f,pursuer.z-player.z,.001f)
    }
    @Test fun caughtFailureDoesNotPutTheModelInsideThePlayerAndClearDoesNotShowIt(){
        assertEquals(2.4f,PursuerPresentation.defeatGap(0f,0f),.001f)
        assertFalse(PursuerPresentation.visible(Phase.CLEARED));assertFalse(PursuerPresentation.visible(Phase.PAUSED))
    }
}
