package com.shanzhasuan.templesprint

import com.shanzhasuan.templesprint.game.*
import org.junit.Assert.*
import org.junit.Test

class RunnerNavigationTest {
    @Test fun selectingAChapterDoesNotStartGameplay(){
        val g=RunnerGame();val n=RunnerNavigation(g);n.select(2)
        assertEquals(RunnerPage.SELECT,n.page);assertEquals(384f,g.distance,0f)
        repeat(40){g.tick(.2f)};assertEquals(384f,g.distance,0f);assertEquals(0f,g.elapsed,0f);assertEquals(0,g.score)
    }
    @Test fun startingIsBlockedUntilResourcesExist(){
        val g=RunnerGame();val n=RunnerNavigation(g);n.select(1)
        assertFalse(n.enter());assertEquals(RunnerPage.SELECT,n.page);assertEquals(Phase.READY,g.phase)
    }
    @Test fun finishingEntryStartsOnlyTheSelectedChapterOnce(){
        val events=mutableListOf<Event>();val g=RunnerGame{events+=it};val n=RunnerNavigation(g)
        n.markResourcesReady();n.select(3);assertTrue(n.enter());assertEquals(RunnerPage.ENTERING,n.page)
        assertFalse(n.enter());g.tick(.2f);assertEquals(Phase.READY,g.phase);assertEquals(0f,g.elapsed,0f)
        n.finishEntry();n.finishEntry();assertEquals(RunnerPage.GAME,n.page);assertEquals(Phase.RUNNING,g.phase)
        assertEquals(576f,g.distance,0f);assertEquals(1,events.count{it==Event.START})
    }
    @Test fun returningToSelectionStopsMovementAndRetainsTheChoice(){
        val g=RunnerGame();val n=RunnerNavigation(g);n.markResourcesReady();n.select(1);n.enter();n.finishEntry();g.tick(.2f)
        n.returnToSelection();assertEquals(RunnerPage.SELECT,n.page);assertEquals(Phase.READY,g.phase);assertEquals(1,g.selectedChapter)
        assertEquals(192f,g.distance,0f);assertEquals(0,g.score);g.tick(.2f);assertEquals(192f,g.distance,0f)
    }
    @Test fun endingAndReplayingUsesTheChosenStartingPoint(){
        val g=RunnerGame();val n=RunnerNavigation(g);n.markResourcesReady();n.enter();n.finishEntry()
        repeat(200){g.tick(.1f)};assertEquals(Phase.ENDED,g.phase)
        assertTrue(n.enter());assertEquals(RunnerPage.ENTERING,n.page);assertEquals(0f,g.distance,0f);assertEquals(0,g.score)
        n.finishEntry();assertEquals(Phase.RUNNING,g.phase)
    }
    @Test fun aMenuSelectionCannotReplaceAnActiveRun(){
        val g=RunnerGame();val n=RunnerNavigation(g);n.markResourcesReady();n.enter();n.finishEntry();g.tick(.2f)
        val distance=g.distance;n.select(3);assertEquals(distance,g.distance,0f);assertEquals(0,g.selectedChapter);assertFalse(n.enter())
    }
}
