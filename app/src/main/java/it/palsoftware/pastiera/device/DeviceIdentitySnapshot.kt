package it.palsoftware.pastiera.device
data class DeviceIdentitySnapshot(
    val stableId: String?,
    val displayName: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val board: String,
    val buildDisplay: String,
    val buildFingerprint: String
)
