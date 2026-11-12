package com.shanzhasuan.templesprint

import com.shanzhasuan.templesprint.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.atan2
import java.io.File

class RunnerGameTest {
    private fun drive(game:RunnerGame,through:Float,turnCorrectly:Boolean=true,dt:Float=1f/90f,queueTurns:Boolean=true){
        var guard=0
        while(game.distance<through&&game.phase==Phase.RUNNING&&guard++<200000){
            val next=game.obstacles.firstOrNull{it.distance>game.distance||(it.kind==ObstacleKind.GAP&&it.distance+it.length/2>game.distance)}
            if(next!=null){val delta=next.distance-game.distance
                if(next.kind==ObstacleKind.BLOCK&&delta<game.speed*.9f){
                    val blocked=game.obstacles.filter{it.kind==ObstacleKind.BLOCK&&it.distance==next.distance}.map{it.lane}
                    val safe=listOf(game.lane,0,-1,1).first{it !in blocked}
                    repeat(abs(safe-game.lane)){game.move(if(safe<game.lane)Move.LEFT else Move.RIGHT)}
                }
                if(next.covers(game.laneX)){
                    if(next.kind in listOf(ObstacleKind.LOW,ObstacleKind.FIRE,ObstacleKind.GAP)&&delta<game.speed*(if(next.kind==ObstacleKind.GAP).43f else .36f)&&!game.jumping)game.move(Move.JUMP)
                    if(next.kind==ObstacleKind.HIGH&&delta<game.speed*.30f&&!game.sliding)game.move(Move.SLIDE)
                }
            }
            if(queueTurns&&game.turnDistance<10f&&game.queuedTurn==0){val sign=game.requiredTurn*(if(turnCorrectly)1 else -1);game.move(if(sign<0)Move.LEFT else Move.RIGHT)}
            game.tick(dt)
        }
    }
    @Test fun collisionEndsRun(){val g=RunnerGame();g.start();repeat(500){g.tick(.02f)};assertEquals(Phase.ENDED,g.phase);assertTrue(g.reason.contains("倒木"))}
    @Test fun jumpingClearsLowObstacle(){val g=RunnerGame();g.start();drive(g,18f);assertEquals(Phase.RUNNING,g.phase);assertTrue(g.distance>=18f)}
    @Test fun slidingClearsHighObstacle(){val g=RunnerGame();g.start();drive(g,30f);assertEquals(Phase.RUNNING,g.phase)}
    @Test fun correctTurnContinuesRoute(){val g=RunnerGame();g.start();drive(g,70f);assertEquals(Phase.RUNNING,g.phase);assertEquals(1,g.completedTurns);assertEquals(-90f,g.pose().yaw,.01f)}
    @Test fun wrongTurnEndsRun(){val g=RunnerGame();g.start();drive(g,70f,false);assertEquals(Phase.ENDED,g.phase);assertTrue(g.reason.contains("路口"))}
    @Test fun pauseFreezesSimulation(){val g=RunnerGame();g.start();g.tick(.1f);g.pause();val d=g.distance;repeat(300){g.tick(.1f)};assertEquals(d,g.distance,0f);g.resume();g.tick(.1f);assertTrue(g.distance>d)}
    @Test fun coarseFramesDoNotSkipObstacles(){val g=RunnerGame();g.start();repeat(80){g.tick(.25f)};assertEquals(Phase.ENDED,g.phase);assertTrue(g.distance in 14f..14.1f)}
    @Test fun coinsCountOnceAndRestartRestoresCourse(){val g=RunnerGame();g.start();drive(g,45f);assertTrue(g.coins>0);val score=g.score;g.pause();g.tick(.2f);assertEquals(score,g.score);g.start();assertEquals(0,g.coins);assertEquals(0f,g.distance,0f);assertEquals(14f,g.obstacles.first().distance,0f)}
    @Test fun longRunSurvivesGeneratedSegments(){val g=RunnerGame();g.start();drive(g,520f);assertEquals(Phase.RUNNING,g.phase);assertTrue(g.completedTurns>=8);g.start();drive(g,70f);assertEquals(Phase.RUNNING,g.phase)}

