# Wi-Fi architecture

Alpha GPS uses Wi-Fi for live view, remote capture, and downloading photos. The phone joins the
camera's Wi-Fi network, opens a control connection to the camera, and opens a separate HTTP stream
for live-view images. Bluetooth can help start the camera's Wi-Fi and obtain its credentials; it
does not carry the images.

This guide follows the implementation in this checkout, including the capability-based switch to a
separate transfer session. Start with the session definitions below, then follow the connection and
browsing walkthroughs. Protocol bytes and reverse-engineering sources belong in
the [protocol evidence](wifi-remote-protocol-evidence.md).

- [What a session means](#what-a-session-means)
- [How the pieces fit together](#how-the-pieces-fit-together)
- [Connecting to a camera](#connecting-to-a-camera)
- [Opening the PTP session](#opening-the-ptp-session)
- [Live view and capture](#live-view-and-capture)
- [Browsing and switching sessions](#browsing-and-switching-sessions)
- [Downloading and saving](#downloading-and-saving)
- [Coroutines and threads](#coroutines-and-threads)
- [Disconnecting and handling failures](#disconnecting-and-handling-failures)
- [Where to read or change the code](#where-to-read-or-change-the-code)

## What a session means

PTP means Picture Transfer Protocol; PTP/IP carries it over a network. TCP is the underlying
reliable byte connection. The app opens multiple TCP connections to one camera, without joining
multiple Wi-Fi networks.

Several objects use the word “session”, but they have different lifetimes:

| Concept                                       | Meaning in this app                                                                                                                                                                | Lifetime                                                                                 |
|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| `CameraSession`                               | The per-camera state record in `CameraSessionRegistry`, including `wifiRemote`. It is data for the UI and coordinators, not a socket.                                              | Can exist before, during, and after a Wi-Fi connection.                                  |
| Controller Wi-Fi session                      | One connect attempt and its work, owned by `WifiRemoteController.runSession()`.                                                                                                    | From Connect until failure or disconnect cleanup completes.                              |
| Camera network ownership                      | The OS network request and/or network-loss monitoring.                                                                                                                             | Kept while the Wi-Fi operation needs the camera network, including PTP session switches. |
| `SonyWifiConnection`                          | The implementation behind the `WifiRemoteConnection` interface, created in `SonyWifiSession.kt`. It holds the current protocol session and exposes capture, preview, and browsing. | One instance for the controller's connection.                                            |
| Private `Session` inside `SonyWifiConnection` | The current PTP connection pair, Sony capabilities, and feature helpers.                                                                                                           | Replaced when an older camera needs a different shooting/transfer mode.                  |
| PTP protocol session                          | The camera-side state opened by `OpenSession` or Sony's `SDIO_OpenSession`, after the TCP handshakes.                                                                              | Closed before replacing the PTP connections or ending the Wi-Fi operation.               |
| `sessionScope`                                | A coroutine lifetime, not a protocol command or network connection. This name occurs in both the controller and connection code.                                                   | Cancelling it stops its child jobs; cleanup must still close external resources.         |

**A session switch for browsing keeps the camera's Wi-Fi network connected.** It closes the old PTP
session and TCP connections, then opens replacements on that same network. There are no simultaneous
shooting and transfer PTP sessions in this flow.

## How the pieces fit together

```mermaid
flowchart TD
    UI[Shared Wi-Fi remote screen] --> Controller[WifiRemoteController]
    Controller --> Registry[CameraSessionRegistry: wifiRemote state]
    Controller --> Connector[Platform connector and network ownership]
    Connector --> Sony[SonyWifiConnection]
    Sony --> Session[Current Session]
    Session --> Queue[PtpIpCommandQueue]
    Session --> Events[PtpIpEventMonitor]
    Session --> Preview[SonyLiveViewStream]
    Queue --> CommandSocket[Command TCP connection: port 15740]
    Events --> EventSocket[Event TCP connection: port 15740]
    Preview --> HTTP[Separate HTTP connection: camera-advertised URL]
    CommandSocket --> Camera[Camera]
    EventSocket --> Camera
    HTTP --> Camera
```

The command connection carries requests, replies, and photo-download data. The event connection
receives notifications such as capture completion. Both are parts of one PTP/IP connection pair.
HTTP carries the live-view image stream. An HTTP connection is not another PTP session.

The shared code owns ordering, state, and Sony protocol decisions. Platform code provides network
access, byte I/O, image decoding where needed, and saving to the phone's photo library. These pieces
are manually wired in
[AndroidWifiRemote][android-wifi]. (This fork is Android-only; the former
iOS platform wiring has been removed.)

## Connecting to a camera

The platform app graph owns one [WifiRemoteController][controller]. Android's
`AppServices` creates it. The shared
[Wi-Fi screen][screen] calls this controller through its platform host. The controller allows one
Wi-Fi camera operation at a time and keeps ownership until cleanup finishes, so a new attempt cannot
overlap a closing connection.

There are three entry paths:

| Entry point                                                   | What happens before opening PTP                                                                                                                                                                  |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `connectAutomatically()`                                      | BLE prepares the camera and reads credentials; the OS joins its Wi-Fi; the connector discovers its gateway address. Available on Android 10+.                                            |
| `connect(identifier, host)`                                   | The user has already joined the camera's Wi-Fi. Android can discover the address when the host is blank. |

> Historical note: `prepareManualConnection()` / `continueManualConnection()` implemented a manual
> join-in-Settings fallback for Android 8–9. Since `minSdk` was raised to 31, Android 10+ automatic
> connection is the only supported path and that fallback is obsolete (kept only until its code is
> removed).

For automatic setup, [SonyWifiBootstrap][bootstrap] uses the existing BLE operation queue to start
Wi-Fi and read the SSID/password, with optional BSSID and status
reads. [AutomaticWifiRemoteConnector][automatic] then owns the OS network request. An available
network is not yet sufficient: it waits for a usable camera address before opening the camera
connection.

Android selects the camera Wi-Fi `Network` and uses its `socketFactory` for both PTP and HTTP. This
keeps camera traffic on that network even when mobile data is also available. Gateway discovery uses
that network's routes, not a hard-coded camera IP.

The controller claims BLE remote controls before connecting: existing BLE shutter sequences and
remote probes finish cancellation so they do not compete with Wi-Fi shooting. This claim does not
itself shut down BLE or the GPS sender. Controls are released after Wi-Fi cleanup.

Typical automatic progress is:

```text
PreparingCamera → AwaitingNetworkApproval → JoiningNetwork → OpeningSession → Ready
```

Manual entry skips the steps it does not need. `Ready` means the protocol connection opened; live
view has its own `Waiting`, `Streaming`, or `Failed`
status. Being connected does not guarantee that the camera supports every feature.

## Opening the PTP session

[PtpIpSessionOpener][opener] establishes transport first:

1. Open the command TCP connection and send the PTP/IP command handshake.
2. Receive the camera's connection number.
3. Open the event TCP connection and attach it using that number.
4. Construct `PtpIpOpenedSession`, its command queue, and its event monitor.

All PTP commands pass through [PtpIpCommandQueue][queue], which executes one transaction at a time.
A transaction is one command, its optional data, and its response, matched by a transaction ID. The
caller suspends waiting for its result; the queue worker performs the I/O. This is a separate queue
from the BLE operation queue. The event monitor reads the other socket independently, so capture
events can arrive while a command is waiting for its response.

TCP reads return arbitrary chunks of bytes, not complete commands or images.
`CameraPtpIpConnection` and `PtpIpPacketCodec` reconstruct PTP packets from those chunks. The HTTP
and live-view decoders perform the equivalent framing work for preview data. These parsing loops are
separate from the platform socket-read loop.

[SonyPtpInitializer][initializer] then establishes the camera-side protocol state:

1. Read `GetDeviceInfo` to discover supported operations.
2. Read the device-description XML (DD) and digital-imaging-description XML (DID)
   through PTP if advertised. These contain endpoint/version/capability information.
3. Select and open the appropriate session mode.
4. Perform Sony's ordered connection handshake and read extended capabilities.
5. For a separate transfer session, read `GetDeviceInfo` again: transfer operations may only become
   visible after entering that mode.

Mode selection is capability-based, not a camera-model name check:

| Advertised capabilities                                                            | Open operation                                              | Browsing behavior                                                              |
|------------------------------------------------------------------------------------|-------------------------------------------------------------|--------------------------------------------------------------------------------|
| DID server major version 4 or newer                                                | Sony `0x9210 [1, 2]`: combined remote shooting and transfer | Keep the PTP session while browsing.                                           |
| DID major version 3, explicit media-server `Enable`, and Sony open-session support | Sony `0x9210 [1, 0]` for shooting; `[1, 1]` for transfer    | Replace the PTP session when entering/leaving browsing.                        |
| Neither selection above                                                            | Standard `0x1002 [1]`                                       | Keep the session; offer browsing only if its advertised operations support it. |

The DID server version is a protocol capability value, **not the camera firmware version**. The `1`
in these open commands is the PTP session ID; the second Sony parameter selects the mode. It is
unrelated to the handshake's connection number.

The modern combined mode remains preferred. The separate mode is a compatibility path, and still
requires usable transfer operations after initialization. Currently DD/DID discovery is through PTP
only; there is no SSDP/HTTP description discovery or DLNA browsing backend. The ZV-1's actual
behavior needs hardware confirmation; the existence of a Sony phone-transfer feature alone does not
prove that this PTP path applies to it.

## Live view and capture

The controller starts `previewJob`, which collects `connection.images`.
[SonyLiveViewEndpointResolver][endpoint] first tries the DD live-view URL, then the camera's
live-view property. URLs must refer to the camera peer.

[SonyLiveViewStream][liveview] enables the advertised modern preview controls when required, opens
the HTTP stream, and feeds received bytes into
`SonyLiveViewFrameDecoder`. That decoder extracts frames from Sony's framing; image decoding then
produces the `ImageBitmap` displayed by Compose. A one-frame buffer drops older waiting frames so
the preview does not accumulate a backlog. Legacy sessions skip the modern preview-enable controls.

Cancelling `previewJob` closes the HTTP stream and releases the preview controls in `finally`. **It
leaves PTP open** so the app can browse photos or start another preview. The active HTTP
implementation is [CameraHttpTransport][http] over
`CameraByteConnection`; Android's factory supplies `AndroidCameraHttpTransport`. The separate
`AndroidSonyLiveViewHttpTransport` class is not the transport wired by the current Android
connector.

Capture follows a different path: controller → [SonyPtpShutter][shutter] → PTP command queue. It
presses and releases the shutter controls and subscribes for capture completion before the full
press. Acceptance of a command is not proof that a photo completed. Missing confirmation produces
`Uncertain`, and the app does not automatically retry a shutter command that may already have fired.

## Browsing and switching sessions

The entry point is `WifiRemoteController.browsePhotos()`. It calls
`previewJob.cancelAndJoin()` before `connection.openPhotoBrowser()`: the old preview's cleanup must
finish before changing camera mode. Capture is also blocked while browsing.

For a modern combined session, `openPhotoBrowser()` uses the existing
`SonyImageTransfer`. Returning disables browser transfer mode and restarts the HTTP preview on the
same PTP connections. “Combined” describes protocol support; our UI still pauses live view while
browsing.

For the separate-session path, the flow is:

```mermaid
sequenceDiagram
    participant UI as User
    participant C as WifiRemoteController
    participant S as SonyWifiConnection
    participant Camera
    UI ->> C: Browse photos
    C ->> C: Cancel and join live view (HTTP cleanup)
    C ->> S: openPhotoBrowser()
    S ->> Camera: Close shooting PTP session and both TCP connections
    Note over S, Camera: Camera Wi-Fi network remains connected
    S ->> Camera: New TCP pair, initialize ImageTransfer (mode 1)
    S ->> Camera: Enable transfer and read photo list
    S -->> C: Photo page
    UI ->> C: Return to shooting
    C ->> S: closePhotoBrowser()
    S ->> Camera: End transfer, close transfer PTP session and TCP pair
    S ->> Camera: New TCP pair, initialize RemoteShooting (mode 0)
    S -->> C: Shooting session ready
    C ->> C: Start live view (new HTTP stream)
```

The actual replacement lives in `SonyWifiConnection.replaceSession()` in
[SonyWifiSession.kt][sony-session]. It detaches the old loss monitor, closes the previous session,
opens and initializes a new pair, then installs a fresh runtime and loss monitor. Detaching matters:
an intentionally closed event socket must not be interpreted as unexpected loss of the whole camera
connection.

Each replacement gets fresh command transaction IDs, capability information, and feature helpers.
Photo handles identify objects in the current camera session; they must not be assumed stable across
mode switches. Saved-download markers are therefore cleared when leaving a separate transfer
session.

A failed switch is reported through the browser's failure state. The app does not resume live view
until reopening shooting succeeds. Retry/return can attempt the transition again. Disconnecting from
the browser uses
`prepareToDisconnect()`, which ends transfer without needlessly reopening shooting.

## Day list, day grid, and thumbnails (UI v3)

The Wi-Fi screen enters the grouped browser automatically once the session is ready
(`WifiRemoteViewModel.maybeAutoEnterBrowser()` → `browseDates()`):

- **Connection overlay.** While connecting, a fixed-size card shows the steps as a segmented ring.
  `wifiConnectionFrame()` in [WifiBrowserModel.kt][browser-model] maps the unchanged controller
  phases to frames: `PreparingCamera` → prepare, `AwaitingNetworkApproval` → approve,
  `JoiningNetwork`/`OpeningSession` → join, `Ready` with the first catalog read still running →
  read catalog, then a short green “ready” frame before the day list. `Failed` shows a red icon and
  ends by a tap, Back, or after 5 s (the permission failure instead offers app settings and waits);
  there is no help or retry button — entering again is the retry. Cancel ends the session.
- **Day list.** `openCatalog()` enumerates the whole card once (it always did: the v4 content
  catalog and `GET_OBJECT_HANDLES` both list every object, and the result is kept in memory as a
  `CatalogSnapshot`). The days and their counts come from that snapshot, so the list adds no camera
  traffic. Leaving the day list ends the Wi-Fi session (Bluetooth stays); refresh re-enumerates and
  starts a new `browserGeneration`.
- **Day grid without pages.** `dayPhotos(dateKey)` returns the whole day from the snapshot; there is
  no page inside a day any more. The transfer's “displayed set” gate (thumbnails, previews and
  metadata are only served for handles of the displayed day) is unchanged — it just covers the whole
  day now. Downloads still resolve handles against the full catalog, so a selection spanning days
  downloads correctly.
- **Stale-view safety.** Every `browseDate()` publishes a new `pageRevision` on the same generation;
  `expectedRevision` refuses a day switch issued from an earlier view (A → B → A), the thumbnail
  demand carries the revision, and a refresh bumps the generation (selection snapshots and range
  gestures are rejected on either change). This is the same mechanism as for pages before, keyed by
  day instead of `(day, offset)`.
- **Viewport-driven thumbnails.** The grid reports its visible captures plus two rows of margin
  (`requestThumbnails`). One serial loader per day takes at most 12 per batch, re-checks before every
  request that the tile is still wanted (fast scrolling drops not-yet-started requests; the request
  in flight is never cancelled), pauses while a download or preview holds the PTP queue, and stays
  idle without a viewport. Because a day can now be long, decoded thumbnails beyond 300 are evicted
  oldest-first (never the current demand) and are fetched again when scrolled back to.
- **Selection and download.** Long-press selects; dragging after a long-press selects a contiguous
  range within the day. The floating download button follows the settings' download format
  (`WifiDownloadFormatSetting`, key `wifi_default_download_format`): JPG plans one JPG per capture,
  RAW one RAW (ARW) per capture — captures without that format are skipped and counted (`NoJpeg` /
  `NoRaw`), never replaced by another format — and JPG + RAW downloads every original file. "Ask each
  time" (the default since 0.3.1) opens an in-page chooser (JPG / RAW / JPG + RAW, each planned by the
  same `planDownloads` with its count, size and skips) before the batch starts, and the viewer asks the
  same way only when the photo has more than one format; the pick is used once and not stored. The
  batch summary is recorded before the batch is submitted, so a
  summary latched by an earlier batch can neither reappear nor settle on the new batch's result.

## Downloading and saving

[SonyImageTransfer][transfer] selects the supported browsing implementation:
Sony's content catalog where advertised, otherwise the storage/object-handle path. The latter reads
object metadata using `GetObjectInfo`, or MTP's
`GetObjectPropList` if that is what the camera advertises. MTP here is a set of media-transfer
operations carried over the PTP/IP connection; it does not mean another HTTP stream.

A download revalidates metadata and reads the photo in chunks through the PTP command queue. It
writes those chunks to the platform's `CameraImageDestination`. The destination contract is
deliberately split:

```text
write(bytes), repeatedly → prepare() → cancellation check → commit()
                                  failure/cancellation → abort()
```

- `write()` stores incoming bytes.
- `prepare()` finishes any preparation, such as HEIF-to-JPEG conversion.
- `commit()` finishes publishing the completed photo to the phone's library.
- `abort()` removes incomplete output; it does not remove a successfully committed photo.

On modern Android, the photo is written into a pending MediaStore entry. Commit closes the stream
and clears `IS_PENDING`, exposing that same file to the gallery. Older Android uses a temporary file
followed by rename and a media scan. Conversion adds temporary files that are cleaned up
separately.

Once commit starts it runs inside `NonCancellable`, allowing the app to determine whether the save
succeeded and retain a completed photo. Cancellation is checked before that boundary. If
cancellation arrives during a successful commit, that photo can still be reported as saved; the
batch checks cancellation before starting the next photo.

## Live status and photo metadata (data layer only)

Two read-only data layers sit beside the Wi-Fi remote. Neither draws UI; pages consume their flows.

**Per-camera live status.** `CameraSessionOrchestrator.liveStatus` (a `CameraLiveStatusHub`) exposes
`statuses: StateFlow<Map<String, CameraLiveStatus>>` and `observe(address)`. It merges the registry's
`CameraSession` (phase, MTU) with telemetry recorded by the BLE and Wi-Fi layers:

- time-sync outcome (`TimeSyncStatus`: NotRun/InProgress/Applied/Unconfirmed/Rejected/Failed/Unsupported);
- GPS write counters: `queuedCount` (packets accepted into the GATT queue) is kept apart from
  `writtenCount` (GATT write confirmations) and `failedCount`, plus last success/failure times. Counters
  reset when a BLE session starts; timestamps survive disconnects, and the last write time is persisted
  through `CameraStatusPersistence` (SharedPreferences, throttled to once a minute) so it can be shown while
  disconnected. No coordinates are recorded;
- signal: `acquireSignalMonitoring(address)` returns a refcounted lease; while at least one lease is open
  and the session is `Transmitting`, `readRemoteRssi` runs every 10 s, bypassing the ATT operation queue
  (so a lost callback can never delay GPS writes). Level mapping is the pure `signalLevelFromRssi`;
- battery: one best-effort read after the PTP session is ready (`SonyBatteryReader`), cached with the
  time it was obtained (`CameraBattery.freshness`).

**Photo metadata.** `WifiRemoteController.loadMetadata(identifier, handle, extended)` publishes
`metadata: StateFlow<PhotoMetadataState>` (Idle/Loading/Ready/Unavailable(reason)). Basic data (format,
size, capture time, ObjectInfo resolution/protection on the v3 path) comes from the catalog and costs no
traffic. With `extended` and a JPEG, `PhotoMetadataLoader` waits 250 ms (a swipe cancels the previous
request), waits for `thumbnailHolds == 0`, holds the camera, then reads the first 64 KB of the original
with one partial read (`SonyImageTransfer.metadataHead`) and parses EXIF with `ExifMetadataParser`.
Results are cached per handle (LRU, 200) and dropped when the browser generation changes or the session
ends. A browse/download interrupts a pending read; failures only yield `Unavailable` and never touch
preview or download state. RAW/HEIF stay basic.

**Viewer use.** The full-screen viewer (`WifiPhotoViewer`) requests the shown capture's preview file
through `WifiRemoteViewModel.showViewerMetadata(extended = true)` on entry, after every settled swipe
and after a download, whether or not the details are visible: the loader's 250 ms debounce makes it
"read the header once the user stays on a photo", so the details usually show real values when opened.
`viewerWantsExtendedMetadata(transferBusy, swiping)` gates it: no request while a transfer is busy, and
a swipe that starts calls `pauseViewerMetadata()` (→ `pauseMetadata` → `interrupt`, the pending or
in-flight read falls back to the basic data) because the photo is being left. Opening or closing the
details drawer requests nothing (its flag is not an input of the request); a repeat request for a cached
handle is served without a camera read. The viewer never prefetches neighbours or the whole day, and
calls `stopViewerMetadata()` (→ `cancelMetadata`) when it closes. The histogram is computed inside the
details (drawer / side panel) only, from the decoded preview bitmap on `Dispatchers.Default` (rows
sampled so at most ~40 000 pixels are read); `Unavailable` just hides the shooting parameter rows.

## Coroutines and threads

A scope groups work by lifetime. A dispatcher decides where work executes. Neither a new scope nor
`launch` implies a new dedicated thread.

```text
App scope (Main.immediate)
└── Controller runSession job
    ├── Connection-loss watcher
    ├── Capture request consumer
    ├── Preview job (while shooting)
    └── Photo work / photo preview jobs (while browsing)

SonyWifiConnection protocolScope (same dispatcher, independent SupervisorJob)
├── Current protocol Session scope
│   ├── PTP command worker
│   └── PTP event worker and session-close watcher
└── Current session loss monitor
```

The second scope deliberately survives cancellation of the controller job. Cleanup still needs a
working command queue to release controls and send
`CloseSession`. `SonyWifiConnection.close()` explicitly cancels this scope after closing the current
protocol session. Its `SupervisorJob` is not a background thread and is not permission to let work
outlive the connection indefinitely.

| Work                                                       | Where it executes or waits                                                                   |
|------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Shared session state, queue coordination, UI state updates | `Main.immediate`; callbacks deliver results into channels/flows.                             |
| Android socket reads/writes and file I/O                   | `Dispatchers.IO`; blocking Java calls occupy a worker while the caller suspends.             |
| Frame and photo decoding                                   | `Dispatchers.Default`; Android HEIF conversion currently uses IO.                            |

`delay`, channel waits, and other cancellable suspension points normally notice cancellation
themselves. Android's blocking `InputStream.read()` does not. Its adapter uses a one-second read
timeout and checks cancellation before retrying:

```kotlin
// Simplified from AndroidCameraByteConnection.read().
withContext(Dispatchers.IO) {
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = try {
            input.read(buffer)
        } catch (_: SocketTimeoutException) {
            continue
        }
        // Return bytes, or end-of-stream when count == -1.
        break
    }
}
```

The check throws if the job was cancelled. It does not interrupt a read already in progress; the
socket timeout bounds the wait before the next check. This is why cancelling and joining preview can
wait for the blocking read to return. Other useful checkpoints are between native conversion stages,
before entering non-cancellable commit, and in error handlers that must distinguish intentional
cancellation from failure. A `suspend` function declaration alone does not guarantee that its
implementation checks cancellation.

## Android visibility, downloads, and explicit exit

BLE location/time writing and Wi-Fi photo transfer have separate lifetimes. Home, lock screen,
switching apps, task removal, and root Back do **not** actively close GATT, stop GPS writing, or
stop the foreground service while connection demand remains. Device-page **Disconnect** still
persists the user's disabled intent, uses the existing confirmation and per-device teardown, and
prevents CDM/restart from reconnecting that device. App disable, Bluetooth loss, and the existing
no-demand/presence retirement paths are not UI exits and retain their service cleanup rules.

`MainActivity` sends immediate `onStart`/`onStop` visibility to `AppServices` (configuration changes
and stale activity owners excluded). The app-scoped [controller][controller], not the composition,
applies [BackgroundWifiPolicy](../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/BackgroundWifiPolicy.kt):

| Event | Wi-Fi / browser / network request | BLE / GPS / demanded FGS |
|---|---|---|
| Home / lock / switch, no user download | Cancel and close immediately | Keep |
| Home / lock / switch, accepted single or batch download | Retain until the entire download job finishes | Keep |
| That download succeeds, fails, or is cancelled while still backgrounded | Close automatically after download cleanup | Keep |
| Return visible before it finishes | Cancel pending automatic close and its deadline | Keep |
| Swipe task / root Back / finishing activity | Cancel even a download and close immediately | Keep |
| User explicitly disconnects a device | Close its Wi-Fi, then its BLE via the existing manual path | Other devices unchanged |

Only the accepted `downloadPhotos` job qualifies (single-photo download uses the same path).
The flag covers the full batch, including conversion, gallery preparation/commit, and cancellation
cleanup; a file boundary does not end the exception. Thumbnail fetching, preview loading, catalog
loading, a busy-looking UI, and a settled/pending batch summary never qualify. Screen disposal and
`WifiRemoteViewModel.onCleared` in the background consult the same policy; navigating out explicitly still closes. Returning to the
foreground never starts a closed session. The Android entry-attempt latch is saved across activity
recreation, so a restored browser cannot silently request camera Wi-Fi again; the user must start
photo transfer/reconnect explicitly.

During retention the [automatic connector][automatic] keeps the same
[Android network request][android-network] registered until normal connection cleanup releases it.
The acquisition timeout (60 seconds) does not expire an already acquired request. A separate
30-minute background-download deadline cancels a stuck download, logs
`Wi-Fi background download retention timed out; attempt=…`, and runs normal abort/connection/network
cleanup. Repeated stop/dispose notifications cannot extend it. Returning visible retires it; the
captured background epoch, session attempt and owner prevent a cancelled old deadline from closing
a replacement session, including a same-camera reconnect. The timer is coroutine-scheduled, not an
exact wakeup alarm: Android/OEM suspension or force-stop can delay execution or kill the process;
FGS/network retention cannot promise survival against those policies. Real-device lock-screen and
long-download testing remains required. Existing pending-write abort versus already-committed-file
semantics are unchanged.

Explicit exit still passes through `AppExitGate`'s owner/epoch and frozen retirement snapshot;
its counterexample tests are retained. `AppExitCoordinator.disconnectEverything` now has **only**
a Wi-Fi close capability, bounded by the existing three-second coordinator wait; it has no BLE,
GPS, session-shutdown or FGS-stop callback. The controller's cancellation-safe cleanup can outlive
that wait, so the bound is not a promise that a non-cancellable protocol/gallery cleanup finishes
within three seconds. `LocationSenderService.onTaskRemoved` no longer calls `stopSelf`, and
finishing the activity no longer calls `stopService`.

### Accepted late task-removal delivery risk

Android 15 AOSP `ActivityTaskSupervisor.cleanUpRemovedTask` → `ActiveServices.cleanUpServices`
posts service enumeration to the system-server Handler; the first unowned `onTaskRemoved` delivery
can therefore arrive after an old flow completes and a new activity/session starts. When the old
flow was already completed at retirement, or the gate has no owned predecessor to reject, that
sequence is indistinguishable locally from a genuine new task removal. The gate intentionally
keeps the real-removal behavior instead of adding a timing heuristic. Root Back still uses
`finishAndRemoveTask`, eliminating the old still-listed-card path. The remaining first-delivery
window is accepted: it can close a replacement **Wi-Fi** session (and cancel its download), never
new GATT/GPS or the FGS. This is the reassessment of former todo P3 #8 (AOSP delivery analysis,
2026-11-09); its completed assessment is removed from the pending-only todo list, not claimed as
an Android delivery fix.

## Disconnecting and handling failures

`disconnect()` sets the controller state to `Closing` and cancels its top-level job. The coroutine
hierarchy stops preview, photo work, and capture handling; their `finally` blocks run before the
outer connection cleanup finishes.
`closeAndJoin()` additionally waits for that cleanup, when a caller needs the resources fully
released before proceeding.

The outer cleanup uses `NonCancellable` so a user cancellation cannot skip it:

1. End browser transfer mode when appropriate, without reopening shooting.
2. On an explicit disconnect without an earlier connection failure, attempt the supported camera
   Wi-Fi shutdown command. The result may be unavailable or unconfirmed.
3. Close the Sony/PTP session, stop its queue/event monitor, and close both sockets.
4. Cancel the protocol scope and release platform monitoring/network ownership. The automatic
   connector releases its OS network request after the camera connection closes.
5. Clear images, release BLE remote controls, and publish `Idle` or `Failed`.

Stopping live view alone performs only preview cleanup. Switching a PTP session performs protocol
cleanup but retains network ownership. Full disconnect releases all of these layers. Releasing an OS
request does not guarantee that every manually joined network is disconnected or that the camera has
powered Wi-Fi off.

Connection failures and feature failures are distinct. An unexpected network or PTP event-channel
loss ends the connection; a failed preview can leave capture available. Browser/transfer errors stay
in their feature state so the user can retry or return. Shutter timeouts remain uncertain rather
than prompting a blind retry.

Per-camera connection and feature state lives in `CameraSession.wifiRemote` using
[WifiRemoteState][state]. Large image payloads live in the controller's separate
`image`, `thumbnails`, and `photoPreview` flows. This keeps the registry focused on state rather
than carrying a new bitmap on every preview frame.

## Where to read or change the code

Read the first three rows in order for the ownership story; follow the feature helpers only when
investigating that feature.

| Question                                                      | Starting point                                                                                                                            |
|---------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Who owns Connect, Browse, Back, Disconnect, and UI state?     | [WifiRemoteController][controller]: `runSession`, `browsePhotos`, `leavePhotoBrowser`.                                                    |
| What stays alive when we switch modes?                        | [SonyWifiSession][sony-session]: `SonyWifiConnection`, private `Session`, `replaceSession`, `close`.                                      |
| What opens the sockets versus the camera protocol session?    | [PtpIpSessionOpener][opener], then [SonyPtpInitializer][initializer].                                                                     |
| How do requests stay ordered and responses find their caller? | [PtpIpCommandQueue][queue]: one worker, transaction IDs, deferred results; [PtpIpEventMonitor][events] for asynchronous events.           |
| How does the phone join camera Wi-Fi?                         | [AutomaticWifiRemoteConnector][automatic], then [AndroidCameraNetworkRequest][android-network]. |
| Where does live view start/stop?                              | [SonyLiveViewStream][liveview] and [CameraHttpTransport][http].                                                                           |
| Why is a shutter command accepted but the UI uncertain?       | [SonyPtpShutter][shutter] and its capture-event wait.                                                                                     |
| Which browsing operations are supported?                      | [SonyImageTransfer][transfer], [SonyContentCatalog][catalog], [SonyMtpObjectInfo][mtp].                                                   |
| Where are native sockets and gallery saves?                   | [Android platform files][android-dir].                                                                                                     |

The [shared Wi-Fi tests][tests] are useful executable examples.
`SonyWifiSessionTest` covers session replacement, cancellation, and preserving network ownership;
`WifiRemoteControllerTest` covers UI-facing lifetimes;
`SonyPtpInitializerTest` covers mode selection; `SonyLiveViewStreamTest` covers preview control
cleanup; and `SonyImageTransferTest` covers download behavior. The Android camera simulator
currently covers remote/live-view flows, not gallery browsing/downloads. Fake-protocol tests do not
establish real-camera compatibility.

For on-device investigation, use the [Android testing guide](wifi-remote-android-testing.md). Useful
log stages are `Wi-Fi remote attempt=…`, `Wi-Fi PTP switching to …`,
`Wi-Fi Sony stage=…`, and `Wi-Fi live view …`: they identify the ownership, session-selection,
initialization, and preview layers respectively.

[controller]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/WifiRemoteController.kt
[sony-session]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyWifiSession.kt
[opener]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/PtpIpSessionOpener.kt
[initializer]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyPtpInitializer.kt
[queue]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/PtpIpCommandQueue.kt
[events]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/PtpIpEventMonitor.kt
[automatic]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/AutomaticWifiRemoteConnector.kt
[bootstrap]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyWifiBootstrap.kt
[liveview]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyLiveViewStream.kt
[endpoint]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyLiveViewEndpointResolver.kt
[http]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/CameraHttpTransport.kt
[shutter]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyPtpShutter.kt
[transfer]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyImageTransfer.kt
[catalog]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyContentCatalog.kt
[mtp]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/SonyMtpObjectInfo.kt
[state]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/WifiRemoteState.kt
[screen]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiRemoteScreen.kt
[browser-model]: ../sharednew/src/commonMain/kotlin/com/sasch/cameragps/sharednew/ui/remote/WifiBrowserModel.kt
[android-wifi]: ../sharednew/src/androidMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/AndroidWifiRemote.kt
[android-network]: ../sharednew/src/androidMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/AndroidCameraNetworkRequest.kt
[android-dir]: ../sharednew/src/androidMain/kotlin/com/sasch/cameragps/sharednew/remote/wifi/
[tests]: ../sharednew/src/commonTest/kotlin/com/sasch/cameragps/sharednew/remote/wifi/
