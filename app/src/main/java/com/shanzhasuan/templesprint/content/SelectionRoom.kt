package com.shanzhasuan.templesprint.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3

/** Static shared-mesh temple entrance behind selection, separate from gameplay. */
class SelectionRoom(owner:Entity):AutoCloseable {
    private val root=Entity().apply{setName("SelectionTempleEntrance");owner.addChild(this)}
    private val materials=mutableListOf<UnlitMaterial>()
    private var fade=0f
    private var showing=true
    fun create(prototypes:Map<String,Entity>,roadLength:Float){
        val gate=copy(prototypes.getValue("gate"));root.addChild(gate);transform(gate).setPosition(Vector3(0f,0f,-10f))
        for(i in 0..3){val p=copy(prototypes.getValue("path_open"));root.addChild(p);transform(p).setPosition(Vector3(0f,0f,-4f-i*4f));transform(p).setScaleVector(Vector3(1f,1f,4f/roadLength))}
        for(x in listOf(-7f,7f))for(z in listOf(-7f,-16f)){val grove=copy(prototypes.getValue("jungle"));root.addChild(grove);transform(grove).setPosition(Vector3(x,0f,z))}
    }
    private fun copy(source:Entity):Entity {
        val node=Entity().apply{setName("Lobby_${source.getName()}")};val src=transform(source)
        transform(node).apply{setPosition(src.position);setQuaternion(src.quaternion);setScaleVector(src.scaleVector)}
        source.components[ModelComponent::class.java]?.let{model->
            val copied:Array<Material> = model.materials.map{sourceMaterial->
                val srcMaterial=sourceMaterial as? UnlitMaterial
                val material=UnlitMaterial.create(BlendingMode.FADE).apply{
                    setBaseColor(srcMaterial?.getBaseColor()?:Color4(.8f,.7f,.5f,1f))
                    if(srcMaterial!=null)runCatching{srcMaterial.getBaseColorTexture()}.getOrNull()?.takeIf{it.valid}?.let{setBaseColorTexture(it)}
                    setCullingMode(srcMaterial?.getCullingMode()?:MaterialCullingMode.BACK);setDepthWrite(true);setOpacity(0f);toGlobal()
                }
                materials+=material;material
            }.toTypedArray()
            node.components.set(ModelComponent(model.mesh,copied));node.components.set(GroundShadowComponent(false,false))
        }
        source.getChildren().forEach{node.addChild(copy(it))};return node
    }
    fun show(value:Boolean){showing=value;root.enabled=value}
    fun tick(dt:Float){
        if(!showing||fade>=1f)return
        fade=(fade+dt.coerceIn(0f,.05f)/.7f).coerceAtMost(1f);val opacity=fade*fade*(3f-2f*fade)
        materials.forEach{it.setOpacity(opacity)}
    }
    override fun close(){root.destroy();materials.forEach{it.close()};materials.clear()}
    private fun transform(e:Entity)=requireNotNull(e.components[TransformComponent::class.java])
}
