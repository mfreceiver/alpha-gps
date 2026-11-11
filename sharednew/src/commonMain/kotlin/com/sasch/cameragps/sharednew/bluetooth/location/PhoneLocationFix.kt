package com.sasch.cameragps.sharednew.bluetooth.location

/**
 * Quality of the phone's most recent location fix, for display only: the horizontal accuracy
 * radius (Android `Location.accuracy`, iOS `horizontalAccuracy`; ≤ 0 = unknown) and the fix's own
 * epoch timestamp. Deliberately carries no coordinates.
 */
data class PhoneLocationFix(
    val accuracyMeters: Double,
    val timeMillis: Long,
)
