package com.safecruise.app.data.api

import com.safecruise.app.data.model.PredictRequest
import com.safecruise.app.data.model.PredictResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SafeCruiseApiService {
    @POST("predict")
    suspend fun predictRisk(
        @Body request: PredictRequest
    ): Response<PredictResponse>
}
