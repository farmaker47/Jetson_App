package com.example.jetsonapp.di

import com.example.jetsonapp.internet.KokoroService
import com.example.jetsonapp.internet.OllamaService
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
    // On the host machine do a "hostname -I" to check the IP
    // In my case it was 192.168.1.92
    // Port for Jetson Orin Nano is 11434
    // Since we use http for the local server then use android:usesCleartextTraffic="true" at the manifest
    private const val OLLAMA_URL = "http://192.168.1.92:11434/"
    // Kokoro base url
    // This is provided from ngrok. Replace with the one provided each time.
    private const val KOKORO_URL   = "https://1589-34-16-164-255.ngrok-free.app/"

//    @Provides
//    @Singleton
//    fun provideRetrofit(): Retrofit {
//        val okHttpClient = OkHttpClient.Builder()
//            .connectTimeout(60, TimeUnit.SECONDS)
//            .readTimeout(60, TimeUnit.SECONDS)
//            .writeTimeout(60, TimeUnit.SECONDS)
//            .build()
//
//        return Retrofit.Builder()
//            .baseUrl(OLLAMA_URL)
//            .client(okHttpClient)
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//    }
//
//    // Use for non streaming.
//    @Provides
//    @Singleton
//    fun provideApiService(retrofit: Retrofit): ApiService =
//        retrofit.create(ApiService::class.java)

    /*@Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiStreamingService =
        retrofit.create(ApiStreamingService::class.java)*/

    @Named("ollama")
    @Provides
    @Singleton
    fun provideOllamaOkHttp(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0,    TimeUnit.MILLISECONDS)
            .writeTimeout(0,   TimeUnit.MILLISECONDS)
            .callTimeout(0,    TimeUnit.MILLISECONDS)
            .build()

    @Named("ollama")
    @Provides
    @Singleton
    fun provideOllamaRetrofit(
        @Named("ollama") client: OkHttpClient
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(OLLAMA_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides
    @Singleton
    fun provideOllamaService(
        @Named("ollama") retrofit: Retrofit
    ): OllamaService =
        retrofit.create(OllamaService::class.java)

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
