package com.example.gdgoc_app.ui.screen.postlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gdgoc_app.data.model.Post
import com.example.gdgoc_app.data.repository.PostRepository
import com.example.gdgoc_app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val posts: List<Post> = emptyList(),
    val errorMessage: String? = null
)

class PostListViewModel(
    private val postRepository: PostRepository = ServiceLocator.postRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostListUiState(isLoading = true))
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()

    init {
        loadPosts()
    }

    fun loadPosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val posts = postRepository.getPosts()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        posts = posts,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "投稿データの取得に失敗しました"
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                val posts = postRepository.getPosts()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        posts = posts,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = e.message ?: "投稿データの更新に失敗しました"
                    )
                }
            }
        }
    }
}
