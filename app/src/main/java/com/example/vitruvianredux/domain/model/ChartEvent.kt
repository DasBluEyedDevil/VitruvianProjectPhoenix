package com.example.vitruvianredux.domain.model

sealed class ChartEvent(
    val timestamp: Long,
    val label: String
) {
    class RepStart(timestamp: Long, repNumber: Int) : ChartEvent(timestamp, "Rep $repNumber")

    class RepComplete(timestamp: Long, repNumber: Int) : ChartEvent(timestamp, "Rep $repNumber Complete")

    class WarmupComplete(timestamp: Long) : ChartEvent(timestamp, "Warmup Complete")
}
