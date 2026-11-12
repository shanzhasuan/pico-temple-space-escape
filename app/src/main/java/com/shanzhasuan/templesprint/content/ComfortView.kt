package com.shanzhasuan.templesprint.content

import android.graphics.Bitmap
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import kotlin.math.*

/** A head-aligned peripheral vignette, independent of the moving world and natural head tracking. */
class ComfortView(private val owner:Entity) : AutoCloseable {
    private val entity=Entity().apply{setName("ComfortPeripheralMask");enabled=false}
    private val opacity=OpacityControllerComponent(0f)
    private val mesh=MeshResource.createPlane(2.6f,2.6f).apply{toGlobal()}
    private val texture:TextureResource
    private val material:UnlitMaterial
    init {
        val size=512;val bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(size*size)
        for(y in 0 until size)for(x in 0 until size){
            val radius=hypot((x-size/2f)/(size/2f),(y-size/2f)/(size/2f))
            val t=((radius-.23f)/.32f).coerceIn(0f,1f);val alpha=(255*t*t*(3f-2f*t)).toInt()
            pixels[y*size+x]=alpha shl 24
        }
        bitmap.setPixels(pixels,0,size,0,0,size,size)
        texture=TextureResource(bitmap).apply{toGlobal()}
        material=UnlitMaterial.create(BlendingMode.FADE).apply{
            setBaseColor(Color4(0f,0f,0f,1f));setBaseColorTexture(texture)
            setCullingMode(MaterialCullingMode.NONE);setDepthTest(false);setDepthWrite(false);setApplyToneMapping(false);toGlobal()
        }
        entity.components.set(ModelComponent(mesh,material));entity.components.set(opacity);owner.addChild(entity)
    }
    fun update(head:HMDPose?,strength:Float){
        if(head==null||strength<.005f){entity.enabled=false;return}
        val raw=head.rotation;val length=sqrt(raw.x*raw.x+raw.y*raw.y+raw.z*raw.z+raw.w*raw.w)
        if(!length.isFinite()||length<.5f){entity.enabled=false;return}
        val q=Quat(raw.x/length,raw.y/length,raw.z/length,raw.w/length)
        val target=head.position+q.rotateVector(Vector3(0f,0f,-.7f))
        val t=entity.components[TransformComponent::class.java]!!
        t.setPosition(owner.convertPositionFrom(target,null));t.setQuaternion(owner.convertRotationFrom(q,null))
        t.setScaleVector(owner.convertScaleFrom(Vector3.ONE,null))
        opacity.opacity=strength.coerceIn(0f,1f);entity.enabled=true
    }
    override fun close(){entity.destroy();material.close();texture.close();mesh.close()}
}
