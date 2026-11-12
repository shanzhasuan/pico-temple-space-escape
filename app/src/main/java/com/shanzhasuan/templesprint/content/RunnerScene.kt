package com.shanzhasuan.templesprint.content

import android.content.Context
import android.graphics.*
import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.System as SpatialSystem
import com.pico.spatial.core.ecs.animation.AnimationPlayConfig
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.shanzhasuan.templesprint.game.*
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.math.*

data class RunnerUi(val ready:Boolean=false,val error:Boolean=false,val phase:Phase=Phase.READY,val distance:Int=0,val coins:Int=0,val score:Int=0,val best:Int=0,val hint:String="",val reason:String="",val comfort:Boolean=true,val chapter:Int=0,val selectedChapter:Int=0,val speed:Float=0f,val pursuerClose:Boolean=false,val caught:Boolean=false,val page:RunnerPage=RunnerPage.SELECT,val showMenu:Boolean=true,val showThreat:Boolean=false)

class RunnerSystem : SpatialSystem() {
    override fun update(context:SceneUpdateContext){active?.let{if(it.ownsScene(context.scene))it.frame(context.deltaTime)}}
    companion object{@Volatile var active:RunnerScene?=null}
}

/** Owns static GPU batches, one animated body, and a head-relative comfort overlay. */
class RunnerScene(private val context:Context,private val publish:(RunnerUi)->Unit) {
    val input=RunnerInput()
    val placement=RunnerPlacement()
    private val seat=node("RunnerSeat")
    private val world=node("MovingTemple")
    private val prefs=context.getSharedPreferences("temple-runner",Context.MODE_PRIVATE)
    private var comfort=prefs.getBoolean("comfort",true)
    private val audio=RunnerAudio(context)
    private val game:RunnerGame=RunnerGame(::onGameEvent).apply{comfortPace=comfort}
    private val navigation=RunnerNavigation(game)
    private var head:HMDPose?=null
    private var panels:SpatialViewAttachments?=null
    private val positionedPanels=mutableMapOf<String,Long>()
    private var selectionPhysicalScale=1f
    private var ready=false
    private var openingTime=0f
    private var opening:OpeningView?=null
    private var failed=false
    private var closed=false
    private val prototypes=mutableMapOf<String,Entity>()
    private val batches=mutableMapOf<String,InstanceBatch>()
    private val lengths=mutableMapOf<String,Float>()
    private val ownedResources=mutableListOf<AutoCloseable>()
    private var body:Entity?=null
    private var guardian:Entity?=null
    private val guardianResources=mutableMapOf<Entity,List<AnimationResource>>()
    private val guardianControllers=mutableListOf<AnimationPlaybackController>()
    private var guardianClip=""
    private var guardianSpeed=-1f
    private var defeatTime=0f
    private var sky:Entity?=null
    private var menuBackdrop:Entity?=null
    private var selectionRoom:SelectionRoom?=null
    private var mask:ComfortView?=null
    private var fireTime=0f
    private var fireAccumulator=0f
    private var fireUpdates=0
    private var fireBaseHeight=.09f
    private val modelFaces=mutableMapOf<String,Int>()
    private var layoutKey=-1
    private var stepTime=0f
    private var publishTime=0f
    private var saved=false
    private var skipMotionFrame=false
    private var clip=""
    private val animationControllers=mutableListOf<AnimationPlaybackController>()
    private val animationResources=mutableMapOf<Entity,List<AnimationResource>>()

    fun ownsScene(scene:Scene)=seat.scene==scene
    fun observeHead(value:HMDPose){placement.observe(value);val p=value.position;val q=value.rotation;val n=q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w;if(p.x.isFinite()&&p.y.isFinite()&&p.z.isFinite()&&n in .5f..1.5f)head=value}
    private fun onGameEvent(event:Event){
        if(event==Event.START||event==Event.CHAPTER)audio.setRegion(game.chapter.ordinal)
        audio.event(event)
        Log.i("RunnerGame","event=$event distance=${if(ready)game.distance else 0f} chapter=${if(ready)game.chapter.ordinal+1 else 1}")
    }

