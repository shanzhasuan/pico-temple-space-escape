package com.shanzhasuan.templesprint

import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shanzhasuan.templesprint.content.RunnerSystem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual SDK resource/scene verification. Does not simulate controller or hand input. */
@RunWith(AndroidJUnit4::class)
class ModelRefreshRuntimeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private var marker=""
    private fun log():String=instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${Process.myPid()} -s RunnerAssets:I RunnerScene:I RunnerFire:I RunnerGeometry:I RunnerLayout:I ModelRefresh:I").use{
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use{reader->reader.readText()}
    }
    private fun waitFor(label:String,timeout:Long=30000,predicate:()->Boolean){
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<deadline){if(predicate())return;SystemClock.sleep(100)}
        fail("Timed out $label\n${log().takeLast(6000)}")
    }
    @Test fun allFourChaptersLoadAndFireKeepsMoving(){
        val context=instrumentation.targetContext
        if(RunnerSystem.active==null)context.startActivity(Intent().setClassName(context.packageName,"${context.packageName}.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("updated resources",45000){RunnerSystem.active!=null&&log().contains("models=16")}
        val initial=log()
        for(name in listOf("log","jungle","bridge","corridor","gold_gate","brazier","low_arch","flame_tongue"))assertTrue("Missing native model $name",initial.contains("model=$name source="))
        assertTrue(initial.contains("volumeMesh=true"))
        try{
            for(chapter in 0..3){
                marker="chapter-${chapter+1}-${SystemClock.elapsedRealtime()}";Log.i("ModelRefresh",marker)
                instrumentation.runOnMainSync{RunnerSystem.active!!.chooseChapter(chapter);RunnerSystem.active!!.startOrResume()}
                waitFor("chapter entry"){log().substringAfter(marker,"").contains("entered chapter=${chapter+1}")}
                waitFor("advancing flame animation",10000){val entries=log().substringAfter(marker,"");entries.contains("RunnerFire: updates=24")&&entries.contains("prefabFaces=")}
                val current=log().substringAfter(marker,"")
                val samples=Regex("updates=(1|24) tongues=(\\d+) sparks=(\\d+) time=([\\d.]+)").findAll(current).toList()
                assertTrue("No two native animation samples",samples.size>=2)
                // Beginner jungle has no fire hazards; later chapters must contain live fire.
                if(chapter>0)assertTrue("Later chapter has no fire instances",samples.all{it.groupValues[2].toInt()>0&&it.groupValues[3].toInt()>0})
                assertTrue("Fire time did not advance",samples.last().groupValues[4].toFloat()>samples.first().groupValues[4].toFloat()+.5f)
                val geometry=Regex("prefabFaces=(\\d+)").find(current)!!.groupValues[1].toInt()
                assertTrue("Visible mesh budget exceeded",geometry<850000)
                instrumentation.sendStatus(2,android.os.Bundle().apply{putString("stream","\nPASS chapter=${chapter+1} nativeModels=16 movingFire=${chapter>0} prefabFaces=$geometry stockMeshesExcluded=true ${samples.map{it.value}}\n")})
                instrumentation.runOnMainSync{RunnerSystem.active!!.returnToSelection()}
                SystemClock.sleep(200)
            }
        }finally{instrumentation.runOnMainSync{RunnerSystem.active?.returnToSelection()}}
    }
}
