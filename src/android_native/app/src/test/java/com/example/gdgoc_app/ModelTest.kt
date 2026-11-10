package com.example.gdgoc_app

import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.model.User
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {

    private val gson = Gson()

    @Test
    fun userSerialization() {
        val json = """
            {
                "id": "user001",
                "name": "テストユーザー",
                "email": "test@example.com",
                "bio": "自己紹介文"
            }
        """.trimIndent()

        val user = gson.fromJson(json, User::class.java)
        assertEquals("user001", user.id)
        assertEquals("テストユーザー", user.name)
        assertEquals("test@example.com", user.email)
        assertEquals("自己紹介文", user.bio)
    }

    @Test
    fun postSerializationAndDateFormatting() {
        val json = """
            {
                "id": "post001",
                "userId": "user001",
                "userName": "テストユーザー",
                "content": "テスト投稿",
                "createdAt": "2026-11-08T12:00:00Z",
                "likeCount": 5
            }
        """.trimIndent()

        val post = gson.fromJson(json, Post::class.java)
        assertEquals("post001", post.id)
        assertEquals("user001", post.userId)
        assertEquals("テストユーザー", post.userName)
        assertEquals("テスト投稿", post.content)
        assertEquals(5, post.likeCount)

        val formatted = post.formattedDate
        assertTrue(formatted.contains("/"))
        assertTrue(formatted.contains(":"))
    }
}
