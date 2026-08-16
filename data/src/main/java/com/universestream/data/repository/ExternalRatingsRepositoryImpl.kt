package com.universestream.data.repository

import com.universestream.domain.model.ExternalRatings
import com.universestream.domain.model.ExternalRatingsLookup
import com.universestream.domain.model.Result
import com.universestream.domain.repository.ExternalRatingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalRatingsRepositoryImpl @Inject constructor() : ExternalRatingsRepository {

    override suspend fun getRatings(lookup: ExternalRatingsLookup): Result<ExternalRatings> {
        return Result.success(ExternalRatings.unavailable())
    }
}