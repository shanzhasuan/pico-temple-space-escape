package com.shanzhasuan.templesprint.content

import com.shanzhasuan.templesprint.game.Phase
import com.shanzhasuan.templesprint.game.RunnerPage

/** Each fixed-size surface owns a separate Attachment and Compose identity. */
enum class RunnerMenuPanel(val id:String,val widthDp:Int,val heightDp:Int) {
    SELECTION("runner-selection",1100,880),
    PAUSED("runner-paused",720,480),
    DEFEAT("runner-defeat",580,520),
    RESULT("runner-result",720,480);

    companion object {
        fun forState(page:RunnerPage,phase:Phase):RunnerMenuPanel = when {
            page==RunnerPage.SELECT->SELECTION
            phase==Phase.ENDED->DEFEAT
            phase==Phase.PAUSED->PAUSED
            else->RESULT
        }
    }
}
