package com.swordfish.lemuroid.app.shared.game

import com.swordfish.libretrodroid.GLRetroView

/**
 * Holds the active game's view inside the dedicated :game process so the game menu
 * can request synchronized memory snapshots without trying to access another process.
 */
object RunningGameMemory {
    @Volatile
    private var activeView: GLRetroView? = null

    fun attach(view: GLRetroView) {
        activeView = view
    }

    fun currentView(): GLRetroView? = activeView

    fun clear() {
        activeView = null
    }
}
