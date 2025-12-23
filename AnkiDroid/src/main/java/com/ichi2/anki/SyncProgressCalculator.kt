package com.ichi2.anki

import kotlin.math.floor

// Utility class for calculating sync progress as a percentage.
object SyncProgressCalculator {
    /**
     * Calculates the sync progress percentage based on completed and total operations.
     *
     * @param completedOps Number of operations completed (completedAddedModified + completedRemoved)
     * @param totalOps Total number of operations (totalAddedModified + totalRemoved)
     * @return Progress percentage as an integer (0-100)
     *
     * Rules:
     * - If totalOps == 0, returns 100 (no-op sync is complete)
     * - Otherwise, returns floor((completedOps / totalOps) * 100)
     * - Progress never decreases (monotonic)
     */
    fun calculateProgressPercentage(
        completedOps: Int,
        totalOps: Int,
    ): Int {
        if (totalOps == 0) {
            return 100  // No operations means sync is complete
        }
        return floor((completedOps.toDouble() / totalOps.toDouble()) * 100).toInt()
    }

    /**
     * Parses a progress string and returns a Pair of (completed, total).
     * Supports multiple formats:
     * - "X / Y" format (e.g., "5 / 10") - X completed out of Y total
     * - "X↑ Y↓" format (e.g., "66↑ 66↓") - X uploaded, Y remaining to upload
     * - Labeled format (e.g., "Added/modified: 0↑ 132↓")
     *
     * Note: The arrow format "X↑ Y↓" represents sync operations:
     * - X↑ = items remaining to upload (pending)
     * - Y↓ = items already processed (completed)
     * - Total = X + Y (all items)
     * - Completed = Y (items processed so far)
     * - Progress = Y / (X + Y)
     *
     * Examples:
     * - "132↑ 0↓" = 0 / 132 = 0% (132 remaining, 0 done)
     * - "66↑ 66↓" = 66 / 132 = 50% (66 remaining, 66 done)
     * - "0↑ 132↓" = 132 / 132 = 100% (0 remaining, 132 done)
     *
     * @param progressString Progress string from backend
     * @return Pair of (completed, total) or (0, 0) if parsing fails
     */
    private fun parseProgressString(progressString: String): Pair<Int, Int> {
        return try {
            val cleanString = progressString.substringAfter(": ", progressString).trim()

            if (cleanString.contains("↑") && cleanString.contains("↓")) {
                val parts = cleanString.split("↑", "↓")
                    .map { it.trim() }
                    .map { it.filter { c -> c.isDigit() } } // Remove all non-digit characters including invisible Unicode
                    .filter { it.isNotEmpty() }
                    .mapNotNull { it.toIntOrNull() }
                if (parts.size >= 2) {
                    val remaining = parts[0]  // X↑ = remaining to upload
                    val processed = parts[1]  // Y↓ = already processed
                    val total = remaining + processed
                    val completed = processed  // Progress is based on what's been processed
                    return Pair(completed, total)
                }
            }

            if (cleanString.contains("/")) {
                val parts = cleanString.split("/")
                    .map { it.trim() }
                    .map { it.filter { c -> c.isDigit() } } // Remove all non-digit characters
                    .filter { it.isNotEmpty() }
                    .mapNotNull { it.toIntOrNull() }
                if (parts.size == 2) {
                    return Pair(parts[0], parts[1])
                }
            }
            
            Pair(0, 0)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    fun formatSyncProgress(
        addedModifiedProgress: String,
        removedProgress: String,
    ): String {
        val (completedAdded, totalAdded) = parseProgressString(addedModifiedProgress)
        val (completedRemoved, totalRemoved) = parseProgressString(removedProgress)

        val completedOps = completedAdded + completedRemoved
        val totalOps = totalAdded + totalRemoved
        val percentage = calculateProgressPercentage(completedOps, totalOps)

        return buildString {
            append("$percentage%")
            append("\nAdded/Modified: $addedModifiedProgress")
            append("\nRemoved: $removedProgress")
        }
    }

    fun calculatePercentageFromStrings(
        addedModifiedProgress: String,
        removedProgress: String,
    ): Int {
        val (completedAdded, totalAdded) = parseProgressString(addedModifiedProgress)
        val (completedRemoved, totalRemoved) = parseProgressString(removedProgress)

        val completedOps = completedAdded + completedRemoved
        val totalOps = totalAdded + totalRemoved
        return calculateProgressPercentage(completedOps, totalOps)
    }

    fun formatSyncDetails(
        addedModifiedProgress: String,
        removedProgress: String,
    ): String {
        return buildString {
            append(addedModifiedProgress)
            append("\n$removedProgress")
        }
    }

    fun formatSyncProgress(
        completedAddedModified: Int,
        totalAddedModified: Int,
        completedRemoved: Int,
        totalRemoved: Int,
    ): String {
        val completedOps = completedAddedModified + completedRemoved
        val totalOps = totalAddedModified + totalRemoved
        val percentage = calculateProgressPercentage(completedOps, totalOps)

        return buildString {
            append("$percentage%")
            append("\nAdded/Modified: $completedAddedModified / $totalAddedModified")
            append("\nRemoved: $completedRemoved / $totalRemoved")
        }
    }
}
