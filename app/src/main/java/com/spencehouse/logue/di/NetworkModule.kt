package com.spencehouse.logue.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.spencehouse.logue.service.Config
import com.spencehouse.logue.service.HeaderInterceptor
import com.spencehouse.logue.service.remote.HondaWscApi
import com.spencehouse.logue.service.remote.IdentityApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideHeaderInterceptor(): HeaderInterceptor {
        return HeaderInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        headerInterceptor: HeaderInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideIdentityApi(okHttpClient: OkHttpClient, json: Json): IdentityApi {
        return Retrofit.Builder()
            .baseUrl(Config.IDENTITY_HOST)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(IdentityApi::class.java)
    }

    @Provides
    @Singleton
    fun provideHondaWscApi(okHttpClient: OkHttpClient, json: Json): HondaWscApi {
        return Retrofit.Builder()
            .baseUrl(Config.WSC_HOST)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HondaWscApi::class.java)
    }
}
