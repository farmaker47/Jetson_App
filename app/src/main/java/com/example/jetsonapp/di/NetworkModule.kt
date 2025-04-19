package com.example.jetsonapp.di

import com.example.jetsonapp.internet.ApiStreamingService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // ngrok spits out the base_url
    // Use it below
    private const val BASE_URL = "https://50fe-34-125-8-203.ngrok-free.app/"

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        val okHttpClient = OkHttpClient.Builder()
            // zero = no timeout
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout   (0, TimeUnit.MILLISECONDS)
            .writeTimeout  (0, TimeUnit.MILLISECONDS)
            // total call timeout (DNS + connection + request + response)
            .callTimeout   (0, TimeUnit.MILLISECONDS)
            // optional: retry on failures
            // .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiStreamingService =
        retrofit.create(ApiStreamingService::class.java)
}
