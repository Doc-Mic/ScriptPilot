package com.mic.scriptpilot.data.repository

import com.mic.scriptpilot.data.local.ProjectDao
import com.mic.scriptpilot.data.local.toDomain
import com.mic.scriptpilot.data.local.toEntity
import com.mic.scriptpilot.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ProjectRepository {
    fun observeAll(): Flow<List<Project>>
    fun observeRecent(limit: Int): Flow<List<Project>>
    suspend fun save(project: Project): Long
    suspend fun clearAll()
}

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val dao: ProjectDao,
) : ProjectRepository {
    override fun observeAll(): Flow<List<Project>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Project>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun save(project: Project): Long =
        dao.insert(project.toEntity())

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
