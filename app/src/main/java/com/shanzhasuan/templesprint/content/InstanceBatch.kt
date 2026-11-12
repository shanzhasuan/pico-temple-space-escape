package com.shanzhasuan.templesprint.content

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3

/** One GPU instance resource per imported mesh/material batch; meshes and textures stay shared. */
class InstanceBatch(parent:Entity,source:Entity,name:String) : AutoCloseable {
    private data class Part(val entity:Entity,val component:ModelComponent,val instances:MeshInstancesResource,val fitted:Transform)
    private val parts=mutableListOf<Part>()
    private val current=mutableMapOf<String,Transform>()

    init {
        fun visit(node:Entity,parentTransform:Transform){
            val t=node.components[TransformComponent::class.java]!!
            val local=Transform(t.position,t.quaternion,t.scaleVector)
            val fitted=compose(parentTransform,local)
            node.components[ModelComponent::class.java]?.let{model->
                val resource=MeshInstancesResource.create("$name-${parts.size}")
                resource.toGlobal()
                val component=ModelComponent(model.mesh,model.materials.toList().toTypedArray()).apply{meshInstances=resource;isVisible=false}
                val entity=Entity().apply{setName("Batch_$name-${parts.size}");components.set(component);components.set(GroundShadowComponent(false,false))}
                parent.addChild(entity);parts+=Part(entity,component,resource,fitted)
            }
            node.getChildren().forEach{visit(it,fitted)}
        }
        // The source wrapper is identity. Its child transforms contain the verified uniform fit.
        visit(source,Transform())
        check(parts.isNotEmpty()){ "No source meshes for $name" }
    }
    fun set(id:String,pose:Transform){
        val old=current[id]
        if(old!=null&&same(old,pose))return
        for(part in parts){
            val fitted=compose(pose,part.fitted)
            if(old==null)check(part.instances.add(MeshInstancesResource.Instance(id,fitted)))
            else check(part.instances.update(id,fitted))
            part.component.isVisible=true
        }
        current[id]=pose
    }
    fun retain(ids:Set<String>){
        val expired=current.keys.filter{it !in ids}
        for(id in expired){for(part in parts)part.instances.remove(id);current.remove(id)}
        if(current.isEmpty())parts.forEach{it.component.isVisible=false}
    }
    val instanceCount get()=current.size
    val meshBatchCount get()=parts.size
    override fun close(){parts.forEach{it.entity.destroy();it.instances.close()};parts.clear();current.clear()}
    companion object {
        private fun multiply(a:Vector3,b:Vector3)=Vector3(a.x*b.x,a.y*b.y,a.z*b.z)
        private fun compose(a:Transform,b:Transform)=Transform(a.position+a.quaternion.rotateVector(multiply(a.scale,b.position)),a.quaternion*b.quaternion,multiply(a.scale,b.scale))
        private fun same(a:Transform,b:Transform)=a.position==b.position&&a.quaternion==b.quaternion&&a.scale==b.scale
    }
}
