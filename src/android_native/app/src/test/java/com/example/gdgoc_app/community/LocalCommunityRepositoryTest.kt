package com.example.gdgoc_app.community

import com.example.gdgoc_app.data.community.*
import com.google.gson.Gson
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LocalCommunityRepositoryTest {
    private fun seed()=Gson().fromJson(File("src/main/assets/community/figma_samples.json").readText(),CommunitySeed::class.java)
    @Test fun figmaSamplesAreSeparatedFromUserProfileAndContainAllMenusData() {
        val state=LocalCommunityRepository(seed()).state.value
        assertEquals(8,state.days.size);assertEquals(16,state.types.size);assertEquals(128,state.posts.size)
        assertTrue(state.posts.none {it.own});assertTrue(state.answers.isEmpty())
        assertEquals("今のあなたは、自分の結婚についてどう考えている？",state.days.first().question.text)
    }
    @Test fun invalidAnswersNeverUnlockTheFeed() {
        val repo=LocalCommunityRepository(seed());repo.answer(0,"存在しない回答");repo.answer(99,"結婚したい")
        assertTrue(repo.state.value.answers.isEmpty())
    }
    @Test fun localLikesToggleAndAreNotPersistedAcrossRepositorySessions() {
        val repo=LocalCommunityRepository(seed());val post=repo.state.value.posts.first()
        repo.toggleLike(post.id);assertTrue(repo.state.value.posts.first().liked);assertEquals(post.likes+1,repo.state.value.posts.first().likes)
        repo.toggleLike(post.id);assertEquals(post,repo.state.value.posts.first())
        repo.answer(0,repo.state.value.days.first().question.choices.first())
        assertTrue(LocalCommunityRepository(seed()).state.value.answers.isEmpty())
    }
    @Test fun composerRequiresLocalAnswerAndRealProfileInputsAndDoesNotTouchAnotherSession() {
        val repo=LocalCommunityRepository(seed())
        assertFalse(repo.addLocalPost(0,"名前","GIPS","APIの名称","投稿",emptyList()))
        repo.answer(0,"結婚したい")
        assertFalse(repo.addLocalPost(0,"","GIPS","APIの名称","投稿",emptyList()))
        assertFalse(repo.addLocalPost(0,"名前","GIPS","APIの名称","あ".repeat(241),emptyList()))
        assertTrue(repo.addLocalPost(0,"実際の名前","EXFD","APIの名称","  端末内の投稿  ",listOf("結婚観")))
        val post=repo.state.value.posts.first();assertTrue(post.own);assertEquals("実際の名前",post.name);assertEquals("EXFD",post.typeCode);assertEquals("端末内の投稿",post.text)
        assertEquals(128,LocalCommunityRepository(seed()).state.value.posts.size)
    }
    @Test fun commentsAndCommentLikesAreLocalAndValidated() {
        val repo=LocalCommunityRepository(seed());val id=repo.state.value.posts.first().id
        assertFalse(repo.addLocalReply(id,"名前"," "));assertFalse(repo.addLocalReply(99999,"名前","本文"))
        assertTrue(repo.addLocalReply(id,"実名","コメント"))
        val reply=repo.state.value.posts.first().replies.last();assertTrue(reply.own)
        repo.toggleReplyLike(id,reply.id);assertEquals(1,repo.state.value.posts.first().replies.last().likes)
    }
}
