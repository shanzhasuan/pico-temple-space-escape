package com.shanzhasuan.templesprint.game

import kotlin.math.*

enum class Phase { READY, RUNNING, PAUSED, ENDED, CLEARED }
enum class Move { LEFT, RIGHT, JUMP, SLIDE }
enum class Event { START, LANE, JUMP, SLIDE, LAND, COIN, TURN, STUMBLE, CAUGHT, HIT, PAUSE, CHAPTER, CLEAR }
enum class ObstacleKind { LOW, HIGH, BLOCK, FIRE, GAP }
enum class ObstacleSkin { ROOT, FALLEN_COLUMN, STONE_ARCH, STONE_BLOCK, FIRE_TRAP, BROKEN_ROAD }
data class Obstacle(val id:Int,val distance:Float,val lane:Int,val kind:ObstacleKind,val fullWidth:Boolean=false,
    val skin:ObstacleSkin=when(kind){ObstacleKind.LOW->ObstacleSkin.ROOT;ObstacleKind.HIGH->ObstacleSkin.STONE_ARCH;ObstacleKind.BLOCK->ObstacleSkin.STONE_BLOCK;ObstacleKind.FIRE->ObstacleSkin.FIRE_TRAP;ObstacleKind.GAP->ObstacleSkin.BROKEN_ROAD},val length:Float=if(kind==ObstacleKind.GAP)1.8f else .6f) {
    fun covers(x:Float)=fullWidth||abs(x-lane*RunnerGame.LANE_WIDTH)<.62f
}
data class Coin(val id:Int,val distance:Float,val lane:Int,val y:Float=0.9f)
data class TrackPose(val x:Float,val z:Float,val yaw:Float)
data class DeckSpan(val start:Float,val end:Float){val center get()=(start+end)/2;val length get()=end-start}

/** Deterministic route and swept crossing checks; simulation is independent of headset tracking. */
class RunnerGame(private val emit:(Event)->Unit = {}) {
    var phase=Phase.READY; private set
    var distance=0f; private set
    var elapsed=0f; private set
    var lane=0; private set
    var laneX=0f; private set
    var coins=0; private set
    var reason=""; private set
    var jumpTime=-1f; private set
    var slideTime=-1f; private set
    var queuedTurn=0; private set
    var completedTurns=0; private set
    private var activeTurnLeg=-1
    private var activeTurnRadius=0f
    var comfortPace=false
    var selectedChapter=0;private set
    private var runStartDistance=0f
    private var announcedChapter=0
    private var lastPruneDistance=-1f
    var stumbleWindow=0f;private set
    var pursuerGap=2.4f;private set
    var caught=false;private set
    private var lastStumble=-10f
    private var bufferedAction:Move?=null
    private var actionExpires=0f
    val pursuerClose get()=stumbleWindow>0f&&phase==Phase.RUNNING&&!caught
    val footstepInterval get()=1f/(2.8f+speed*.16f)
    val chapter get()=Chapter.at(distance)
    val chapterProgress get()=((distance-chapter.start)/(chapter.end-chapter.start)).coerceIn(0f,1f)
    val runDistance get()=(distance-runStartDistance).coerceAtLeast(0f)
    val turning get()=activeTurnLeg>=0
    val turnProgress get()=if(turning)((distance-((activeTurnLeg+1)*LEG-activeTurnRadius))/(2f*activeTurnRadius)).coerceIn(0f,1f) else 0f
    val speed get()=paceAt(distance)
    val score get()=runDistance.toInt()+coins*25
    val jumping get()=jumpTime>=0f
    val sliding get()=slideTime>=0f
    val jumpHeight get()=if(jumping)sin(PI.toFloat()*jumpTime/JUMP_DURATION)*1.05f else 0f
    val turnDistance get()=if(turning)((activeTurnLeg+1)*LEG-distance).coerceAtLeast(0f) else LEG-(distance%LEG)
    val requiredTurn get()=turnSign(if(turning)activeTurnLeg else (distance/LEG).toInt())
    val obstacles= mutableListOf<Obstacle>()
    val coinLine= mutableListOf<Coin>()
    private val collected= mutableSetOf<Int>()
    private val origins= mutableListOf(TrackPose(0f,0f,0f))
    init { ensureRoute(4) }

