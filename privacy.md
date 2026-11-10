# Privacy Policy — AlphaGPS

**Publisher:** mfreceiver (AlphaGPS)
**Effective date:** 2026-11-10

AlphaGPS is an open-source app (GPL-3.0), a personal customization of
[Saschl/alpha-gps](https://github.com/Saschl/alpha-gps). It is not distributed through any app
store.

## In short

**AlphaGPS does not collect or upload any data.** There are no accounts, no analytics, no crash
reporting, no advertising, and no SDKs that send data to third parties. Everything the app
processes stays on your device or goes directly to the camera you paired with it.

## How data is used

- **Location** — read only with your permission, and only to write GPS position and time to the
  Sony camera you have paired with the app. Location is not uploaded anywhere and no location
  history is kept.
- **Bluetooth / nearby devices** — used only to discover, pair with and connect to your camera.
  The app does not scan for or record other devices.
- **Camera Wi-Fi / local network** — used only to connect to the camera's own Wi-Fi to browse and
  download photos. Downloaded photos are saved to your device's gallery; nothing is uploaded.
- **Notifications** — used only to show the connection and synchronization status of the
  foreground service.
- **Logs** — kept only on your device. The logs do not contain precise coordinates. Copying or
  sharing logs is done by you, manually, and only if you decide to.

## Permissions and what happens if you deny them

| Permission | Purpose | If denied |
|---|---|---|
| Location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`, incl. background) | Read the phone's GPS position to write it to the camera | No location can be transmitted; photos will not be geotagged |
| Bluetooth (`BLUETOOTH_CONNECT`) | Discover, pair with and connect to the camera | The camera cannot be found or connected |
| Nearby Wi-Fi devices (`NEARBY_WIFI_DEVICES`) | Join/inspect the camera's Wi-Fi network | Automatic Wi-Fi connection to the camera is unavailable |
| Local network / Wi-Fi state (`ACCESS_LOCAL_NETWORK`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`) | Connect to the camera's Wi-Fi and talk to it | Photos cannot be browsed or downloaded |
| Notifications (`POST_NOTIFICATIONS`) | Show the foreground-service status notification | The status notification is not shown; syncing still works |
| Companion device (`REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`) | Keep the camera association and restart the service when the camera returns | Reconnection when the camera comes back is limited |
| Boot (`RECEIVE_BOOT_COMPLETED`) | Restart the location service after reboot | Transmission does not resume after reboot |
| Battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Keep the foreground service alive on aggressive vendor ROMs | Some devices may stop the service in the background |
| Internet / network state | Talk to the camera over Wi-Fi; the app contacts no servers | Camera Wi-Fi features unavailable |

Legacy permissions limited to old Android versions (`WRITE_EXTERNAL_STORAGE` up to SDK 28,
`BLUETOOTH`/`BLUETOOTH_ADMIN` up to SDK 30) remain declared in the manifest for historical
builds; current installations (Android 12+) do not use them.

## Data retention and deletion

All app data (settings, associations, logs) is stored locally on your device. Uninstalling the
app or clearing its app data deletes it. Photos you have downloaded belong to your device's
gallery and are managed by you.

## Changes to this policy

This document is updated together with the app; changes are recorded in the public repository
commit history.

## Contact

Questions or feedback: open an issue at either repository —

- https://github.com/mfreceiver/alpha-gps/issues
- https://forge.example.com:443/owner/alphagps/issues
