package com.extensionbox.app.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun getGitHubService(): GitHubService = retrofit.create(GitHubService::class.java)
}
