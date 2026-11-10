package com.example.gdgoc_app.data.model

import com.google.gson.annotations.SerializedName

/**
 * GET /posts および GET /users/me/posts のレスポンスラッパー。
 */
data class PostsResponse(
    @SerializedName("posts")
    val posts: List<Post> = emptyList()
)
