package sa.factory.maqaraccesscoworki

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.maqaraccesscoworki.data.repository.MaqarAccessRepositoryImpl
import sa.factory.maqaraccesscoworki.engine.*
import sa.factory.maqaraccesscoworki.hardware.HardwareSensorDriver
import sa.factory.maqaraccesscoworki.model.MaqarAccessItem

class MaqarAccessRegressionTest {

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = MaqarAccessRepositoryImpl(sensorDriver)

        val item1 = MaqarAccessItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = MaqarAccessItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testValidMifareUidValidation() {
        val engine = RfidNfcTurnstileProtocolEngine()

        // Valid 4-byte and 7-byte hex UIDs
        assertTrue(engine.validateUidFormat("04A1B2C3D4"))
        assertTrue(engine.validateUidFormat("04E5F6A7B8"))
        assertTrue(engine.validateUidFormat("A1B2C3D4"))

        // Invalid UIDs (odd length, invalid chars, too short)
        assertFalse(engine.validateUidFormat("XYZ123"))
        assertFalse(engine.validateUidFormat("12345"))
        assertFalse(engine.validateUidFormat(""))
    }

    @Test
    fun testTurnstileSectorKeyAuthentication() {
        val engine = RfidNfcTurnstileProtocolEngine()

        // Valid standard transport key
        assertTrue(engine.authenticateSectorKey(1, "FFFFFFFFFFFF"))

        // Invalid keys or out-of-bounds sectors
        assertFalse(engine.authenticateSectorKey(1, "000000000000"))
        assertFalse(engine.authenticateSectorKey(-1, "FFFFFFFFFFFF"))
        assertFalse(engine.authenticateSectorKey(50, "FFFFFFFFFFFF"))
    }

    @Test
    fun testAuthorizedTurnstileBadgeEntry() {
        val engine = RfidNfcTurnstileProtocolEngine()

        val (status, relayCmd) = engine.processTurnstileScan(
            uid = "04A1B2C3D4",
            laneId = "TURNSTILE-LANE-01",
            direction = AccessDirection.ENTRY,
            enforceAntiPassback = false
        )

        assertEquals(AccessStatus.GRANTED, status)
        assertNotNull(relayCmd)
        assertEquals("TURNSTILE-LANE-01", relayCmd?.laneId)
        assertEquals(500, relayCmd?.pulseDurationMs)
        assertEquals("ACTIVE_MOMENTARY_OPEN", relayCmd?.relayState)
        assertTrue(relayCmd?.formattedPayload?.contains("PULSE_OPEN:500MS") == true)
    }

    @Test
    fun testSuspendedOrUnregisteredBadgeRejection() {
        val engine = RfidNfcTurnstileProtocolEngine()

        val (status, relayCmd) = engine.processTurnstileScan(
            uid = "99887766AA",
            laneId = "TURNSTILE-LANE-01",
            direction = AccessDirection.ENTRY
        )

        assertEquals(AccessStatus.DENIED_SUSPENDED, status)
        assertNull(relayCmd)
    }

    @Test
    fun testAntiPassbackGuard() {
        val engine = RfidNfcTurnstileProtocolEngine()

        // First scan should pass
        val (firstStatus, _) = engine.processTurnstileScan(
            uid = "04E5F6A7B8",
            laneId = "LANE-01",
            direction = AccessDirection.ENTRY,
            enforceAntiPassback = true
        )
        assertEquals(AccessStatus.GRANTED, firstStatus)

        // Immediate second entry scan without exiting should trigger anti-passback violation
        val (secondStatus, secondRelay) = engine.processTurnstileScan(
            uid = "04E5F6A7B8",
            laneId = "LANE-01",
            direction = AccessDirection.ENTRY,
            enforceAntiPassback = true
        )
        assertEquals(AccessStatus.DENIED_ANTI_PASSBACK, secondStatus)
        assertNull(secondRelay)
    }

    @Test
    fun testCoworkingZoneOccupancyCalculation() {
        val occupancyEngine = DynamicSpaceOccupancyEngine()

        val rate = occupancyEngine.calculateOccupancyRate("ZONE-FLEX-01")
        // 18 / 40 = 45.0%
        assertEquals(45.0, rate, 0.01)

        val comfort = occupancyEngine.evaluateComfortLevel(rate)
        assertEquals(ZoneComfortLevel.BALANCED_COLLABORATION, comfort)

        val telemetry = occupancyEngine.getZoneTelemetry("ZONE-FLEX-01")
        assertNotNull(telemetry)
        assertEquals(22, telemetry?.availableSpots)
        assertFalse(telemetry?.ecoModeActive ?: true)
    }

    @Test
    fun testDynamicDeskCheckInAndCheckOut() {
        val occupancyEngine = DynamicSpaceOccupancyEngine()

        // Check into Boardroom which was at 0 occupancy and eco mode active
        val initialBoardroom = occupancyEngine.getZoneTelemetry("ZONE-BOARDROOM-04")
        assertTrue(initialBoardroom?.ecoModeActive == true)

        val checkInOk = occupancyEngine.checkInMember("ZONE-BOARDROOM-04", "DESK-BR-01", "MEM-101")
        assertTrue(checkInOk)

        val afterCheckIn = occupancyEngine.getZoneTelemetry("ZONE-BOARDROOM-04")
        assertFalse(afterCheckIn?.ecoModeActive == true)
        assertEquals(1, afterCheckIn?.occupancyRate?.let { (it * 16 / 100.0).toInt() })

        // Check out member
        val checkOutOk = occupancyEngine.checkOutMember("DESK-BR-01")
        assertTrue(checkOutOk)

        val afterCheckOut = occupancyEngine.getZoneTelemetry("ZONE-BOARDROOM-04")
        assertTrue(afterCheckOut?.ecoModeActive == true)
    }

    @Test
    fun testHardwareSensorDriverEmission() = runBlocking {
        val driver = HardwareSensorDriver()
        val frames = driver.observePeripheralStream().take(2).toList()

        assertEquals(2, frames.size)
        assertEquals("13.56 MHz RFID Turnstile Access + TOTP Pass", frames[0].peripheralName)
        assertEquals("CONNECTING", frames[0].connectionState)
        assertEquals("ONLINE", frames[1].connectionState)
    }
}