    suspend fun load(view:SpatialViewContent,attachments:SpatialViewAttachments){
        view.addEntity(seat);seat.addChild(world);panels=attachments
        try{
            world.enabled=false;makeMenuBackdrop();attachPanels(view,attachments)
            makeSky()
            seat.addChild(node("TempleSun").apply{components.set(DirectionalLightComponent(Color4(1f,.95f,.82f,1f),2000f,false));transform(this).setEulerAngles(EulerAngles(-45f,30f,0f))})
            val info=JSONObject(context.assets.open("models/asset-info.json").bufferedReader().use{it.readText()})
            val geometry=JSONObject(context.assets.open("models/geometry-budget.json").bufferedReader().use{it.readText()})
            for(name in geometry.keys())modelFaces[name]=geometry.getInt(name)
            modelFaces["flame"]=modelFaces.getValue("flame_tongue")
            val names=listOf("path","path_open","gate","log","guardian","hero_fp","jungle","bridge","corridor","stone_block","stone_beam","fallen_column","flame_tongue","gold_gate","brazier","low_arch")
            for(name in names){
                val raw=Entity.loadSuspend("asset://models/$name.glb");coroutineContext.ensureActive()
                if(closed){raw.destroy();return}
                val wrapper=node("Prototype_$name");world.addChild(wrapper);wrapper.addChild(raw)
                val b=raw.getVisualBounds(wrapper);check(b.size.x>0&&b.size.y>0&&b.size.z>0){"Empty $name bounds"}
                val target=when(name){"path","path_open","bridge","corridor"->3.5f;"gate","gold_gate"->12.5f;"log"->1.15f;"brazier"->.85f;"guardian"->1.6f;"jungle"->3.8f;"flame_tongue"->.65f;else->1.75f}
                val dimension=when(name){"path","path_open","bridge","corridor","gate","gold_gate","log","brazier"->b.size.x;"hero_fp"->info.getJSONObject("hero").let{it.getJSONArray("max").getDouble(1).toFloat()-it.getJSONArray("min").getDouble(1).toFloat()};else->b.size.y}
                val scale=if(name in listOf("stone_block","stone_beam","fallen_column","low_arch"))1f else target/dimension;transform(raw).setScaleVector(Vector3(scale,scale,scale))
                val scaled=raw.getVisualBounds(wrapper)
                val floor=if(name in listOf("path","path_open","bridge"))info.getJSONObject(if(name=="path_open")"path" else name).getDouble("deckY").toFloat()*scale else scaled.min.y
                transform(raw).setPosition(Vector3(if(name=="low_arch")0f else -scaled.center.x,-floor,-scaled.center.z))
                if(name in listOf("path","path_open","bridge")){lengths[name]=scaled.size.z;check(scaled.size.z in 2.5f..24f)}
                if(name=="brazier")fireBaseHeight=scaled.size.y*.68f
                disableGroundShadows(wrapper)
                if(name!="hero_fp")useBakedMaterials(wrapper)
                prototypes[name]=wrapper;wrapper.enabled=false
                Log.i("RunnerAssets","model=$name source=${if(name=="flame_tongue")"local animated geometry" else "SGS"} scale=$scale boundsOwner=Prototype_$name size=${raw.getVisualBounds(wrapper).size}")
            }
            for(name in names.filter{it!="hero_fp"&&it!="guardian"&&it!="flame_tongue"})batches[name]=InstanceBatch(world,prototypes.getValue(name),name)
            val gold=UnlitMaterial.create().apply{setBaseColor(Color4(.98f,.68f,.08f,1f))};ownedResources+=gold
            val coinMesh=MeshResource.createCylinder(.055f,.13f);ownedResources+=coinMesh
            val coinSource=node("Prototype_coin").apply{components.set(ModelComponent(coinMesh,gold))};world.addChild(coinSource);coinSource.enabled=false;prototypes["coin"]=coinSource
            batches["coin"]=InstanceBatch(world,coinSource,"coin")
            makeFirePrototype()
            body=prototypes.remove("hero_fp")!!.also{seat.addChild(it);transform(it).setPosition(Vector3(0f,0f,.12f));transform(it).setEulerAngles(EulerAngles(0f,180f,0f));prepareAnimations(it);it.enabled=false}
            guardian=prototypes.remove("guardian")!!.also{world.addChild(it);prepareGuardian(it);it.enabled=false}
            mask=ComfortView(seat)
            selectionRoom=SelectionRoom(seat).also{it.create(prototypes,lengths.getValue("path_open"))}
            ready=true;navigation.markResourcesReady();publishState()
            Log.i("RunnerScene","lobby ready sdk=6.1.9 chapters=4 models=${names.size} gpuBatches=${batches.values.sumOf{it.meshBatchCount}} worldVisible=false page=SELECT comfort=$comfort")
        }catch(e:Exception){if(e is kotlinx.coroutines.CancellationException)throw e;failed=true;world.enabled=false;opening?.close();opening=null;Log.e("RunnerScene","initialization failed",e);publishState()}
    }
    private fun useBakedMaterials(root:Entity){
        root.components[ModelComponent::class.java]?.let{mc->for(i in 0 until mc.materials.size){
            val pbr=mc.materials[i] as? PhysicallyBasedMaterial?:continue
            val unlit=UnlitMaterial.create(pbr.getBlendingMode()).apply{
                setBaseColor(pbr.getBaseColor());runCatching{pbr.getBaseColorTexture()}.getOrNull()?.takeIf{it.valid}?.let{setBaseColorTexture(it)}
                setCullingMode(pbr.getCullingMode());setOpacity(pbr.getOpacity());setOpacityThreshold(pbr.getOpacityThreshold())
            }
            unlit.toGlobal();ownedResources+=unlit;mc.materials[i]=unlit
        }}
        root.getChildren().forEach{useBakedMaterials(it)}
    }
    private fun disableGroundShadows(root:Entity){if(root.components[ModelComponent::class.java]!=null)root.components.set(GroundShadowComponent(false,false));root.getChildren().forEach{disableGroundShadows(it)}}
    private fun copyGoldenGate(source:Entity):Entity{
        val copy=node("GoldenFinishGate");val src=transform(source)
        transform(copy).apply{setPosition(src.position);setQuaternion(src.quaternion);setScaleVector(src.scaleVector)}
        source.components[ModelComponent::class.java]?.let{model->
            val materials:Array<Material> = model.materials.map{m->
                val material=UnlitMaterial.create().apply{setBaseColor(Color4(1f,.85f,.5f,1f));if(m is UnlitMaterial)runCatching{m.getBaseColorTexture()}.getOrNull()?.takeIf{it.valid}?.let{setBaseColorTexture(it)}}
                material.toGlobal();ownedResources+=material;material
            }.toTypedArray()
            copy.components.set(ModelComponent(model.mesh,materials))
        }
        source.getChildren().forEach{copy.addChild(copyGoldenGate(it))};return copy
    }
    private fun prepareAnimations(entity:Entity){
        val targets=entity.findSkeletonRootEntity(includeDisabled=true).toList().ifEmpty{entity.findSkinnedMeshEntity(includeDisabled=true).toList()}
        check(targets.isNotEmpty()){"First-person body skeleton missing"}
        for(target in targets){val resources=target.getAnimationResources().toList();check(resources.isNotEmpty());animationResources[target]=resources;Log.i("RunnerAnimation","clips=${resources.map{it.getName()}}")}
    }
    private fun animate(name:String){
        if(name==clip)return;stopAnimations();clip=name
        for((entity,resources) in animationResources){val resource=resources.firstOrNull{it.getName().equals(name,true)}?:error("Missing $name");animationControllers+=entity.playAnimation(resource,AnimationPlayConfig(transitionDuration=.12f))}
    }
    private fun stopAnimations(){animationControllers.forEach{it.stop();it.close()};animationControllers.clear();clip=""}
    private fun prepareGuardian(entity:Entity){
        val targets=entity.findSkeletonRootEntity(includeDisabled=true).toList().ifEmpty{entity.findSkinnedMeshEntity(includeDisabled=true).toList()}
        check(targets.isNotEmpty()){"Guardian skeleton missing"}
        for(target in targets){val resources=target.getAnimationResources().toList();check(resources.isNotEmpty());guardianResources[target]=resources;Log.i("RunnerGuardian","clips=${resources.map{it.getName()}} source=SGS localQuadrupedRig=true")}
    }
    private fun animateGuardian(name:String,speed:Float){
        if(name!=guardianClip||guardianControllers.any{it.isComplete()}){
            stopGuardian();guardianClip=name
            for((entity,resources)in guardianResources){val resource=resources.firstOrNull{it.getName().equals(name,true)}?:error("Guardian missing $name");guardianControllers+=entity.playAnimation(resource,AnimationPlayConfig(transitionDuration=.12f))}
        }
        if(abs(speed-guardianSpeed)>.025f){guardianControllers.forEach{it.setSpeed(speed)};guardianSpeed=speed}
    }
    private fun stopGuardian(){guardianControllers.forEach{it.stop();it.close()};guardianControllers.clear();guardianClip="";guardianSpeed=-1f}
    private fun showResultMenu()=navigation.page==RunnerPage.SELECT||((ready||failed)&&navigation.page==RunnerPage.GAME&&game.phase!=Phase.RUNNING&&!(game.phase==Phase.ENDED&&defeatTime<1.2f))