    fun start() {
        reset(Phase.RUNNING);emit(Event.START)
    }
    fun chooseChapter(index:Int){selectedChapter=index.coerceIn(0,Chapter.entries.lastIndex);reset(Phase.READY)}
    private fun reset(state:Phase){
        runStartDistance=Chapter.entries[selectedChapter].start
        phase=state;distance=runStartDistance;elapsed=0f;lane=0;laneX=0f;coins=0
        reason="";jumpTime=-1f;slideTime=-1f;queuedTurn=0;completedTurns=0;activeTurnLeg=-1;activeTurnRadius=0f
        announcedChapter=selectedChapter
        stumbleWindow=0f;pursuerGap=2.4f;caught=false;lastStumble=-10f
        bufferedAction=null;actionExpires=0f
        collected.clear();obstacles.clear();coinLine.clear();origins.clear();origins+=TrackPose(0f,0f,0f);ensureRoute((distance/LEG).toInt()+4)
        pruneHistory();lastPruneDistance=distance
    }
    fun pause(){if(phase==Phase.RUNNING){phase=Phase.PAUSED;emit(Event.PAUSE)}}
    fun resume(){if(phase==Phase.PAUSED)phase=Phase.RUNNING}
    fun move(move:Move) {
        if(phase!=Phase.RUNNING)return
        if(turning&&move !in listOf(Move.LEFT,Move.RIGHT))return
        if(turning&&distance>(activeTurnLeg+1)*LEG+TURN_CONFIRM_MARGIN)return
        when(move){
            Move.LEFT,Move.RIGHT->{
                val sign=if(move==Move.LEFT)-1 else 1
                if(turning||turnDistance<=12f){queuedTurn=sign;emit(Event.LANE)}
                else {val next=(lane+sign).coerceIn(-1,1);if(next!=lane){lane=next;emit(Event.LANE)}else stumble()}
            }
            Move.JUMP,Move.SLIDE->{
                if(!jumping&&!sliding)startAction(move)
                else {val remaining=if(jumping)JUMP_DURATION-jumpTime else SLIDE_DURATION-slideTime
                    if(remaining<=.18f){bufferedAction=move;actionExpires=elapsed+.20f}}
            }
        }
    }
    private fun startAction(move:Move){if(move==Move.JUMP){jumpTime=0f;emit(Event.JUMP)}else{slideTime=0f;emit(Event.SLIDE)}}
    fun tick(rawDt:Float) {
        if(phase!=Phase.RUNNING)return
        // Substeps preserve jump timing and collision checks across slow render frames.
        var remaining=rawDt.coerceIn(0f,.25f)
        while(remaining>0f&&phase==Phase.RUNNING){val dt=min(remaining,1f/90f);step(dt);remaining-=dt}
        if(distance-lastPruneDistance>=32f){pruneHistory();lastPruneDistance=distance}
    }
    private fun step(dt:Float) {
        elapsed+=dt
        stumbleWindow=(stumbleWindow-dt).coerceAtLeast(0f)
        val targetGap=if(pursuerClose)1.9f else if(elapsed<2.5f)2.4f else 6.5f
        pursuerGap+=(targetGap-pursuerGap)*(1f-exp(-dt/1.1f))
        val centering=turning||(turnDistance<=10f&&queuedTurn==requiredTurn)
        if(centering)lane=0
        laneX+=(lane*LANE_WIDTH-laneX)*(1f-exp(-dt/(if(centering).35f else .13f)))
        if(jumping){jumpTime+=dt;if(jumpTime>=JUMP_DURATION){jumpTime=-1f;emit(Event.LAND)}}
        if(sliding){slideTime+=dt;if(slideTime>=SLIDE_DURATION)slideTime=-1f}
        bufferedAction?.let{action->if(elapsed>actionExpires){bufferedAction=null}else if(!jumping&&!sliding){bufferedAction=null;if(!turning)startAction(action)}}
        val previous=distance
        advance(speed*dt)
        if(phase!=Phase.RUNNING)return
        ensureRoute((distance/LEG).toInt()+3)
        for(o in obstacles){
            if(o.kind==ObstacleKind.GAP){
                if(distance>=o.distance-o.length/2&&previous<o.distance+o.length/2&&o.covers(laneX)&&jumpHeight<.30f){end("踩空断路，提前跳过缺口");return}
            }else if(o.distance>previous&&o.distance<=distance&&o.covers(laneX)){
                val safe=when(o.kind){ObstacleKind.LOW,ObstacleKind.FIRE->jumpHeight>.48f;ObstacleKind.HIGH->sliding;ObstacleKind.BLOCK->false;ObstacleKind.GAP->true}
                if(!safe){end(when(o.kind){ObstacleKind.LOW->if(o.skin==ObstacleSkin.ROOT)"撞到倒木，提前跳跃" else "撞到石柱，提前跳跃";ObstacleKind.HIGH->"撞到石门，提前滑铲";ObstacleKind.BLOCK->"前方石块堵塞，左右换道";ObstacleKind.FIRE->"触碰火焰，提前跳跃";ObstacleKind.GAP->"踩空断路，提前跳跃"});return}
            }else if(o.kind==ObstacleKind.BLOCK&&o.distance>previous&&o.distance<=distance&&abs(laneX-o.lane*LANE_WIDTH)<.82f){
                stumble();if(phase!=Phase.RUNNING)return
            }
        }
        for(c in coinLine){
            if(c.distance>previous&&c.distance<=distance&&abs(laneX-c.lane*LANE_WIDTH)<.55f&&abs((if(sliding).45f else .9f)+jumpHeight-c.y)<.65f&&collected.add(c.id)){coins++;emit(Event.COIN)}
        }
        if(chapter.ordinal>announcedChapter){announcedChapter=chapter.ordinal;emit(Event.CHAPTER)}
        if(distance>=FINISH_DISTANCE){phase=Phase.CLEARED;reason="已冲出神庙";emit(Event.CLEAR)}
    }
    /** Spend a physical-distance budget, including partial entry/exit steps at the corner. */
    private fun advance(travel:Float){
        var left=travel
        while(left>0f){
            if(turning){
                val exit=(activeTurnLeg+1)*LEG+activeTurnRadius
                val inputDeadline=(activeTurnLeg+1)*LEG+TURN_CONFIRM_MARGIN
                val toDeadline=((inputDeadline-distance)*ARC_LENGTH_FACTOR).coerceAtLeast(0f)
                if(queuedTurn!=turnSign(activeTurnLeg)&&left>=toDeadline){
                    distance=inputDeadline;end(if(queuedTurn==0)"路口没有转向，请按箭头左右拨动摇杆" else "路口方向不对，请按箭头转向");return
                }
                val remainingArc=((exit-distance)*ARC_LENGTH_FACTOR).coerceAtLeast(0f)
                if(left<remainingArc){distance+=left/ARC_LENGTH_FACTOR;left=0f}
                else{distance=exit;left-=remainingArc;completedTurns++;queuedTurn=0;activeTurnLeg=-1}
            }else{
                val leg=(distance/LEG).toInt();val radius=radiusAtCorner((leg+1)*LEG)
                val entrance=(leg+1)*LEG-radius
                val target=min(entrance,FINISH_DISTANCE)
                val straight=(target-distance).coerceAtLeast(0f)
                if(left<straight){distance+=left;left=0f}
                else{
                    distance=target;left-=straight
                    if(target==FINISH_DISTANCE)return
                    activeTurnLeg=leg;activeTurnRadius=radius;lane=0;emit(Event.TURN)
                }
            }
        }
    }
    private fun paceAt(at:Float):Float=if(comfortPace)(3.8f+(at-runStartDistance).coerceAtLeast(0f)/300f).coerceAtMost(5.4f) else (4.8f+(at-runStartDistance).coerceAtLeast(0f)/90f).coerceAtMost(8.5f)
    private fun radiusAtCorner(corner:Float):Float=paceAt(corner)*(if(comfortPace)1.8f else TURN_SECONDS)/(2f*ARC_LENGTH_FACTOR)
    private fun end(message:String){phase=Phase.ENDED;reason=message;emit(Event.HIT)}
    private fun stumble(){
        // A single physical edge press can arrive from both controllers. Do not
        // count those near-simultaneous callbacks as two independent mistakes.
        if(phase!=Phase.RUNNING||elapsed-lastStumble<.45f)return
        lastStumble=elapsed
        if(pursuerClose){caught=true;phase=Phase.ENDED;reason="连续失误，被神庙守卫抓住";pursuerGap=0f;emit(Event.CAUGHT)}
        else{stumbleWindow=6f;emit(Event.STUMBLE)}
    }
    fun coinVisible(id:Int)=id !in collected
    /** The renderer and gap collision use the same missing-floor interval. */
    fun deckSpans(from:Float,to:Float):List<DeckSpan>{
        var result=listOf(DeckSpan(from,to))
        for(gap in obstacles.filter{it.kind==ObstacleKind.GAP&&it.distance+it.length/2>from&&it.distance-it.length/2<to}){
            val a=gap.distance-gap.length/2;val b=gap.distance+gap.length/2
            result=result.flatMap{span->if(b<=span.start||a>=span.end)listOf(span)else buildList{
                if(a>span.start)add(DeckSpan(span.start,a));if(b<span.end)add(DeckSpan(b,span.end))
            }}
        }
        return result
    }
    fun pose(at:Float=distance):TrackPose {
        val leg=(at.coerceAtLeast(0f)/LEG).toInt();ensureRoute(leg+1)
        val p=origins[leg];val within=at.coerceAtLeast(0f)-leg*LEG
        val r=Math.toRadians(p.yaw.toDouble())
        return TrackPose(p.x-sin(r).toFloat()*within,p.z-cos(r).toFloat()*within,p.yaw)
    }
    /** Position and heading share one rounded path; both tangents meet the straight segments. */
    fun renderPose():TrackPose {
        if(!turning)return pose()
        val before=origins[activeTurnLeg];val corner=origins[activeTurnLeg+1]
        val a=Math.toRadians(before.yaw.toDouble());val b=Math.toRadians(corner.yaw.toDouble())
        val inX=-sin(a).toFloat();val inZ=-cos(a).toFloat()
        val outX=-sin(b).toFloat();val outZ=-cos(b).toFloat()
        // Invert integrated arc length: equal elapsed time advances equal ground distance.
        val t=curveParameter(turnProgress);val t3=t*t*t;val integral=t3-.5f*t3*t
        val alongIn=2f*activeTurnRadius*(t-integral)-activeTurnRadius
        val alongOut=2f*activeTurnRadius*integral
        val blend=smooth(t)
        val yaw=before.yaw-turnSign(activeTurnLeg)*Math.toDegrees(atan2(blend.toDouble(),(1f-blend).toDouble())).toFloat()
        return TrackPose(corner.x+inX*alongIn+outX*alongOut,corner.z+inZ*alongIn+outZ*alongOut,yaw)
    }
    fun renderYaw():Float=renderPose().yaw
    private fun smooth(t:Float)=t*t*(3f-2f*t)
    private fun curveParameter(progress:Float):Float{
        val target=progress*ARC_LENGTH_FACTOR
        var low=0;var high=ARC_SAMPLES
        while(high-low>1){val mid=(low+high)/2;if(ARC_TABLE[mid]<target)low=mid else high=mid}
        val fraction=((target-ARC_TABLE[low])/(ARC_TABLE[high]-ARC_TABLE[low])).coerceIn(0f,1f)
        return (low+fraction)/ARC_SAMPLES
    }
    private fun ensureRoute(through:Int) {
        while(origins.size<=through){
            val previous=origins.last();val i=origins.lastIndex;val r=Math.toRadians(previous.yaw.toDouble())
            origins+=TrackPose(previous.x-sin(r).toFloat()*LEG,previous.z-cos(r).toFloat()*LEG,previous.yaw-90f*turnSign(i))
        }
        // Route lists grow only on entering a leg; retain a short visible and testable horizon.
        val target=through*LEG
        var leg=if(obstacles.isEmpty())0 else (obstacles.last().distance/LEG).toInt()+1
        while(leg*LEG<target){
            val offsets=if(leg==0)listOf(14f,26f,38f,50f) else listOf(12f,20.5f,29f,37.5f,46f)
            for((j,offset) in offsets.withIndex()){val d=leg*LEG+offset
                val region=Chapter.atLeg(leg)
                val kind=if(leg==0)listOf(ObstacleKind.LOW,ObstacleKind.HIGH,ObstacleKind.BLOCK,ObstacleKind.LOW)[j] else when(region){
                    Chapter.JUNGLE->listOf(ObstacleKind.LOW,ObstacleKind.HIGH,ObstacleKind.BLOCK,ObstacleKind.LOW,ObstacleKind.HIGH)[j]
                    Chapter.CANYON->listOf(ObstacleKind.GAP,ObstacleKind.BLOCK,ObstacleKind.LOW,ObstacleKind.FIRE,ObstacleKind.HIGH)[j]
                    Chapter.CORRIDOR->listOf(ObstacleKind.HIGH,ObstacleKind.BLOCK,ObstacleKind.FIRE,ObstacleKind.HIGH,ObstacleKind.LOW)[j]
                    Chapter.SANCTUM->listOf(ObstacleKind.FIRE,ObstacleKind.HIGH,ObstacleKind.BLOCK,ObstacleKind.GAP,ObstacleKind.LOW)[j]
                }
                val obstacleLane=if(leg==0)0 else ((leg+j)%3)-1
                val skin=if(kind==ObstacleKind.LOW&&((leg+j)%2!=0||region!=Chapter.JUNGLE))ObstacleSkin.FALLEN_COLUMN else null
                val fullWidth=kind!=ObstacleKind.BLOCK&&!(leg==0&&j==0)
                val obstacle=Obstacle(leg*16+j*2,d,obstacleLane,kind,fullWidth)
                obstacles+=if(skin!=null)obstacle.copy(skin=skin)else obstacle
                val blocked=mutableListOf(obstacleLane)
                if(kind==ObstacleKind.BLOCK&&leg>=6){
                    val second=listOf(-1,0,1).first{it!=obstacleLane};blocked+=second
                    obstacles+=Obstacle(leg*16+j*2+1,d,second,kind)
                }
                val safeLane=if(kind==ObstacleKind.BLOCK)listOf(0,-1,1).first{it !in blocked} else if(fullWidth)0 else obstacleLane
                for(k in 0..3)coinLine+=Coin(leg*32+j*4+k,d-5f+k, safeLane)
            };leg++
        }
    }
    private fun pruneHistory(){if(distance>LEG*5){obstacles.removeAll{it.distance<distance-LEG*2};coinLine.removeAll{it.distance<distance-LEG*2}}}
    companion object {const val LEG=64f;const val LANE_WIDTH=.9f;const val JUMP_DURATION=.95f;const val SLIDE_DURATION=1.05f;const val TURN_SECONDS=1.15f;const val TURN_CONFIRM_MARGIN=1f;const val FINISH_DISTANCE=760f
        private const val ARC_SAMPLES=256
        private val ARC_TABLE=FloatArray(ARC_SAMPLES+1).apply{
            fun magnitude(t:Float):Float{val s=t*t*(3f-2f*t);return sqrt((1f-s)*(1f-s)+s*s)}
            for(i in 1..ARC_SAMPLES)this[i]=this[i-1]+(magnitude((i-1f)/ARC_SAMPLES)+magnitude(i.toFloat()/ARC_SAMPLES))/(2f*ARC_SAMPLES)
        }
        private val ARC_LENGTH_FACTOR=ARC_TABLE.last()
        fun turnSign(leg:Int)=if(leg%4 in 0..1)1 else -1
    }
}
