package sa.factory.maqaraccesscoworki.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.maqaraccesscoworki.engine.AccessDirection
import sa.factory.maqaraccesscoworki.engine.DynamicSpaceOccupancyEngine
import sa.factory.maqaraccesscoworki.engine.RfidNfcTurnstileProtocolEngine

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver(
    val turnstileEngine: RfidNfcTurnstileProtocolEngine = RfidNfcTurnstileProtocolEngine(),
    val spaceOccupancyEngine: DynamicSpaceOccupancyEngine = DynamicSpaceOccupancyEngine()
) {
    private val peripheral = "13.56 MHz RFID Turnstile Access + TOTP Pass"

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "13.56 MHz RFID/NFC Turnstile Controller: Handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "13.56 MHz RFID Turnstile Access + TOTP Pass: ONLINE | READY"))

        val sampleUids = listOf("04A1B2C3D4", "04E5F6A7B8", "04C9D0E1F2")
        var frameIndex = 0

        while (true) {
            delay(1500)
            frameIndex++
            val uid = sampleUids[frameIndex % sampleUids.size]
            val direction = if (frameIndex % 2 == 0) AccessDirection.ENTRY else AccessDirection.EXIT
            val (status, relayCmd) = turnstileEngine.processTurnstileScan(
                uid = uid,
                laneId = "LANE-01",
                direction = direction,
                enforceAntiPassback = false
            )

            val occupancySummary = spaceOccupancyEngine.generateOverallFacilitySummary()
            val payload = "RFID: $status [UID: $uid] | RELAY: ${relayCmd?.relayState ?: "IDLE"} | $occupancySummary"

            emit(HardwareTelemetryFrame(
                peripheralName = peripheral,
                connectionState = "ACTIVE_STREAMING",
                livePayload = payload
            ))
        }
    }
}