    @Test fun intersectionShowsPartialTurnInsteadOfSwitchingHeading(){
        val g=RunnerGame();g.start();drive(g,62f)
        assertTrue(g.turning);assertTrue(g.renderPose().yaw in -20f..-.1f)
        drive(g,64f);assertTrue(g.turning);assertEquals(-45f,g.renderPose().yaw,3f)
        assertEquals(0,g.completedTurns)
        drive(g,68f);assertFalse(g.turning);assertEquals(1,g.completedTurns);assertEquals(-90f,g.renderPose().yaw,.01f)
    }
    @Test fun turnPositionAndHeadingRemainContinuousAndAligned(){
        val g=RunnerGame();g.start();drive(g,60f)
        val trace=StringBuilder("elapsed,distance,x,z,yaw,speed,progress,physicalSpeed\n")
        var previous=g.renderPose();var sawTurn=false
        val dt=1f/90f
        while(g.distance<69f&&g.phase==Phase.RUNNING){
            val speed=g.speed;g.tick(dt);val p=g.renderPose()
            val dx=p.x-previous.x;val dz=p.z-previous.z
            assertTrue("position jumped at ${g.distance}",hypot(dx,dz)<=speed*dt*1.1f+.001f)
            assertTrue("turn slowed physical movement at ${g.distance}",hypot(dx,dz)>=speed*dt*.96f)
            assertTrue("heading jumped at ${g.distance}",abs(p.yaw-previous.yaw)<3f)
            if(g.turning){
                sawTurn=true
                val direction=Math.toDegrees(atan2(-dx.toDouble(),-dz.toDouble())).toFloat()
                assertEquals("heading must follow the path tangent",direction,p.yaw,2f)
            }
            trace.append("${g.elapsed},${g.distance},${p.x},${p.z},${p.yaw},${g.speed},${g.turnProgress},${hypot(dx,dz)/dt}\n")
            previous=p
        }
        assertTrue(sawTurn);assertEquals(Phase.RUNNING,g.phase)
        File("build/reports/turn-motion.csv").apply{parentFile?.mkdirs();writeText(trace.toString())}
    }
    @Test fun leftTurnAlsoPassesThroughIntermediateAngles(){
        val g=RunnerGame();g.start();drive(g,190.8f)
        assertTrue(g.turning);assertTrue(g.renderPose().yaw> -180f&&g.renderPose().yaw< -90f)
        drive(g,192f);assertEquals(-135f,g.renderPose().yaw,3f)
        drive(g,198f);assertEquals(Phase.RUNNING,g.phase);assertEquals(-90f,g.renderPose().yaw,.01f);assertEquals(3,g.completedTurns)
    }
    @Test fun pausingDuringTurnPreservesThePathAndResumesSmoothly(){
        val g=RunnerGame();g.start();drive(g,63.3f);assertTrue(g.turning)
        val before=g.renderPose();val progress=g.turnProgress;g.pause()
        repeat(100){g.tick(.2f)}
        assertEquals(before,g.renderPose());assertEquals(progress,g.turnProgress,0f)
        g.resume();g.tick(.01f);val after=g.renderPose()
        assertTrue(hypot(after.x-before.x,after.z-before.z)<.06f);assertTrue(g.turning)
    }
    @Test fun laneRecentersWithoutAnInstantSidewaysJump(){
        val g=RunnerGame();g.start();g.move(Move.RIGHT);drive(g,52.1f);g.move(Move.RIGHT)
        assertTrue(g.laneX>.8f)
        while(g.distance<69f&&g.phase==Phase.RUNNING){
            val previous=g.laneX;g.tick(1f/90f)
            assertTrue("lane offset snapped at ${g.distance}",abs(g.laneX-previous)<.04f)
        }
        assertEquals(Phase.RUNNING,g.phase);assertTrue(abs(g.laneX)<.01f)
    }
    @Test fun higherSpeedRunKeepsItsPaceThroughoutTheCorner(){
        val g=RunnerGame();g.start();drive(g,314f)
        var previous=g.renderPose();var sawTurn=false;val dt=1f/90f
        while(g.distance<326f&&g.phase==Phase.RUNNING){
            val expected=(4.8f+g.distance/90f).coerceAtMost(8.5f)
            assertEquals("corner must not impose a low speed cap",expected,g.speed,.001f)
            g.tick(dt);val current=g.renderPose()
            val actual=hypot(current.x-previous.x,current.z-previous.z)/dt
            assertTrue("corner physical speed dropped: $actual vs $expected",actual>=expected*.96f)
            assertTrue("corner physical speed surged: $actual vs $expected",actual<=expected*1.04f)
            if(g.turning)sawTurn=true
            previous=current
        }
        assertTrue(sawTurn);assertEquals(Phase.RUNNING,g.phase)
    }
    @Test fun allFourChaptersProgressAndFinishAtTheGate(){
        val events=mutableListOf<Event>();val g=RunnerGame{events+=it};g.start();drive(g,800f)
        assertEquals(Phase.CLEARED,g.phase);assertEquals(760f,g.distance,.01f)
        assertEquals(Chapter.SANCTUM,g.chapter);assertEquals(3,events.count{it==Event.CHAPTER});assertEquals(1,events.count{it==Event.CLEAR})
        val p=g.renderPose();repeat(40){g.tick(.2f)};assertEquals(p,g.renderPose());assertEquals(1,events.count{it==Event.CLEAR})
    }
    @Test fun chapterSelectionStartsAZeroScoreRunAtTheSelectedRegion(){
        val g=RunnerGame();g.chooseChapter(2)
        assertEquals(Phase.READY,g.phase);assertEquals(384f,g.distance,0f);assertEquals(0,g.score);assertEquals(Chapter.CORRIDOR,g.chapter)
        g.start();drive(g,410f);assertEquals(Phase.RUNNING,g.phase);g.start()
        assertEquals(384f,g.distance,0f);assertEquals(0,g.coins);assertEquals(0,g.score)
    }
    @Test fun corridorHasItsDistinctLowBeamSequence(){
        val g=RunnerGame();g.chooseChapter(2)
        val obstacles=g.obstacles.filter{it.distance in 384f..448f}
        assertEquals(listOf(ObstacleKind.HIGH,ObstacleKind.BLOCK,ObstacleKind.FIRE,ObstacleKind.HIGH,ObstacleKind.LOW),obstacles.distinctBy{it.distance}.map{it.kind})
    }
    @Test fun lastChapterCompletesWithoutRequiringAnExtraTurn(){
        val g=RunnerGame();g.chooseChapter(3);g.start();drive(g,800f)
        assertEquals(Phase.CLEARED,g.phase);assertEquals(2,g.completedTurns);assertEquals(184f,g.runDistance,.01f)
    }
    @Test fun comfortPaceIsSteadyDuringTheLongerTurn(){
        val g=RunnerGame();g.comfortPace=true;g.start();drive(g,59f)
        val dt=1f/90f;var previous=g.renderPose();var start=-1f;var end=-1f
        while(g.distance<69f&&g.phase==Phase.RUNNING){
            val expected=g.speed;g.tick(dt);val p=g.renderPose();val actual=hypot(p.x-previous.x,p.z-previous.z)/dt
            assertTrue(actual>=expected*.96f);assertTrue(actual<=expected*1.04f)
            if(g.turning&&start<0f)start=g.elapsed
            if(!g.turning&&start>=0f&&end<0f)end=g.elapsed
            previous=p
        }
        assertEquals(Phase.RUNNING,g.phase);assertTrue(g.speed<=5.4f);assertEquals(1.8f,end-start,.05f)
    }

