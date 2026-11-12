package com.shanzhasuan.templesprint.game

enum class RunnerPage { SELECT, ENTERING, GAME }

/** Selection is a menu action; only completing entry starts the simulation. */
class RunnerNavigation(private val game:RunnerGame) {
    var page=RunnerPage.SELECT;private set
    var resourcesReady=false;private set
    fun markResourcesReady(){resourcesReady=true}
    fun select(index:Int){if(page==RunnerPage.SELECT)game.chooseChapter(index)}
    fun enter():Boolean {
        if(!resourcesReady||page==RunnerPage.ENTERING)return false
        if(page==RunnerPage.GAME&&game.phase !in listOf(Phase.ENDED,Phase.CLEARED))return false
        game.chooseChapter(game.selectedChapter);page=RunnerPage.ENTERING;return true
    }
    fun finishEntry(){if(page==RunnerPage.ENTERING){game.start();page=RunnerPage.GAME}}
    fun returnToSelection(){game.chooseChapter(game.selectedChapter);page=RunnerPage.SELECT}
}
