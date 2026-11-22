package com.sasch.cameragps.sharednew.bluetooth

enum class BleSessionPhase {
    Disconnected,
    Connecting,
    Connected,
    DiscoveringServices,
    ReadingConfig,
    EnablingGps,
    LockingGps,
    SyncingTime,
    RemoteStatusProbing,
    Transmitting,
    Error,
}

/** Categorized reason for a BLE connection or handshake failure. */
enum class BleFailureReason {
    /** Existing bond was removed on the phone or camera; requires re-pairing. */
    BondLost,
    /** Authentication or encryption failed after pairing retries. */
    AuthenticationFailed,
    /** Connection or handshake timed out waiting for the camera. */
    Timeout,
    /** Camera appears out of range or powered off. */
    OutOfRange,
    /** Phone Bluetooth adapter is turned off. */
    BluetoothOff,
    /** Required Bluetooth / nearby-devices permission is missing. */
    PermissionMissing,
    /** GATT service discovery, config read, or link setup failed. */
    GattFailure,
    /** Unclassified failure. */
    Unknown,
}

enum class BleFailureStage {
    Connect,
    DiscoverServices,
    Handshake,
}

data class BleFailure(
    val reason: BleFailureReason,
    val stage: BleFailureStage = BleFailureStage.Connect,
    val platformStatus: Int? = null,
)