    @Test fun allObstacleTypesAreReachableAndBothPacesHaveASurvivingRoute(){
        for(comfort in listOf(false,true)){
            val g=RunnerGame();g.comfortPace=comfort;g.start()
            val seen=mutableSetOf<ObstacleKind>()
            while(g.phase==Phase.RUNNING){seen+=g.obstacles.map{it.kind};drive(g,g.distance+32f,dt=1f/45f)}
            assertEquals("no impossible pattern at comfort=$comfort distance=${g.distance} reason=${g.reason}",Phase.CLEARED,g.phase)
            assertEquals(ObstacleKind.entries.toSet(),seen)
        }
    }
    @Test fun stayingInASideLaneCannotBypassEveryObstacle(){
        val g=RunnerGame();g.start();g.move(Move.RIGHT)
        repeat(600){g.tick(.02f)}
        assertEquals(Phase.ENDED,g.phase);assertTrue(g.reason.contains("石门"))
    }
    @Test fun aGapHasNoFloorAndStandingFailsBeforeItsCenter(){
        val g=RunnerGame();g.chooseChapter(1);g.start()
        val gap=g.obstacles.first{it.kind==ObstacleKind.GAP&&it.distance>g.distance}
        val spans=g.deckSpans(gap.distance-4f,gap.distance+4f)
        assertEquals(2,spans.size);assertEquals(gap.length,spans[1].start-spans[0].end,.001f)
        assertTrue(spans.none{gap.distance>=it.start&&gap.distance<=it.end})
        while(g.phase==Phase.RUNNING&&g.distance<gap.distance)g.tick(.02f)
        assertEquals(Phase.ENDED,g.phase);assertTrue(g.distance<gap.distance);assertTrue(g.reason.contains("断路"))
    }
    @Test fun correctJumpClearsTheWholeGapAtBothPaces(){
        for(comfort in listOf(false,true)){
            val g=RunnerGame();g.comfortPace=comfort;g.chooseChapter(1);g.start()
            val gap=g.obstacles.first{it.kind==ObstacleKind.GAP&&it.distance>g.distance}
            drive(g,gap.distance+2f)
            assertEquals(Phase.RUNNING,g.phase)
        }
    }
    @Test fun laterBlockingWavesAlwaysLeaveAnOpenLane(){
        val g=RunnerGame();g.chooseChapter(3)
        for(wave in g.obstacles.filter{it.kind==ObstacleKind.BLOCK}.groupBy{it.distance}.values){
            assertEquals(2,wave.size);assertEquals(2,wave.map{it.lane}.toSet().size)
        }
    }
    @Test fun speedRisesContinuouslyAndStopsAtTheSelectedCap(){
        for(comfort in listOf(false,true)){
            val g=RunnerGame();g.comfortPace=comfort;g.start();var previous=g.speed
            assertEquals(if(comfort)3.8f else 4.8f,previous,.001f)
            while(g.phase==Phase.RUNNING){drive(g,g.distance+8f);assertTrue(g.speed>=previous);previous=g.speed}
            assertEquals(Phase.CLEARED,g.phase);assertEquals(if(comfort)5.4f else 8.5f,g.speed,.001f)
        }
    }
    @Test fun coinAndObstacleIdsRemainUniqueWithDenserWaves(){
        val g=RunnerGame();g.chooseChapter(3)
        assertEquals(g.obstacles.size,g.obstacles.map{it.id}.toSet().size)
        assertEquals(g.coinLine.size,g.coinLine.map{it.id}.toSet().size)
    }
    @Test fun oneEdgeMistakeWarnsAndSecondMistakeInsideTheWindowGetsCaught(){
        val events=mutableListOf<Event>();val g=RunnerGame{events+=it};g.start();g.move(Move.RIGHT);g.tick(.25f);g.tick(.25f)
        g.move(Move.RIGHT);assertTrue(g.pursuerClose);assertFalse(g.caught);assertEquals(Phase.RUNNING,g.phase)
        repeat(3){g.tick(.2f)};g.move(Move.RIGHT)
        assertTrue(g.caught);assertEquals(Phase.ENDED,g.phase);assertTrue(g.reason.contains("守卫"));assertFalse(g.pursuerClose)
        assertEquals(1,events.count{it==Event.STUMBLE});assertEquals(1,events.count{it==Event.CAUGHT})
    }
    @Test fun duplicateControllerCallbacksDoNotCountAsTwoMistakes(){
        val g=RunnerGame();g.start();g.move(Move.LEFT);g.tick(.2f);g.move(Move.LEFT);g.move(Move.LEFT)
        assertEquals(Phase.RUNNING,g.phase);assertTrue(g.pursuerClose);assertFalse(g.caught)
    }
    @Test fun cleanRunningRecoversFromAStumbleAndRestartClearsPursuit(){
        val g=RunnerGame();g.start();g.move(Move.RIGHT);g.tick(.2f);g.move(Move.RIGHT)
        drive(g,45f);assertEquals(Phase.RUNNING,g.phase);assertFalse(g.pursuerClose)
        g.start();assertEquals(0f,g.stumbleWindow,0f);assertFalse(g.caught);assertEquals(2.4f,g.pursuerGap,.001f)
    }
    @Test fun pauseFreezesThePursuitWindowAndMonsterGap(){
        val g=RunnerGame();g.start();g.move(Move.RIGHT);g.tick(.2f);g.move(Move.RIGHT);g.pause()
        val window=g.stumbleWindow;val gap=g.pursuerGap;repeat(100){g.tick(.25f)}
        assertEquals(window,g.stumbleWindow,0f);assertEquals(gap,g.pursuerGap,0f)
    }
    @Test fun aJumpEmitsOneLandingAndRunningCadenceIncreasesWithSpeed(){
        val events=mutableListOf<Event>();val g=RunnerGame{events+=it};g.start();g.move(Move.JUMP)
        repeat(100){g.tick(.01f)};assertEquals(1,events.count{it==Event.LAND})
        repeat(20){g.tick(.01f)};assertEquals(1,events.count{it==Event.LAND})
        val start=g.footstepInterval;drive(g,320f)
        assertEquals(Phase.RUNNING,g.phase);assertTrue(g.footstepInterval<start);assertTrue(g.footstepInterval in .20f.. .34f)
    }
    @Test fun turningCanBeConfirmedAtTheVisibleCornerInsteadOfFourMetersEarly(){
        for(comfort in listOf(false,true)){
            val g=RunnerGame();g.comfortPace=comfort;g.start();drive(g,64.05f,queueTurns=false)
            assertEquals(Phase.RUNNING,g.phase);assertTrue(g.turning);assertEquals(0,g.queuedTurn)
            g.move(Move.RIGHT);drive(g,72f,queueTurns=false)
            assertEquals(Phase.RUNNING,g.phase);assertEquals(1,g.completedTurns)
        }
    }
    @Test fun missingTurnFailsOnlyAfterTheVisibleCornerAndGrace(){
        val g=RunnerGame();g.comfortPace=true;g.start();drive(g,63.9f,queueTurns=false)
        assertEquals(Phase.RUNNING,g.phase);drive(g,70f,queueTurns=false)
        assertEquals(Phase.ENDED,g.phase);assertEquals(65f,g.distance,.01f)
        assertFalse(g.reason.contains("提前"))
    }

    @Test fun failureNearTheCornerKeepsTheGuardianBehindTheActualViewDirection(){
        val g=RunnerGame();g.comfortPace=true;g.start();drive(g,70f,queueTurns=false)
        assertEquals(Phase.ENDED,g.phase)
        val player=g.renderPose();val guardian=PursuerPresentation.pose(g,2.4f)
        val a=Math.toRadians(player.yaw.toDouble());val dx=guardian.x-player.x;val dz=guardian.z-player.z
        assertEquals(-2.4f,(-kotlin.math.sin(a)*dx-kotlin.math.cos(a)*dz).toFloat(),.001f)
    }

}
