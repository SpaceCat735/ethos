package com.naechamon.domain

// 속도 샘플. 시간 순서대로 들어온다고 본다.
data class SpeedSample(val timeMillis: Long, val speedKmh: Float)

enum class DrivingEventType {
    HARD_ACCELERATION, HARD_START, HARD_DECELERATION, HARD_STOP;

    // 급감속에서 그대로 급정지로 이어지거나(제동), 급출발에서 급가속으로 이어지면(가속) 한 번의 동작으로 센다
    val isBraking: Boolean get() = this == HARD_DECELERATION || this == HARD_STOP
    fun sameFamily(other: DrivingEventType?): Boolean = other != null && isBraking == other.isBraking
}

// timeMillis: 사건이 처음 감지된 구간의 끝 샘플 시각
data class DrivingEvent(val type: DrivingEventType, val timeMillis: Long)

// 연비는 km/L(전기차는 km/kWh). 둘 다 같은 단위면 된다. 모르면 null.
data class TripInput(
    val samples: List<SpeedSample>,
    val distanceKm: Double,
    val tripEfficiency: Double? = null,
    val ratedEfficiency: Double? = null,
)

data class TripScore(
    val events: List<DrivingEvent>,
    val moodChange: Int,
    val newMood: Int,
    val experience: Double,
)

object DrivingScorer {

    // 연속 샘플 사이의 초당 속도 변화로 위험운전을 찾는다. 한 구간에서는 사건 하나만 센다.
    // 같은 종류가 연속 구간에 이어지면 한 번의 동작으로 보고 1회만 센다.
    fun detectEvents(samples: List<SpeedSample>): List<DrivingEvent> {
        val events = mutableListOf<DrivingEvent>()
        var previousType: DrivingEventType? = null
        for ((prev, cur) in samples.zipWithNext()) {
            if (cur.timeMillis <= prev.timeMillis) continue
            val type = classify(prev, cur)
            if (type != null && !type.sameFamily(previousType)) {
                events += DrivingEvent(type, cur.timeMillis)
            }
            previousType = type
        }
        return events
    }

    // 두 샘플 사이 한 구간을 판정한다. 실시간 감지(LiveDrivingTracker)도 이 기준을 쓴다.
    fun classify(prev: SpeedSample, cur: SpeedSample): DrivingEventType? {
        val seconds = (cur.timeMillis - prev.timeMillis) / 1000.0
        if (seconds <= 0) return null
        val from = prev.speedKmh.toDouble()
        val to = cur.speedKmh.toDouble()
        val rate = (to - from) / seconds // km/h/초, 감속이면 음수
        return with(DrivingRules) {
            when {
                // 급출발·급정지를 급가속·급감속보다 먼저 본다
                from <= HARD_START_MAX_SPEED_KMH && rate >= HARD_START_KMH_PER_SEC -> DrivingEventType.HARD_START
                from >= HARD_ACCEL_MIN_SPEED_KMH && rate >= HARD_ACCEL_KMH_PER_SEC -> DrivingEventType.HARD_ACCELERATION
                to <= HARD_STOP_MAX_SPEED_KMH && -rate >= HARD_STOP_KMH_PER_SEC -> DrivingEventType.HARD_STOP
                from >= HARD_DECEL_MIN_SPEED_KMH && -rate >= HARD_DECEL_KMH_PER_SEC -> DrivingEventType.HARD_DECELERATION
                else -> null
            }
        }
    }

    // 공인 연비가 0 이하이거나 연비를 모르면 기본 배수
    fun fuelMultiplier(tripEfficiency: Double?, ratedEfficiency: Double?): Double {
        if (tripEfficiency == null || ratedEfficiency == null || tripEfficiency <= 0 || ratedEfficiency <= 0) {
            return DrivingRules.FUEL_MULTIPLIER_DEFAULT
        }
        return (tripEfficiency / ratedEfficiency)
            .coerceIn(DrivingRules.FUEL_MULTIPLIER_MIN, DrivingRules.FUEL_MULTIPLIER_MAX)
    }

    // 위험운전 없이 일정 거리 이상 달린 주행이면 기분 보너스
    fun smoothTripBonus(eventCount: Int, distanceKm: Double): Int =
        if (eventCount == 0 && distanceKm >= DrivingRules.SMOOTH_TRIP_MIN_KM) DrivingRules.SMOOTH_TRIP_MOOD_BONUS else 0

    fun score(trip: TripInput, currentMood: Int = DrivingRules.MOOD_START): TripScore {
        val events = detectEvents(trip.samples)
        val distanceKm = trip.distanceKm.coerceAtLeast(0.0)

        val bonus = smoothTripBonus(events.size, distanceKm)
        val moodChange = bonus - events.size * DrivingRules.MOOD_PENALTY_PER_EVENT
        val newMood = (currentMood + moodChange).coerceIn(DrivingRules.MOOD_MIN, DrivingRules.MOOD_MAX)

        val experience = distanceKm * fuelMultiplier(trip.tripEfficiency, trip.ratedEfficiency)
        return TripScore(events, moodChange, newMood, experience)
    }
}
