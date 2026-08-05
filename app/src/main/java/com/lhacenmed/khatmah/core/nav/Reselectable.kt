package com.lhacenmed.khatmah.core.nav

/**
 * Implemented by a tab body that answers to its bar item being tapped while already showing.
 *
 * What that means is each tab's own business — the list tabs scroll back to the top, the Quran
 * tab reopens the mushaf where reading stopped. Tabs that have no such answer simply don't
 * implement it, and MainActivity leaves them alone.
 */
interface Reselectable {
    fun onReselect()
}
