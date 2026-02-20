package com.extensionbox.app.network

import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubService {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(@Path("owner") owner: String, @Path("repo") repo: String): List<Release>
}
