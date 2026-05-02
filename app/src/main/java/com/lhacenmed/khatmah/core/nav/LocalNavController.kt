package com.lhacenmed.khatmah.core.nav

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/**
 * App-level NavHostController available to the entire composition tree.
 * Tab screens call LocalNavController.current to navigate without receiving
 * the controller as an explicit parameter.
 */
val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("LocalNavController not provided — wrap content in CompositionLocalProvider")
}