    fun frame(dt:Float){
        if(closed)return
        placement.apply(seat)
        bindPanels()
        if(navigation.page==RunnerPage.SELECT){
            selectionRoom?.tick(dt)
            var command=input.queue.poll()
            while(command!=null){if(command==Command.CALIBRATE)calibrate();command=input.queue.poll()}
            return
        }
        if(ready)tickFire(dt)
        if(navigation.page==RunnerPage.ENTERING){
            input.queue.clear();openingTime+=dt.coerceIn(0f,.05f)
            val t=(openingTime/1.1f).coerceIn(0f,1f);opening?.update(head,1f-t*t*(3f-2f*t))
            if(t>=1f){opening?.close();opening=null;navigation.finishEntry();skipMotionFrame=true;publishState();Log.i("RunnerScene","entered chapter=${game.chapter.ordinal+1} distance=${game.distance} page=GAME openingComplete=true world=${world.enabled} sky=${sky?.enabled} menuBackdrop=${menuBackdrop?.enabled}")}
            return
        }
        if(!ready)return
        var cmd=input.queue.poll()
        while(cmd!=null){when(cmd){Command.PAUSE->togglePause();Command.CALIBRATE->calibrate();else->if(game.phase==Phase.RUNNING)game.move(Move.valueOf(cmd.name))};cmd=input.queue.poll()}
        game.tick(if(skipMotionFrame)0f else dt);skipMotionFrame=false
        val playing=game.phase==Phase.RUNNING
        if(game.phase==Phase.ENDED)defeatTime+=dt.coerceIn(0f,.05f)
        applyGamePose()
        val key=(game.distance/6f).toInt()
        if(layoutKey!=key)layoutTrack()
        updateObjects()
        body?.enabled=playing&&!comfort
        if(playing&&!comfort){val desired=when{game.jumping->"jump";game.sliding->"slide";else->"run"};if(desired!=clip||animationControllers.any{it.isComplete()}){if(desired==clip)clip="";animate(desired)};if(desired=="run")animationControllers.forEach{it.setSpeed(.72f/(2f*game.footstepInterval))}}else if(animationControllers.isNotEmpty())stopAnimations()
        val progress=game.turnProgress
        fun ease(value:Float):Float{val t=value.coerceIn(0f,1f);return t*t*(3f-2f*t)}
        val strength=if(comfort&&playing&&game.turning).96f*min(ease(progress/.20f),ease((1f-progress)/.20f)) else 0f
        mask?.update(head,strength)
        if(playing&&!game.jumping&&!game.sliding){stepTime+=dt.coerceIn(0f,.05f);if(stepTime>=game.footstepInterval){stepTime-=game.footstepInterval;audio.step()}}else stepTime=0f
        if(game.phase in listOf(Phase.ENDED,Phase.CLEARED)&&!saved){saved=true;prefs.edit().putInt("best",max(prefs.getInt("best",0),game.score)).apply();Log.i("RunnerGame","ended phase=${game.phase} score=${game.score} reason=${game.reason}")}
        panels?.entity("runner-hud")?.enabled=playing
        panels?.entity("runner-threat")?.enabled=ready&&(game.pursuerClose||game.phase==Phase.ENDED&&defeatTime<1.2f)
        publishTime+=dt;if(publishTime>.15f){publishTime=0f;publishState()}
    }
    private fun instance(name:String,id:String,at:Float,y:Float=0f,side:Float=0f,scale:Float=1f,roll:Float=0f,yawOffset:Float=0f){
        val p=game.pose(at);val r=Math.toRadians(p.yaw.toDouble())
        val position=Vector3(p.x+cos(r).toFloat()*side,y,p.z-sin(r).toFloat()*side)
        batches.getValue(name).set(id,Transform(position,EulerAngles(0f,p.yaw+yawOffset,roll),Vector3(scale,scale,scale)))
    }
    private fun layoutTrack(){
        layoutKey=(game.distance/6f).toInt()
        val wanted=mutableMapOf<String,MutableSet<String>>()
        fun place(name:String,id:String,at:Float,y:Float=0f,side:Float=0f,scale:Float=1f,yawOffset:Float=0f,extent:Float=4f){
            if(at+extent<game.distance-18f||at-extent>game.distance+72f)return
            wanted.getOrPut(name){mutableSetOf()}.add(id);instance(name,id,at,y,side,scale,yawOffset=yawOffset)
        }
        for(leg in max(0,(game.distance/64).toInt()-1)..min(11,(game.distance/64).toInt()+1)){
            val stage=Chapter.atLeg(leg);val start=leg*64f
            // All turns use broad low stone pads; prefabs on the straights keep their native proportions.
            for((i,offset) in listOf(1.5f,4.5f,59.5f,62.5f).withIndex()){
                val at=start+offset
                if(at+1.5f>=game.distance-18f&&at-1.5f<=game.distance+72f){
                    val id="pad_${leg}_$i";wanted.getOrPut("path_open"){mutableSetOf()}.add(id);val p=game.pose(at)
                    batches.getValue("path_open").set(id,Transform(Vector3(p.x,0f,p.z),EulerAngles(0f,p.yaw,0f),Vector3(1f,1f,3f/lengths.getValue("path_open"))))
                }
            }
            val road=stage.road
            val length=lengths.getValue(road);val count=ceil(52f/length).toInt();val spacing=52f/count
            for(i in 0 until count){
                val at=start+6f+(i+.5f)*spacing
                if(at+spacing/2>=game.distance-18f&&at-spacing/2<=game.distance+72f){
                    for((part,span) in game.deckSpans(at-spacing/2,at+spacing/2).withIndex()){
                        val id="road_${leg}_${i}_$part";wanted.getOrPut(road){mutableSetOf()}.add(id);val p=game.pose(span.center)
                        batches.getValue(road).set(id,Transform(Vector3(p.x,0f,p.z),EulerAngles(0f,p.yaw,0f),Vector3(1f,1f,span.length/length)))
                    }
                }
            }
            val terrainY=if(leg%2==0)-.24f else -.28f
            if(stage!=Chapter.CANYON){
                for(i in 0..1){
                    val at=start+16f+i*32f
                    if(at+18f>=game.distance-18f&&at-18f<=game.distance+72f){
                        for((part,span) in game.deckSpans(at-16f,at+16f).withIndex()){
                            val id="terrain_${leg}_${i}_$part";wanted.getOrPut("path_open"){mutableSetOf()}.add(id)
                            val p=game.pose(span.center)
                            batches.getValue("path_open").set(id,Transform(Vector3(p.x,terrainY,p.z),EulerAngles(0f,p.yaw,0f),Vector3(22f/3.5f,1.6f,span.length/lengths.getValue("path_open"))))
                        }
                    }
                }
            }
            if(stage==Chapter.JUNGLE){
                for(i in 0 until 6)place("jungle","grove_${leg}_$i",start+10f+i*9f,y=terrainY,side=if(i%2==0)-6f else 6f)
            }
            if(stage==Chapter.CORRIDOR||stage==Chapter.SANCTUM){
                // The generated hall is a narrow arch; use intact halls as side colonnades.
                // The three-lane walking surface remains unobstructed rather than stretching the asset.
                for(i in 0..3)for(side in listOf(-4.8f,4.8f))place("corridor","cloister_${leg}_${i}_$side",start+14f+i*12f,y=terrainY,side=side,yawOffset=if(side<0)90f else -90f)
            }
            if(stage==Chapter.SANCTUM){
                for(i in 0..1)for(side in listOf(-8f,8f))place("gold_gate","sanctum_portal_${leg}_${i}_$side",start+22f+i*26f,y=terrainY,side=side,scale=.28f,yawOffset=if(side<0)90f else -90f)
            }
            if(stage==Chapter.JUNGLE&&leg==0)place("gate","entrance",4f,extent=4f)
            if(stage==Chapter.CANYON)for(i in 0..1)place("gate","ruins_${leg}_$i",start+18f+i*28f,y=-2.4f,side=if(i==0)-6f else 6f,scale=.25f,yawOffset=90f)
        }
        place("gold_gate","finish",RunnerGame.FINISH_DISTANCE,extent=4f)
        for(name in listOf("path","path_open","bridge","corridor","jungle","gate","gold_gate"))batches.getValue(name).retain(wanted[name]?:emptySet())
        Log.i("RunnerLayout","chapter=${game.chapter.ordinal+1} distance=${game.distance.toInt()} visibleInstances=${batches.values.sumOf{it.instanceCount}} gpuBatches=${batches.values.sumOf{it.meshBatchCount}}")
    }
    private fun updateObjects(){
        val wanted=mutableMapOf<String,MutableSet<String>>()
        fun place(name:String,id:String,o:Obstacle,y:Float=0f,side:Float=0f,size:Vector3=Vector3.ONE,at:Float=o.distance){
            wanted.getOrPut(name){mutableSetOf()}.add(id)
            val p=game.pose(at);val r=Math.toRadians(p.yaw.toDouble())
            batches.getValue(name).set(id,Transform(Vector3(p.x+cos(r).toFloat()*side,y,p.z-sin(r).toFloat()*side),EulerAngles(0f,p.yaw,0f),size))
        }
        for(o in game.obstacles)if(o.distance>game.distance-5f&&o.distance<game.distance+72f&&o.distance<RunnerGame.FINISH_DISTANCE){
            val id="obstacle_${o.id}";val side=if(o.fullWidth)0f else o.lane*.9f
            when(o.skin){
                ObstacleSkin.ROOT->place("log",id,o,side=side,size=Vector3(if(o.fullWidth)2.6f else 1f,1f,1f))
                ObstacleSkin.FALLEN_COLUMN->place("fallen_column",id,o,side=side)
                ObstacleSkin.STONE_BLOCK->place("stone_block",id,o,side=side)
                ObstacleSkin.STONE_ARCH->{
                    place("low_arch",id,o)
                }
                ObstacleSkin.FIRE_TRAP->{
                    for(i in -1..1)place("brazier","${id}_brazier_$i",o,side=i*.88f)
                }
                ObstacleSkin.BROKEN_ROAD->{
                    for(edge in listOf(-1f,1f))for(s in listOf(-1.55f,1.55f))place("fallen_column","${id}_edge_${edge}_$s",o,side=s,size=Vector3(.16f,.25f,.5f),at=o.distance+edge*(o.length/2+.12f))
                }
            }
        }
        for(name in listOf("log","stone_block","stone_beam","fallen_column","low_arch","brazier"))batches.getValue(name).retain(wanted[name]?:emptySet())
        val coins=mutableSetOf<String>()
        for(coin in game.coinLine)if(coin.distance>game.distance-2f&&coin.distance<game.distance+72f&&coin.distance<RunnerGame.FINISH_DISTANCE&&game.coinVisible(coin.id)){
            val id="coin_${coin.id}";coins+=id
            val p=game.pose(coin.distance);val r=Math.toRadians(p.yaw.toDouble())
            val pos=Vector3(p.x+cos(r).toFloat()*coin.lane*.9f,coin.y,p.z-sin(r).toFloat()*coin.lane*.9f)
            // Static facing coins avoid rebuilding the GPU instance buffer on every frame.
            batches.getValue("coin").set(id,Transform(pos,EulerAngles(90f,p.yaw,0f),Vector3.ONE))
        }
        batches.getValue("coin").retain(coins)
        guardian?.let{entity->
            val playing=game.phase==Phase.RUNNING;val visible=PursuerPresentation.visible(game.phase)
            entity.enabled=visible
            if(playing){
                val at=game.distance-game.pursuerGap;val p=if(at<0f)TrackPose(0f,-at,0f)else game.pose(at)
                val r=Math.toRadians(p.yaw.toDouble());val x=p.x+cos(r).toFloat()*game.laneX;val z=p.z-sin(r).toFloat()*game.laneX
                transform(entity).setPosition(Vector3(x,0f,z));transform(entity).setEulerAngles(EulerAngles(0f,p.yaw+180f,0f))
                animateGuardian("run",(game.speed/4.5f).coerceIn(.75f,1.9f))
            }else if(visible){
                // Keep the pursuer behind the stopped player after every defeat.
                // A natural head turn can inspect it until replay/selection.
                val gap=PursuerPresentation.defeatGap(game.pursuerGap,defeatTime)
                val p=PursuerPresentation.pose(game,gap)
                val r=Math.toRadians(p.yaw.toDouble())
                transform(entity).setPosition(Vector3(p.x+cos(r).toFloat()*game.laneX,0f,p.z-sin(r).toFloat()*game.laneX))
                transform(entity).setEulerAngles(EulerAngles(0f,p.yaw+180f,0f))
                animateGuardian(if(defeatTime<.8f)"run" else "idle",1f)
            }else if(guardianControllers.isNotEmpty())stopGuardian()
        }
    }
    fun attachPanels(view:SpatialViewContent,value:SpatialViewAttachments,selectionRasterScale:Float?=null){
        if(selectionRasterScale!=null)selectionPhysicalScale=1f/selectionRasterScale
        panels=value
        bindPanels()
    }
    private fun bindPanels(){
        val value=panels?:return
        fun place(id:String,position:Vector3,visible:Boolean,physicalScale:Float=1f){
            value.entity(id)?.let{entity->
                if(positionedPanels[id]!=entity.id||entity.getParent()!=seat||abs(transform(entity).scaleVector.x-physicalScale)>.001f){
                    if(entity.getParent()!=seat)seat.addChild(entity)
                    transform(entity).apply{setPosition(position);setScaleVector(Vector3(physicalScale,physicalScale,physicalScale))};positionedPanels[id]=entity.id
                    Log.i("LobbyLayout","bound id=$id position=$position visible=$visible physicalScale=$physicalScale")
                }
                if(entity.enabled!=visible)entity.enabled=visible
            }
        }
        place("runner-hud",Vector3(0f,2.05f,-1.7f),ready&&navigation.page==RunnerPage.GAME&&game.phase==Phase.RUNNING)
        val activeMenu=RunnerMenuPanel.forState(navigation.page,game.phase)
        for(menu in RunnerMenuPanel.entries)place(menu.id,Vector3(0f,1.55f,-1.15f),showResultMenu()&&menu==activeMenu,if(menu==RunnerMenuPanel.SELECTION)selectionPhysicalScale else 1f)
        place("runner-threat",Vector3(0f,2.25f,-1.7f),ready&&(game.pursuerClose||game.phase==Phase.ENDED&&defeatTime<1.2f))
    }
    private fun applyGamePose(){
        val pose=game.renderPose();val yaw=pose.yaw;val r=Math.toRadians((-yaw).toDouble());val c=cos(r).toFloat();val s=sin(r).toFloat()
        val height=RunnerMotion.height(game.jumpHeight,game.slideTime,comfort)
        transform(world).apply{setEulerAngles(EulerAngles(0f,-yaw,0f));setPosition(Vector3(-c*pose.x-s*pose.z-game.laneX,-height,s*pose.x-c*pose.z))}
    }
    fun startOrResume(){
        if(!ready)return
        if(navigation.page==RunnerPage.GAME&&game.phase==Phase.PAUSED){game.resume();audio.resume();skipMotionFrame=true;publishState();return}
        if(!navigation.enter())return
        audio.pause();input.queue.clear();layoutKey=-1;saved=false;stepTime=0f;defeatTime=0f;openingTime=0f;fireUpdates=0;fireAccumulator=0f
        opening=OpeningView(seat,"正在进入 · ${Chapter.entries[game.selectedChapter].title}").also{it.update(head,1f)}
        applyGamePose();layoutTrack();updateObjects();world.enabled=true;sky?.enabled=true;menuBackdrop?.enabled=false;selectionRoom?.show(false)
        for(menu in RunnerMenuPanel.entries)panels?.entity(menu.id)?.enabled=false
        publishState()
        Log.i("RunnerScene","entry requested chapter=${game.selectedChapter+1} page=ENTERING simulationRunning=false")
    }
    fun pause(){if(navigation.page==RunnerPage.ENTERING){returnToSelection();return};game.pause();audio.pause();publishState()}
    fun togglePause(){if(game.phase==Phase.RUNNING)pause()else if(game.phase==Phase.PAUSED)startOrResume()}
    fun calibrate(){pause();placement.request();publishState()}
    fun chooseChapter(index:Int){if(navigation.page!=RunnerPage.SELECT)return;navigation.select(index);layoutKey=-1;saved=false;defeatTime=0f;publishState();Log.i("RunnerScene","selected chapter=${game.selectedChapter+1} page=SELECT worldVisible=false")}
    fun returnToSelection(){
        audio.pause();stopAnimations();stopGuardian();opening?.close();opening=null;navigation.returnToSelection();input.queue.clear()
        world.enabled=false;sky?.enabled=false;menuBackdrop?.enabled=true;selectionRoom?.show(true);body?.enabled=false;guardian?.enabled=false;defeatTime=0f;layoutKey=-1;placement.request()
        panels?.entity("runner-hud")?.enabled=false;panels?.entity("runner-threat")?.enabled=false;publishState()
        Log.i("RunnerScene","returned page=SELECT worldVisible=false")
    }
    fun toggleComfort(){comfort=!comfort;game.comfortPace=comfort;prefs.edit().putBoolean("comfort",comfort).apply();publishState()}
    private fun publishState(){
        val next=game.obstacles.firstOrNull{it.distance>game.distance&&it.covers(game.laneX)}
        val hint=when{
            RunnerGame.FINISH_DISTANCE-game.distance<12f->"终点 ${ceil(RunnerGame.FINISH_DISTANCE-game.distance).toInt()}米"
            game.turning||game.turnDistance<12f->{
                val direction=if(game.requiredTurn<0)"← 左转" else "右转 →"
                when{game.queuedTurn==game.requiredTurn->"$direction ✓";game.turnDistance<=1.5f->"$direction · 现在拨动";else->"前方 $direction ${ceil(game.turnDistance).toInt()}米"}
            }
            game.jumping->"↑ 跳跃中"
            game.sliding->"↓ 滑铲中"
            next!=null&&next.distance-game.distance<max(9f,game.speed*1.6f)->when(next.kind){ObstacleKind.LOW->"↑ 跳过路障";ObstacleKind.FIRE->"↑ 跳过火焰";ObstacleKind.GAP->"↑ 跳过断路";ObstacleKind.HIGH->"↓ 滑过石门";ObstacleKind.BLOCK->"←→ 绕开石块"}
            else->"${game.score} 分"
        }
        publish(RunnerUi(ready,failed,game.phase,game.runDistance.toInt(),game.coins,game.score,prefs.getInt("best",0),hint,game.reason,comfort,game.chapter.ordinal,game.selectedChapter,game.speed,game.pursuerClose,game.caught,navigation.page,showResultMenu(),ready&&(game.pursuerClose||game.phase==Phase.ENDED&&defeatTime<1.2f)))
    }
    private fun makeMenuBackdrop(){
        val mesh=MeshResource.createSphere(80f);ownedResources+=mesh
        val bitmap=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888)
        val paint=Paint().apply{shader=LinearGradient(0f,0f,0f,512f,intArrayOf(0xff456b72.toInt(),0xffb0c7b1.toInt(),0xff789963.toInt(),0xffb0c7b1.toInt(),0xff456b72.toInt()),null,Shader.TileMode.CLAMP)}
        Canvas(bitmap).drawRect(0f,0f,1024f,512f,paint);val texture=TextureResource(bitmap);ownedResources+=texture
        val material=UnlitMaterial.create().apply{setBaseColorTexture(texture);setCullingMode(MaterialCullingMode.NONE);setApplyToneMapping(false)};ownedResources+=material
        menuBackdrop=node("SelectionBackdrop").apply{components.set(ModelComponent(mesh,material))};seat.addChild(menuBackdrop!!)
    }
    private fun makeSky(){
        val bitmap=Bitmap.createBitmap(1024,512,Bitmap.Config.ARGB_8888)
        val paint=Paint().apply{shader=LinearGradient(0f,0f,0f,512f,intArrayOf(0xff244a5b.toInt(),0xffa3b19a.toInt(),0xffd9cba2.toInt(),0xff405f35.toInt(),0xffd9cba2.toInt(),0xffa3b19a.toInt(),0xff244a5b.toInt()),null,Shader.TileMode.CLAMP)}
        Canvas(bitmap).drawRect(0f,0f,1024f,512f,paint)
        val texture=TextureResource(bitmap);ownedResources+=texture
        val material=UnlitMaterial.create().apply{setBaseColorTexture(texture);setCullingMode(MaterialCullingMode.NONE)};ownedResources+=material
        val mesh=MeshResource.createSphere(140f);ownedResources+=mesh;sky=node("JungleSky").apply{components.set(ModelComponent(mesh,material));enabled=false};seat.addChild(sky!!)
        // Lightweight distant river surface; the visible route, plants and masonry remain SGS assets.
        val riverMaterial=UnlitMaterial.create().apply{setBaseColor(Color4(.16f,.31f,.31f,1f))};ownedResources+=riverMaterial
        val riverMesh=MeshResource.createPlane(400f,400f);ownedResources+=riverMesh
        world.addChild(node("DistantRiver").apply{components.set(ModelComponent(riverMesh,riverMaterial));transform(this).setPosition(Vector3(0f,-4f,0f));transform(this).setEulerAngles(EulerAngles(-90f,0f,0f))})
    }
    private fun makeFirePrototype(){
        batches["flame"]=InstanceBatch(world,prototypes.getValue("flame_tongue"),"flame")
        val material=UnlitMaterial.create(BlendingMode.FADE).apply{setBaseColor(Color4(1f,.58f,.08f,.85f));setDepthWrite(false)};ownedResources+=material
        val mesh=MeshResource.createSphere(.016f);ownedResources+=mesh
        val source=node("Prototype_spark").apply{components.set(ModelComponent(mesh,material))};world.addChild(source);source.enabled=false
        batches["spark"]=InstanceBatch(world,source,"spark")
        Log.i("RunnerFire","volumeMesh=true triangleCount=76 animatedTongues=true risingSparks=true updateHz=24")
    }
    private fun tickFire(dt:Float){
        val delta=dt.coerceIn(0f,.05f);fireTime+=delta;fireAccumulator+=delta
        if(fireAccumulator<FireMotion.UPDATE_INTERVAL)return
        fireAccumulator%=FireMotion.UPDATE_INTERVAL
        val flames=mutableSetOf<String>();val sparks=mutableSetOf<String>()
        for(o in game.obstacles)if(o.skin==ObstacleSkin.FIRE_TRAP&&o.distance>game.distance-5f&&o.distance<game.distance+72f){
            val pose=game.pose(o.distance);val radians=Math.toRadians(pose.yaw.toDouble());val c=cos(radians).toFloat();val s=sin(radians).toFloat()
            fun position(x:Float,y:Float,z:Float)=Vector3(pose.x+c*x+s*z,y,pose.z-s*x+c*z)
            for(i in 0..8){
                val motion=FireMotion.tongue(fireTime,o.id,i);val id="fire_${o.id}_$i";flames+=id
                batches.getValue("flame").set(id,Transform(position((i-4)*.29f+motion.sway,fireBaseHeight+motion.lift,if(i%2==0)-.06f else .06f),EulerAngles(0f,pose.yaw+motion.yaw,0f),Vector3(motion.width,motion.height,motion.width)))
            }
            for(i in 0..3){
                val motion=FireMotion.spark(fireTime,o.id,i);val id="spark_${o.id}_$i";sparks+=id
                batches.getValue("spark").set(id,Transform(position(motion.x,fireBaseHeight+motion.y,motion.z),EulerAngles(0f,pose.yaw,0f),Vector3(motion.scale,motion.scale,motion.scale)))
            }
        }
        batches.getValue("flame").retain(flames);batches.getValue("spark").retain(sparks)
        fireUpdates++;if(fireUpdates==1||fireUpdates==24||fireUpdates%120==0)Log.i("RunnerFire","updates=$fireUpdates tongues=${flames.size} sparks=${sparks.size} time=$fireTime")
        if(fireUpdates==24){
            val faces=batches.entries.sumOf{(name,batch)->(modelFaces[name]?:0)*batch.instanceCount}+(if(body?.enabled==true)modelFaces.getValue("hero_fp")else 0)+(if(guardian?.enabled==true)modelFaces.getValue("guardian")else 0)
            Log.i("RunnerGeometry","chapter=${game.chapter.ordinal+1} prefabFaces=$faces stockCoinSparkSkyExcluded=true")
        }
    }
    fun close(){if(closed)return;closed=true;stopAnimations();stopGuardian();opening?.close();opening=null;selectionRoom?.close();mask?.close();batches.values.forEach{it.close()};batches.clear();seat.destroy();ownedResources.asReversed().forEach{runCatching{it.close()}};ownedResources.clear();audio.close()}
    companion object{private fun node(name:String)=Entity().apply{setName(name)};private fun transform(e:Entity)=requireNotNull(e.components[TransformComponent::class.java])}
}
