package com.shanzhasuan.templesprint.game

enum class Chapter(val title:String,val description:String,val road:String) {
    JUNGLE("丛林遗迹","跨过树根，熟悉跳跃、滑铲和换道","path"),
    CANYON("峡谷栈桥","跳过断桥和火焰，沿金币路线绕开石块","bridge"),
    CORRIDOR("机关回廊","滑过石门，绕开双路石块，跳过火焰","path"),
    SANCTUM("圣坛逃脱","穿过连续机关与断路，冲向黄金终点门","path");
    val start get()=ordinal*192f
    val end get()=if(this==SANCTUM)760f else start+192f
    companion object {
        fun at(distance:Float)=entries[(distance/192f).toInt().coerceIn(0,entries.lastIndex)]
        fun atLeg(leg:Int)=entries[(leg/3).coerceIn(0,entries.lastIndex)]
    }
}
