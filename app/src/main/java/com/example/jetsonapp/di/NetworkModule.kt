package com.example.jetsonapp.di

import com.example.jetsonapp.internet.KokoroService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Kokoro base url
    // This is provided from ngrok. Replace with the one provided each time.
    private const val KOKORO_URL   = "https://e791-104-199-127-176.ngrok-free.app/"

    @Named("kokoro")
    @Provides
    @Singleton
    fun provideKokoroOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0,    TimeUnit.MILLISECONDS)
            .writeTimeout(0,   TimeUnit.MILLISECONDS)
            .callTimeout(0,    TimeUnit.MILLISECONDS)
            .build()

    @Named("kokoro")
    @Provides
    @Singleton
    fun provideKokoroRetrofit(
        @Named("kokoro") client: OkHttpClient
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(KOKORO_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideKokoroService(
        @Named("kokoro") retrofit: Retrofit
    ): KokoroService =
        retrofit.create(KokoroService::class.java)
}
