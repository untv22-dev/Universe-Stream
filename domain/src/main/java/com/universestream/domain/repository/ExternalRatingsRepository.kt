package com.universestream.domain.repository

import com.universestream.domain.model.ExternalRatings
import com.universestream.domain.model.ExternalRatingsLookup
import com.universestream.domain.model.Result

interface ExternalRatingsRepository {
    suspend fun getRatings(lookup: ExternalRatingsLookup): Result<ExternalRatings>
}