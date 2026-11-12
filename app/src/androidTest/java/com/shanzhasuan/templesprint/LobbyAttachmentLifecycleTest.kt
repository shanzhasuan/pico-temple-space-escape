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

/** Drives existing app actions on the main thread; checks the real SpatialUI layout.
 * This tests attachment lifecycle, not controller/hand input or gameplay reactions.
 */
@RunWith(AndroidJUnit4::class)
class LobbyAttachmentLifecycleTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private var marker=""

    private fun log():String = instrumentation.uiAutomation.executeShellCommand(
        "logcat -d --pid=${Process.myPid()} -s LobbyLayout:I RunnerScene:I LobbyRegression:I"
    ).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use{r->r.readText()}}

    private fun afterMarker():String = log().substringAfter(marker,"")
    private fun mark(label:String){marker="marker=$label-${SystemClock.elapsedRealtime()}";Log.i("LobbyRegression",marker)}
    private fun waitFor(label:String,timeout:Long=20000,predicate:()->Boolean){
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<deadline){if(predicate())return;SystemClock.sleep(100)}
        fail("Timed out: $label\n${afterMarker().takeLast(6000)}")
    }
    private fun action(block:()->Unit)=instrumentation.runOnMainSync(block)

    private fun assertLobby(){
        waitFor("full-size lobby images and descriptions"){
            val lines=afterMarker()
            lines.contains("panelPx=1650 x 1320") && (1..4).all{chapter->
                listOf("image","title","summary","action").all{kind->lines.contains("chapter=$chapter ${kind}Bounds=")}
            }
        }
        val lines=afterMarker()
        assertFalse("Selection inherited defeat size",lines.contains("panelPx=1160 x 1040"))
        for(chapter in 1..4){
            assertTrue("$chapter full description layout missing",lines.contains("chapter=$chapter summaryOverflow=false"))
            assertFalse("$chapter description clipped",lines.contains("chapter=$chapter summaryOverflow=true"))
        }
        val measurements=mutableListOf<String>()
        for(chapter in 1..4){
            for(kind in listOf("image","title","summary","action")){
                val pattern=Regex("chapter=$chapter ${kind}Bounds=Rect.fromLTRB\\(([\\d.]+), ([\\d.]+), ([\\d.]+), ([\\d.]+)\\)")
                val rect=pattern.findAll(lines).last().groupValues.drop(1).map{it.toFloat()}
                measurements+="$chapter/$kind=$rect"
                assertTrue("$chapter $kind outside canvas: $rect",rect[0]>=0&&rect[1]>=0&&rect[2]<=1650&&rect[3]<=1320)
                if(kind=="image")assertTrue("$chapter image collapsed: $rect",rect[2]-rect[0]>=500&&rect[3]-rect[1]>=200)
                // Text wraps to its natural width (about 386px), unlike the full-width image.
                // The broken 580dp canvas constrained descriptions to only 160px.
                if(kind=="summary")assertTrue("$chapter description collapsed: $rect",rect[2]-rect[0]>=300&&rect[3]-rect[1]>=50)
            }
        }
        assertTrue("Physical size compensation missing",lines.contains("physicalScale=1.333"))
        waitFor("actual full raster surface",10000){
            val surface=instrumentation.uiAutomation.executeShellCommand("dumpsys SurfaceFlinger").use{
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use{reader->reader.readText()}
            }
            Regex("0\\s+0\\s+1650\\s+1320").containsMatchIn(surface)
        }
        Log.i("LobbyRegression","PASS returned lobby raster=1650x1320 physicalDp=1100x880 images=4 summaries=4 surfaceFits=true")
        instrumentation.sendStatus(2,android.os.Bundle().apply{
            putString("stream","\n$marker PASS returned lobby raster=1650x1320 physicalDp=1100x880 images=4 summaries=4 surfaceFits=true ${measurements.joinToString("; ")}\n")
        })
    }

    @Test fun fullSelectionCanvasSurvivesRepeatedPauseAndDefeatReturns(){
        val context=instrumentation.targetContext
        if(RunnerSystem.active==null)context.startActivity(Intent().setClassName(context.packageName,"${context.packageName}.platform.LaunchActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("app and resources ready",40000){RunnerSystem.active!=null&&log().contains("lobby ready sdk=6.1.9")}
        try{
            repeat(2){cycle->
                mark("pause-enter-$cycle");action{RunnerSystem.active!!.startOrResume()}
                waitFor("entered gameplay"){afterMarker().contains("entered chapter=")}
                mark("pause-$cycle");action{RunnerSystem.active!!.pause()}
                waitFor("pause attachment"){afterMarker().contains("bound id=runner-paused")}
                mark("pause-return-$cycle");action{RunnerSystem.active!!.returnToSelection()};assertLobby()
            }
            repeat(2){cycle->
                mark("defeat-enter-$cycle");action{RunnerSystem.active!!.startOrResume()}
                // No input: the existing course naturally ends the run at an obstacle.
                waitFor("defeat attachment",45000){afterMarker().contains("bound id=runner-defeat")}
                mark("defeat-return-$cycle");action{RunnerSystem.active!!.returnToSelection()};assertLobby()
            }
        }finally{action{RunnerSystem.active?.returnToSelection()}}
    }
}
