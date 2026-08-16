package com.universestream.domain.repository

import com.universestream.domain.model.Category
import com.universestream.domain.model.LibraryBrowseQuery
import com.universestream.domain.model.MovieDetailPresentationHint
import com.universestream.domain.model.Movie
import com.universestream.domain.model.PagedResult
import com.universestream.domain.model.Result
import com.universestream.domain.model.StreamInfo
import com.universestream.domain.model.VodMovieVariant
import kotlinx.coroutines.flow.Flow

interface MovieRepository {
    fun getMovies(providerId: Long): Flow<List<Movie>>
    fun getMoviesByCategory(providerId: Long, categoryId: Long): Flow<List<Movie>>
    fun getMoviesByCategoryPage(providerId: Long, categoryId: Long, limit: Int, offset: Int): Flow<List<Movie>>
    fun getMoviesByCategoryPreview(providerId: Long, categoryId: Long, limit: Int): Flow<List<Movie>>
    fun getCategoryPreviewRows(providerId: Long, categoryIds: List<Long>, limitPerCategory: Int): Flow<Map<Long?, List<Movie>>>
    fun getTopRatedPreview(providerId: Long, limit: Int): Flow<List<Movie>>
    fun getFreshPreview(providerId: Long, limit: Int): Flow<List<Movie>>
    fun getRecommendations(providerId: Long, limit: Int): Flow<List<Movie>>
    fun getRelatedContent(providerId: Long, movieId: Long, limit: Int): Flow<List<Movie>>
    fun getMoviesByIds(ids: List<Long>): Flow<List<Movie>>
    fun getCategories(providerId: Long): Flow<List<Category>>
    fun getCategoryItemCounts(providerId: Long): Flow<Map<Long, Int>>
    fun getLibraryCount(providerId: Long): Flow<Int>
    fun browseMovies(query: LibraryBrowseQuery): Flow<PagedResult<Movie>>
    fun searchMovies(providerId: Long, query: String): Flow<List<Movie>>
    suspend fun getMovie(movieId: Long): Movie?
    suspend fun getMovieVariants(movieId: Long): List<VodMovieVariant> = emptyList()
    suspend fun getMovieDetails(
        providerId: Long,
        movieId: Long,
        knownPresentation: MovieDetailPresentationHint? = null
    ): Result<Movie>
    suspend fun getStreamInfo(movie: Movie): Result<StreamInfo>
    suspend fun refreshMovies(providerId: Long): Result<Unit>
    suspend fun getWatchProgress(movieId: Long): Long? = null
}
