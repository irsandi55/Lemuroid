package com.swordfish.lemuroid.app.shared.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swordfish.libretrodroid.GLRetroView

/**
 * Observable access to the active view in the dedicated game process.
 * The view is only exposed while its game activity is alive.
 */
object RunningGameMemory {
    private var activeView by mutableStateOf<GLRetroView?>(null)

    fun attach(view: GLRetroView) {
        activeView = view
    }

    fun currentView(): GLRetroView? = activeView

    fun clear() {
        activeView = null
    }
}
