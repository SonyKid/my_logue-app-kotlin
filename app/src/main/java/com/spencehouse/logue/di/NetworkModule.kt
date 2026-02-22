package com.spencehouse.logue.di

import com.google.gson.Gson
import com.spencehouse.logue.service.Config
import com.spencehouse.logue.service.HeaderInterceptor
import com.spencehouse.logue.service.remote.HondaWscApi
import com.spencehouse.logue.service.remote.IdentityApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
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
    fun provideIdentityApi(okHttpClient: OkHttpClient, gson: Gson): IdentityApi {
        return Retrofit.Builder()
            .baseUrl(Config.IDENTITY_HOST)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(IdentityApi::class.java)
    }

    @Provides
    @Singleton
    fun provideHondaWscApi(okHttpClient: OkHttpClient, gson: Gson): HondaWscApi {
        return Retrofit.Builder()
            .baseUrl(Config.WSC_HOST)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HondaWscApi::class.java)
    }
}
