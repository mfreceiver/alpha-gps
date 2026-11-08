package com.saschl.cameragps.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.sasch.cameragps.sharednew.bluetooth.SonyBluetoothConstants.locationTransmissionNotificationId
import com.sasch.cameragps.sharednew.notification.TransmissionNotificationCoordinator

/** Renders shared transmission state into the service's existing ongoing notification. */
internal class AndroidTransmissionNotificationPublisher(
    private val context: Context,
    private val isChannelEnabled: (String) -> Boolean = { channelId ->
        context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    },
    private val postNotification: (Int, Notification) -> Unit = { id, notification ->
        NotificationsHelper.showNotification(context, id, notification)
    },
) : TransmissionNotificationCoordinator.Publisher {
    private var previousCameraCount = 0

    override suspend fun show(cameraCount: Int) = publish(cameraCount)

    // A single ready camera gets a "Connected to camera" title with the camera's name.
    override suspend fun showSingleCamera(cameraCount: Int, cameraName: String?) =
        publish(cameraCount, cameraName)

    // A foreground service must retain its notification even with no transmitting cameras.
    override fun showIdle() = publish(0)

    private fun publish(cameraCount: Int, singleCameraName: String? = null) {
        // A disabled disconnect-alert channel must not block foreground status updates.
        val decreasing = cameraCount < previousCameraCount
        val disconnectAlert = decreasing &&
                isChannelEnabled(NotificationsHelper.DISCONNECT_NOTIFICATION_CHANNEL)
        val channelId = when {
            disconnectAlert -> NotificationsHelper.DISCONNECT_NOTIFICATION_CHANNEL
            cameraCount > 0 -> NotificationsHelper.TRANSMISSION_NOTIFICATION_CHANNEL
            else -> NotificationsHelper.NOTIFICATION_CHANNEL_ID
        }
        val notification = when {
            cameraCount == 0 -> NotificationsHelper.buildWaitingNotification(context, channelId)
            cameraCount == 1 -> NotificationsHelper.buildSingleCameraNotification(
                context,
                singleCameraName,
                channelId,
                silent = decreasing && !disconnectAlert,
            )
            else -> NotificationsHelper.buildNotification(
                context,
                cameraCount,
                channelId,
                silent = decreasing && !disconnectAlert,
            )
        }
        postNotification(locationTransmissionNotificationId, notification)
        previousCameraCount = cameraCount
    }
}
