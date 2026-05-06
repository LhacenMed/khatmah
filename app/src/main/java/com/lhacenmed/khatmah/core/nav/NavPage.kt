package com.lhacenmed.khatmah.core.nav

import androidx.compose.runtime.Composable

/**
 * A sub-page navigation destination.
 *
 * Each NavPage is responsible for its own Scaffold + AppTopBar — it animates
 * as a complete, self-contained screen alongside the main shell.
 *
 * Developer flow:
 *  1. Create a NavPage val; supply route (from Route.*) and a full-screen composable.
 *  2. Append it to the pages list in AppEntry.
 *
 * NavHost registration is automatic from the list.
 *
 * @param route   Navigation route string; use Route.* constants.
 * @param content Full-screen composable; owns its Scaffold, TopAppBar, and back handling.
 */
class NavPage(
    val route: String,
    val content: @Composable () -> Unit,
)