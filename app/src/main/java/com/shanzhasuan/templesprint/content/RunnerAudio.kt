package com.shanzhasuan.templesprint.content

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import android.os.SystemClock
import com.shanzhasuan.templesprint.game.Event

/** All effects and regional ambience are preloaded; stage changes do not prepare codecs on a frame. */
class RunnerAudio(context:Context) {
    private val pool=SoundPool.Builder().setMaxStreams(12).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val ids=mutableMapOf<String,Int>()
    private var region=0
    private var ambienceStream=0
    private var running=false
    private var closed=false
    private val effects=mutableMapOf<String,Int>()
    private val reported=mutableSetOf<String>()
    private val coinNames=arrayOf("coin","coin_alt1","coin_alt2")
    private val stoneSteps=arrayOf("step1","step2","step3","step4")
    private val woodSteps=arrayOf("wood1","wood2","wood3","wood4")
    private val clipNames=listOf("step1","step2","step3","step4","wood1","wood2","wood3","wood4","coin","coin_alt1","coin_alt2","jump","slide","slide_wood","hit","lane","turn","start","end","chapter","clear","ambient_0","ambient_1","ambient_2","ambient_3","roar")
    private var nextCoin=0
    private var nextStep=0
    private var footfalls=0
    private var lastRoar=-10000L
    private var lastCoin=-10000L
    private var coinCues=0
    private val stepVolume=.62f
    private val coinVolume=.95f
    private val coinDuckMillis=180L
    private val coinDuckGain=.55f
    init {
        pool.setOnLoadCompleteListener{_,id,status->
            if(closed)return@setOnLoadCompleteListener
            if(status!=0){Log.e("RunnerAudio","load failed id=$id status=$status");return@setOnLoadCompleteListener}
            val count=synchronized(loaded){loaded.add(id);loaded.size}
            if(count==clipNames.size)Log.i("RunnerAudio","ready clips=${clipNames.size} revision=0.5.3 recordedFoley=true pcmPreloaded=true coinVolume=$coinVolume stepVolume=$stepVolume")
            if(running&&id==ids["ambient_$region"]&&ambienceStream==0)startAmbience()
        }
        for(name in clipNames){
            context.assets.openFd("audio/$name.wav").use{ids[name]=pool.load(it,1)}
        }
    }
    private fun play(name:String,volume:Float=1f,priority:Int=2,trace:Boolean=false):Int{
        if(closed)return 0
        val id=ids[name]?:run{Log.w("RunnerAudio","missing clip=$name");return 0}
        if(!synchronized(loaded){id in loaded}){Log.w("RunnerAudio","not loaded clip=$name");return 0}
        effects.remove(name)?.let(pool::stop)
        val stream=pool.play(id,volume,volume,priority,0,1f)
        if(stream==0)Log.w("RunnerAudio","play failed clip=$name")
        else {effects[name]=stream;val first=reported.add(name);if(first||trace)Log.i("RunnerAudio","play clip=$name volume=$volume priority=$priority stream=$stream revision=0.5.3")}
        return stream
    }
    private fun stopEffects(){effects.values.forEach(pool::stop);effects.clear()}
    private fun stopFootsteps(){for(name in stoneSteps+woodSteps)effects.remove(name)?.let(pool::stop)}
    private fun roar(volume:Float){val now=SystemClock.elapsedRealtime();if(now-lastRoar>=2000){lastRoar=now;play("roar",volume);Log.i("RunnerAudio","guardian roar volume=$volume")}}
    private fun startAmbience(){if(closed)return;val id=ids["ambient_$region"]?:return;if(synchronized(loaded){id in loaded})ambienceStream=pool.play(id,.04f,.04f,1,-1,1f)}
    fun setRegion(index:Int){if(index==region&&ambienceStream!=0)return;pool.stop(ambienceStream);ambienceStream=0;region=index.coerceIn(0,3);if(running)startAmbience()}
    fun event(event:Event){when(event){
        Event.START->{stopEffects();nextCoin=0;nextStep=0;footfalls=0;coinCues=0;lastCoin=-10000L;lastRoar=-10000L;play("start",.65f);roar(.50f);running=true;pool.stop(ambienceStream);ambienceStream=0;startAmbience()}
        Event.COIN->{
            val name=coinNames[nextCoin]
            val stream=play(name,coinVolume,priority=3,trace=true)
            if(stream!=0){
                lastCoin=SystemClock.elapsedRealtime()
                // Lower only short footfalls; the actual coin recording keeps its natural tail.
                for(step in stoneSteps+woodSteps)effects[step]?.let{pool.setVolume(it,stepVolume*coinDuckGain,stepVolume*coinDuckGain)}
            }
            coinCues++;Log.i("RunnerAudio","coin cue=$coinCues clip=$name stream=$stream duckMs=$coinDuckMillis")
            nextCoin=(nextCoin+1)%coinNames.size
        }
        Event.JUMP->{stopFootsteps();play("jump",.82f)}
        Event.SLIDE->{stopFootsteps();val name=if(region==1)"slide_wood" else "slide";play(name,.88f);Log.i("RunnerAudio","slide cue=$name volume=0.88 duration=0.95s")}
        Event.LAND->step(.9f)
        Event.STUMBLE->{play("hit",.35f);roar(.58f)}
        Event.CAUGHT->{pause();play("end",.75f);roar(.58f)}
        Event.CHAPTER->play("chapter",.8f)
        Event.CLEAR->{pause();play("clear",.68f)}
        // The end asset already includes its impact: do not layer a second hit over it.
        Event.HIT->{pause();play("end",.67f);roar(.45f)}
        Event.PAUSE->pause()
        else->play(event.name.lowercase(),.7f)
    }}
    fun pause(){running=false;stopEffects();if(ambienceStream!=0)pool.pause(ambienceStream)}
    fun step(volume:Float=stepVolume){
        if(!running||closed)return
        val names=if(region==1)woodSteps else stoneSteps
        val gain=if(SystemClock.elapsedRealtime()-lastCoin<coinDuckMillis)volume*coinDuckGain else volume
        play(names[nextStep],gain,priority=1);nextStep=(nextStep+1)%names.size;footfalls++
        if(footfalls==1||footfalls==8||footfalls==32)Log.i("RunnerAudio","footfalls=$footfalls region=$region volume=$gain")
    }
    fun resume(){if(closed)return;running=true;if(ambienceStream==0)startAmbience()else pool.resume(ambienceStream)}
    fun close(){if(closed)return;closed=true;pool.release()}
}
