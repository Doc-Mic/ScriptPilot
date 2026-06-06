package com.mic.scriptpilot.di

import com.mic.scriptpilot.data.repository.ApiIdeaRepository
import com.mic.scriptpilot.data.repository.ApiScriptRepository
import com.mic.scriptpilot.data.repository.ApiSeoRepository
import com.mic.scriptpilot.data.repository.AuthRepository
import com.mic.scriptpilot.data.repository.FirebaseAuthRepository
import com.mic.scriptpilot.data.repository.IdeaRepository
import com.mic.scriptpilot.data.repository.ProjectRepository
import com.mic.scriptpilot.data.repository.ProjectRepositoryImpl
import com.mic.scriptpilot.data.repository.ScriptRepository
import com.mic.scriptpilot.data.repository.SeoRepository
import com.mic.scriptpilot.data.repository.TrendRepository
import com.mic.scriptpilot.data.repository.TrendsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindTrendRepository(impl: TrendsRepository): TrendRepository

    @Binds
    @Singleton
    abstract fun bindIdeaRepository(impl: ApiIdeaRepository): IdeaRepository

    @Binds
    @Singleton
    abstract fun bindScriptRepository(impl: ApiScriptRepository): ScriptRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindSeoRepository(impl: ApiSeoRepository): SeoRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
