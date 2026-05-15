package com.mic.scriptpilot.data.local

import com.mic.scriptpilot.domain.model.Project
import com.mic.scriptpilot.domain.model.ProjectType

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    title = title,
    script = script,
    type = ProjectType.fromStorage(type),
    timestamp = timestamp,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    title = title,
    script = script,
    type = type.storageKey,
    timestamp = timestamp,
)
