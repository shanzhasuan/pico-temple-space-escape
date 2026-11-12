package com.shanzhasuan.templesprint.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose

/** Head-aligned opaque loading artwork, fading only after all scene assets exist. */
class OpeningView(private val owner:Entity,private val subtitle:String?=null):AutoCloseable {
    private val entity=Entity().apply{setName("TempleOpeningTransition")}
    private val mesh=MeshResource.createPlane(2.6f,2.6f).apply{toGlobal()}
    private val opacity=OpacityControllerComponent(1f)
    private val material=UnlitMaterial.create(BlendingMode.FADE).apply{
        setBaseColor(Color4(1f,1f,1f,1f));setCullingMode(MaterialCullingMode.NONE)
        setDepthTest(false);setDepthWrite(false);setApplyToneMapping(false);toGlobal()
    }
    private var texture:TextureResource?=null
    private var bitmap:Bitmap?=null
    private var lastPercent=-1
    init {entity.components.set(ModelComponent(mesh,material));entity.components.set(opacity);owner.addChild(entity);progress(if(subtitle==null)0f else 1f);update(null,1f)}
    fun progress(value:Float){
        val percent=(value.coerceIn(0f,1f)*100).toInt()
        if(percent==lastPercent)return
        lastPercent=percent
        val image=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888)
        val c=Canvas(image);val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=LinearGradient(0f,0f,1024f,1024f,intArrayOf(0xff081611.toInt(),0xff19332a.toInt(),0xff081611.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,1024f,1024f,p);p.shader=null;p.color=0xffddbb68.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=3f
        c.drawRect(477f,353f,547f,420f,p);c.drawLine(466f,420f,558f,420f,p);c.drawLine(478f,351f,512f,326f,p);c.drawLine(512f,326f,546f,351f,p)
        p.style=Paint.Style.FILL;p.textAlign=Paint.Align.CENTER;p.textSize=36f
        c.drawText("神庙之空间逃亡",512f,502f,p);p.color=0xffeee8d4.toInt();p.textSize=24f
        c.drawText(subtitle?:if(percent<100)"神庙正在苏醒…" else "准备出发",512f,558f,p)
        p.color=0xff354b40.toInt();c.drawRoundRect(392f,594f,632f,602f,4f,4f,p)
        p.color=0xffddbb68.toInt();c.drawRoundRect(392f,594f,392f+240f*percent/100f,602f,4f,4f,p)
        val next=TextureResource(image).apply{toGlobal()};material.setBaseColorTexture(next)
        texture?.close();bitmap?.recycle();texture=next;bitmap=image
    }
    fun update(head:HMDPose?,strength:Float){
        val q=head?.rotation?:Quat.identity()
        val position=head?.position?:Vector3(0f,1.55f,0f)
        val t=entity.components[TransformComponent::class.java]!!
        t.setPosition(owner.convertPositionFrom(position+q.rotateVector(Vector3(0f,0f,-.7f)),null))
        t.setQuaternion(owner.convertRotationFrom(q,null));t.setScaleVector(owner.convertScaleFrom(Vector3.ONE,null))
        opacity.opacity=strength.coerceIn(0f,1f)
    }
    override fun close(){entity.destroy();material.close();texture?.close();bitmap?.recycle();mesh.close()}
}
