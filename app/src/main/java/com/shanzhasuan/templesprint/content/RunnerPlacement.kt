package com.shanzhasuan.templesprint.content

import android.os.SystemClock
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import kotlin.math.*

/** A one-time rigid seat alignment. Subsequent natural head movement stays untouched. */
class RunnerPlacement {
    private var pose:HMDPose?=null
    private var at=0L
    private var requested=true
    private var expectedEye:Vector3?=null
    private var checks=0
    var calibrated=false;private set
    fun observe(value:HMDPose){
        val p=value.position;val q=value.rotation;val n=q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w
        if(p.x.isFinite()&&p.y.isFinite()&&p.z.isFinite()&&n in .5f..1.5f){pose=value;at=SystemClock.elapsedRealtime()}
    }
    fun request(){requested=true;expectedEye=null;calibrated=false}
    fun apply(owner:Entity){
        expectedEye?.let{expected->
            val eye=Vector3(0f,EYE_HEIGHT,0f)
            val actual=owner.convertPositionTo(eye,null);val delta=expected-actual
            val error=sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z)
            if(error<=.005f){calibrated=true;expectedEye=null;Log.i("RunnerPlacement","calibrated worldLocked=true eyeError=$error")}
            else if(checks++<5){
                val parent=owner.getParent()
                val expectedLocal=parent?.convertPositionFrom(expected,null)?:expected
                val actualLocal=owner.convertPositionTo(eye,parent)
                val transform=owner.components[TransformComponent::class.java]!!
                transform.setPosition(transform.position+expectedLocal-actualLocal)
            }else{expectedEye=null;Log.w("RunnerPlacement","calibration verification failed eyeError=$error")}
            return
        }
        if(!requested)return
        val head=pose?:return
        if(SystemClock.elapsedRealtime()-at>1000)return
        val q=head.rotation;val n=q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w
        val fx=-2f*(q.x*q.z+q.w*q.y)/n;val fz=-(1f-2f*(q.x*q.x+q.y*q.y)/n)
        if(fx*fx+fz*fz<.04f)return
        val yaw=atan2(-fx,-fz)
        val p=Vector3(head.position.x,head.position.y-EYE_HEIGHT,head.position.z)
        val rotation=EulerAngles(0f,Math.toDegrees(yaw.toDouble()).toFloat(),0f).toQuat()
        val parent=owner.getParent()
        owner.components[TransformComponent::class.java]!!.apply{setPosition(parent?.convertPositionFrom(p,null)?:p);setQuaternion(parent?.convertRotationFrom(rotation,null)?:rotation)}
        // Verify on subsequent ECS frames: native conversion can still reflect the previous transform in this frame.
        expectedEye=head.position;checks=0;requested=false
    }
    companion object{const val EYE_HEIGHT=1.55f}
}
