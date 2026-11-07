package sa.factory.maqaraccesscoworki.engine

import java.util.concurrent.ConcurrentHashMap

enum class AccessDirection {
    ENTRY,
    EXIT
}

enum class AccessStatus {
    GRANTED,
    DENIED_INVALID_UID,
    DENIED_AUTH_FAILED,
    DENIED_ANTI_PASSBACK,
    DENIED_SUSPENDED
}

data class AccessBadge(
    val uid: String,
    val memberId: String,
    val memberName: String,
    val membershipTier: String,
    val isActive: Boolean = true
)

data class AccessAuditRecord(
    val recordId: String,
    val uid: String,
    val memberId: String,
    val laneId: String,
    val direction: AccessDirection,
    val status: AccessStatus,
    val timestamp: Long = System.currentTimeMillis()
)

data class TurnstileRelayCommand(
    val commandId: String,
    val laneId: String,
    val pulseDurationMs: Int,
    val relayState: String,
    val formattedPayload: String
)

class RfidNfcTurnstileProtocolEngine {

    private val registeredBadges = ConcurrentHashMap<String, AccessBadge>()
    private val lastAccessTimestamps = ConcurrentHashMap<String, Long>()
    private val auditLogs = mutableListOf<AccessAuditRecord>()

    // Standard MIFARE Classic default transport key (FFFFFFFFFFFF)
    private val validMasterKey = "FFFFFFFFFFFF"

    init {
        // Seed default corporate & member badges for testing
        registerBadge(AccessBadge("04A1B2C3D4", "MEM-101", "عبدالله السديري", "VIP_STUDIO"))
        registerBadge(AccessBadge("04E5F6A7B8", "MEM-102", "سارة القحطاني", "FLEX_DESK"))
        registerBadge(AccessBadge("04C9D0E1F2", "MEM-103", "فهد الشمري", "DEDICATED_OFFICE"))
    }

    fun registerBadge(badge: AccessBadge) {
        registeredBadges[badge.uid.uppercase()] = badge
    }

    fun validateUidFormat(uid: String): Boolean {
        val clean = uid.trim().uppercase()
        // Standard ISO 14443-A UID lengths: 4-byte (8 hex chars), 7-byte (14 hex chars), or 10-byte
        val validHex = clean.matches(Regex("^[0-9A-F]{8,20}$"))
        return validHex && (clean.length % 2 == 0)
    }

    fun authenticateSectorKey(sector: Int, keyHex: String): Boolean {
        if (sector < 0 || sector > 39) return false
        val cleanKey = keyHex.trim().uppercase()
        return cleanKey.length == 12 && cleanKey == validMasterKey
    }

    fun checkAntiPassback(uid: String, cooldownSeconds: Long = 300): Boolean {
        val lastTime = lastAccessTimestamps[uid.uppercase()] ?: return true
        val elapsed = (System.currentTimeMillis() - lastTime) / 1000
        return elapsed >= cooldownSeconds
    }

    fun processTurnstileScan(
        uid: String,
        laneId: String,
        direction: AccessDirection,
        keyHex: String = validMasterKey,
        enforceAntiPassback: Boolean = true
    ): Pair<AccessStatus, TurnstileRelayCommand?> {
        val cleanUid = uid.trim().uppercase()

        if (!validateUidFormat(cleanUid)) {
            val record = AccessAuditRecord("REC-${System.currentTimeMillis()}", cleanUid, "UNKNOWN", laneId, direction, AccessStatus.DENIED_INVALID_UID)
            auditLogs.add(record)
            return Pair(AccessStatus.DENIED_INVALID_UID, null)
        }

        val badge = registeredBadges[cleanUid]
        if (badge == null || !badge.isActive) {
            val record = AccessAuditRecord("REC-${System.currentTimeMillis()}", cleanUid, badge?.memberId ?: "UNKNOWN", laneId, direction, AccessStatus.DENIED_SUSPENDED)
            auditLogs.add(record)
            return Pair(AccessStatus.DENIED_SUSPENDED, null)
        }

        if (!authenticateSectorKey(1, keyHex)) {
            val record = AccessAuditRecord("REC-${System.currentTimeMillis()}", cleanUid, badge.memberId, laneId, direction, AccessStatus.DENIED_AUTH_FAILED)
            auditLogs.add(record)
            return Pair(AccessStatus.DENIED_AUTH_FAILED, null)
        }

        if (enforceAntiPassback && direction == AccessDirection.ENTRY && !checkAntiPassback(cleanUid)) {
            val record = AccessAuditRecord("REC-${System.currentTimeMillis()}", cleanUid, badge.memberId, laneId, direction, AccessStatus.DENIED_ANTI_PASSBACK)
            auditLogs.add(record)
            return Pair(AccessStatus.DENIED_ANTI_PASSBACK, null)
        }

        // Grant access and generate relay activation command
        lastAccessTimestamps[cleanUid] = System.currentTimeMillis()
        val record = AccessAuditRecord("REC-${System.currentTimeMillis()}", cleanUid, badge.memberId, laneId, direction, AccessStatus.GRANTED)
        auditLogs.add(record)

        val relayCommand = generateRelayPulse(laneId, 500)
        return Pair(AccessStatus.GRANTED, relayCommand)
    }

    fun generateRelayPulse(laneId: String, pulseMs: Int = 500): TurnstileRelayCommand {
        val commandId = "CMD-RELAY-${System.currentTimeMillis() % 10000}"
        val payload = "TURNSTILE:${laneId.uppercase()}:PULSE_OPEN:${pulseMs}MS:CRC16_OK"
        return TurnstileRelayCommand(
            commandId = commandId,
            laneId = laneId,
            pulseDurationMs = pulseMs,
            relayState = "ACTIVE_MOMENTARY_OPEN",
            formattedPayload = payload
        )
    }

    fun getAuditLogCount(): Int = auditLogs.size

    fun getLatestAuditRecord(): AccessAuditRecord? = auditLogs.lastOrNull()
}
