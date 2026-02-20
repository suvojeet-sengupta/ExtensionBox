package com.extensionbox.app.network

import com.google.gson.annotations.SerializedName

data class Release(
    @SerializedName("tag_name")
    val tagName: String
)
