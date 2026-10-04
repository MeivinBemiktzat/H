package com.ailocal.app.ui

sealed class NavDestination(val route: String) {
    object Chat : NavDestination("chat")
    object Conversations : NavDestination("conversations")
    object Actions : NavDestination("actions")
    object Models : NavDestination("models")
    object Settings : NavDestination("settings")
}
