package com.example.gdgoc_app.data.repository

import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.remote.ApiClient
import com.example.gdgoc_app.data.community.ApiCommunityRepository

/** Both endpoints use the same authenticated mapper as the Figma community screens. */
class ApiPostRepository(apiClient: ApiClient): PostRepository {
    private val community=ApiCommunityRepository(apiClient)
    override suspend fun getPosts(): List<Post> = community.publicPosts().sortedByDescending {it.createdAt}
    override suspend fun getUserPosts(userId: String): List<Post> = community.myPosts().sortedByDescending {it.createdAt}
}
