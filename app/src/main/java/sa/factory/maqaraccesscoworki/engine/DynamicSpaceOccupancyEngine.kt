package sa.factory.maqaraccesscoworki.engine

import java.util.concurrent.ConcurrentHashMap

enum class ZoneComfortLevel {
    QUIET_ZONE,
    BALANCED_COLLABORATION,
    HIGH_DENSITY_ALERT,
    OVER_CAPACITY
}

data class CoworkingZone(
    val zoneId: String,
    val zoneName: String,
    val maxCapacity: Int,
    var activeOccupancy: Int = 0,
    var isEcoModeActive: Boolean = false,
    val hvacTargetCelsius: Double = 22.0
)

data class DeskAssignment(
    val deskId: String,
    val zoneId: String,
    val memberId: String,
    val checkedInAt: Long = System.currentTimeMillis()
)

data class ZoneOccupancyTelemetry(
    val zoneId: String,
    val zoneName: String,
    val occupancyRate: Double,
    val comfortLevel: ZoneComfortLevel,
    val ecoModeActive: Boolean,
    val availableSpots: Int
)

class DynamicSpaceOccupancyEngine {

    private val zones = ConcurrentHashMap<String, CoworkingZone>()
    private val activeAssignments = ConcurrentHashMap<String, DeskAssignment>()

    init {
        // Seed default spaces across the coworking floorplan
        registerZone(CoworkingZone("ZONE-FLEX-01", "قاعة المكاتب المشتركة المرنة", maxCapacity = 40, activeOccupancy = 18))
        registerZone(CoworkingZone("ZONE-DEDICATED-02", "منطقة المكاتب المخصصة الهادئة", maxCapacity = 20, activeOccupancy = 12))
        registerZone(CoworkingZone("ZONE-STUDIO-03", "أجنحة الشركات الناشئة التنفيذية", maxCapacity = 10, activeOccupancy = 7))
        registerZone(CoworkingZone("ZONE-BOARDROOM-04", "قاعة الاجتماعات الكبرى", maxCapacity = 16, activeOccupancy = 0, isEcoModeActive = true))
        registerZone(CoworkingZone("ZONE-BOOTH-05", "كبائن الاتصال الصوتي العازلة", maxCapacity = 6, activeOccupancy = 2))
    }

    fun registerZone(zone: CoworkingZone) {
        zones[zone.zoneId] = zone
    }

    fun calculateOccupancyRate(zoneId: String): Double {
        val zone = zones[zoneId] ?: return 0.0
        if (zone.maxCapacity == 0) return 0.0
        val rate = zone.activeOccupancy.toDouble() / zone.maxCapacity.toDouble()
        return (rate * 100.0).coerceIn(0.0, 100.0)
    }

    fun evaluateComfortLevel(occupancyRatePct: Double): ZoneComfortLevel {
        return when {
            occupancyRatePct < 30.0 -> ZoneComfortLevel.QUIET_ZONE
            occupancyRatePct <= 75.0 -> ZoneComfortLevel.BALANCED_COLLABORATION
            occupancyRatePct <= 95.0 -> ZoneComfortLevel.HIGH_DENSITY_ALERT
            else -> ZoneComfortLevel.OVER_CAPACITY
        }
    }

    fun checkInMember(zoneId: String, deskId: String, memberId: String): Boolean {
        val zone = zones[zoneId] ?: return false
        if (zone.activeOccupancy >= zone.maxCapacity) return false
        if (activeAssignments.containsKey(deskId)) return false

        activeAssignments[deskId] = DeskAssignment(deskId, zoneId, memberId)
        zone.activeOccupancy += 1
        if (zone.isEcoModeActive) {
            zone.isEcoModeActive = false
        }
        return true
    }

    fun checkOutMember(deskId: String): Boolean {
        val assignment = activeAssignments.remove(deskId) ?: return false
        val zone = zones[assignment.zoneId] ?: return true
        if (zone.activeOccupancy > 0) {
            zone.activeOccupancy -= 1
        }
        if (zone.activeOccupancy == 0) {
            zone.isEcoModeActive = true
        }
        return true
    }

    fun getZoneTelemetry(zoneId: String): ZoneOccupancyTelemetry? {
        val zone = zones[zoneId] ?: return null
        val rate = calculateOccupancyRate(zoneId)
        val comfort = evaluateComfortLevel(rate)
        val available = (zone.maxCapacity - zone.activeOccupancy).coerceAtLeast(0)
        return ZoneOccupancyTelemetry(
            zoneId = zone.zoneId,
            zoneName = zone.zoneName,
            occupancyRate = rate,
            comfortLevel = comfort,
            ecoModeActive = zone.isEcoModeActive,
            availableSpots = available
        )
    }

    fun generateOverallFacilitySummary(): String {
        val totalCap = zones.values.sumOf { it.maxCapacity }
        val totalOcc = zones.values.sumOf { it.activeOccupancy }
        val overallRate = if (totalCap > 0) (totalOcc.toDouble() / totalCap.toDouble()) * 100.0 else 0.0
        val ecoCount = zones.values.count { it.isEcoModeActive }
        return "CAPACITY: ${totalOcc}/${totalCap} (${String.format("%.1f", overallRate)}%) | ECO_ZONES: ${ecoCount}/${zones.size}"
    }

    fun getActiveAssignmentCount(): Int = activeAssignments.size
}
