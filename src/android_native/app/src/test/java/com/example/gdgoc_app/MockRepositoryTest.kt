package com.example.gdgoc_app

import com.example.gdgoc_app.data.repository.MockPostRepository
import com.example.gdgoc_app.data.repository.MockUserRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRepositoryTest {

    @Test
    fun mockPostRepositoryReturns5PostsSortedByDate() = runTest {
        val repo = MockPostRepository()
        val posts = repo.getPosts()

        assertEquals(5, posts.size)
        // 降順ソート確認
        for (i in 0 until posts.size - 1) {
            assertTrue(posts[i].createdAt >= posts[i + 1].createdAt)
        }
    }

    @Test
    fun mockPostRepositoryFiltersByUserId() = runTest {
        val repo = MockPostRepository()
        val userPosts = repo.getUserPosts("user001")

        assertTrue(userPosts.isNotEmpty())
        for (post in userPosts) {
            assertEquals("user001", post.userId)
        }
    }

    @Test
    fun mockUserRepositoryReturnsProfile() = runTest {
        val repo = MockUserRepository()
        val user = repo.getMyProfile()

        assertEquals("user001", user.id)
        assertEquals("テストユーザー", user.name)
        assertEquals("test@example.com", user.email)
    }

}
