package com.saschl.cameragps.service.location

import android.content.Context
import com.sasch.cameragps.sharednew.bluetooth.location.LocationSource

/** No Play Services location: there is no provider to choose. */
const val LOCATION_PROVIDER_SELECTABLE = false

/** [LocationSource]: platform LocationManager only — no Play Services. */
fun createLocationSource(context: Context): LocationSource =
    PlatformLocationSource(context.applicationContext)
