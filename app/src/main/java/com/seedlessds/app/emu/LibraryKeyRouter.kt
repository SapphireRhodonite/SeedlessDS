package com.seedlessds.app.emu

object LibraryKeyRouter {
    @Volatile
    var cycleFilter: ((Boolean) -> Boolean)? = null

    @Volatile
    var openOptions: (() -> Boolean)? = null

    @Volatile
    var toggleFavorite: (() -> Boolean)? = null
}
