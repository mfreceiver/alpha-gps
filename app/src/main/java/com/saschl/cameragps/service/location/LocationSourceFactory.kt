package com.saschl.cameragps.service.location

import android.content.Context
import com.sasch.cameragps.sharednew.bluetooth.location.LocationSource

/** [LocationSource]: platform LocationManager only — no Play Services. */
fun createLocationSource(context: Context): LocationSource =
    PlatformLocationSource(context.applicationContext)
