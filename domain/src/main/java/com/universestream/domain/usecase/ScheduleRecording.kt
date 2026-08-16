package com.universestream.domain.usecase

import com.universestream.domain.manager.RecordingManager
import com.universestream.domain.model.Channel
import com.universestream.domain.model.ContentType
import com.universestream.domain.model.Program
import com.universestream.domain.model.RecordingItem
import com.universestream.domain.model.RecordingRecurrence
import com.universestream.domain.model.RecordingRequest
import com.universestream.domain.model.Result
import javax.inject.Inject

data class ScheduleRecordingCommand(
    val contentType: ContentType,
    val providerId: Long,
    val channel: Channel?,
    val streamUrl: String,
    val currentProgram: Program?,
    val nextProgram: Program?,
    val recurrence: RecordingRecurrence,
    val nowMs: Long = System.currentTimeMillis()
)

class ScheduleRecording @Inject constructor(
    private val recordingManager: RecordingManager
) {
    suspend operator fun invoke(command: ScheduleRecordingCommand): Result<RecordingItem> {
        val channel = command.channel
        val targetProgram = command.nextProgram ?: command.currentProgram

        if (
            command.contentType != ContentType.LIVE ||
            channel == null ||
            command.providerId <= 0L ||
            targetProgram == null ||
            command.streamUrl.isBlank()
        ) {
            return Result.error("Recording needs guide timing for the current live channel.")
        }

        val scheduledStartMs = maxOf(command.nowMs, targetProgram.startTime)
        if (scheduledStartMs >= targetProgram.endTime) {
            return Result.error("The selected program has already ended. Refresh the guide and try again.")
        }

        return recordingManager.scheduleRecording(
            RecordingRequest(
                providerId = command.providerId,
                channelId = channel.id,
                channelName = channel.name,
                streamUrl = command.streamUrl,
                scheduledStartMs = scheduledStartMs,
                scheduledEndMs = targetProgram.endTime,
                programTitle = targetProgram.title,
                recurrence = command.recurrence
            )
        )
    }
}