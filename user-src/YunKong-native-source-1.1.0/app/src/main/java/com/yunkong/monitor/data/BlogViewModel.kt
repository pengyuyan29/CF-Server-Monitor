package com.yunkong.monitor.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

data class BlogUi(
    val baseUrl: String = "", val busy: Boolean = false, val unlocked: Boolean = false,
    val posts: List<kotlinx.serialization.json.JsonObject> = emptyList(),
    val selected: kotlinx.serialization.json.JsonObject? = null, val html: String = "", val error: String? = null,
    val localAvailable: Boolean = false
)

class BlogViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("yunkong_modules", 0)
    private val repository = BlogRepository()
    private val worker = Executors.newSingleThreadExecutor()
    private val cryptoDispatcher = worker.asCoroutineDispatcher()
    private val archiveFile = java.io.File(app.filesDir, "encrypted-blog.zip")
    private val state = MutableStateFlow(BlogUi(baseUrl = prefs.getString("blog_url", "https://pengyuyan29.github.io/blog/").orEmpty(), localAvailable = archiveFile.isFile))
    val ui = state.asStateFlow()
    private var generation = 0

    fun unlock(base: String, password: CharArray, local: Boolean = false) {
        if (state.value.busy) { password.fill('\u0000'); return }
        val current = ++generation
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val posts = withContext(cryptoDispatcher) { repository.unlock(base, password, if (local) archiveFile else null) }
                if (current != generation) return@launch
                prefs.edit().putString("blog_url", base.trim()).apply()
                state.value = BlogUi(baseUrl = base.trim(), unlocked = true, posts = posts, localAvailable = archiveFile.isFile)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (current == generation) state.value = state.value.copy(busy = false, error = e.message ?: "解锁失败")
            }
        }
    }

    fun importArchive(uri: android.net.Uri) {
        if (state.value.busy) return
        val current = ++generation
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(cryptoDispatcher) {
                    val temporary = java.io.File(archiveFile.parentFile, "encrypted-blog-import.tmp")
                    try {
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(8192); var total = 0
                                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 64 * 1024 * 1024) { "ZIP 超过 64 MB" }; output.write(buffer, 0, n) }
                            }
                        } ?: error("文件不可读取")
                        BlogRepository.validateArchive(temporary)
                        java.nio.file.Files.move(temporary.toPath(), archiveFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    } finally { temporary.delete() }
                }
                if (generation == current) state.value = state.value.copy(busy = false, localAvailable = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (generation == current) state.value = state.value.copy(busy = false, error = e.message)
            }
        }
    }

    fun open(post: kotlinx.serialization.json.JsonObject) {
        val current = ++generation
        state.value = state.value.copy(selected = post, html = "", busy = true, error = null)
        viewModelScope.launch {
            try {
                val html = withContext(cryptoDispatcher) { repository.article(post.str("slug").orEmpty()) }
                if (generation == current) state.value = state.value.copy(html = html, busy = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (generation == current) state.value = state.value.copy(busy = false, error = e.message)
            }
        }
    }

    fun back() { generation++; state.value = state.value.copy(selected = null, html = "", busy = false, error = null) }
    fun lock() {
        generation++
        state.value = BlogUi(baseUrl = state.value.baseUrl, localAvailable = archiveFile.isFile)
        worker.execute { repository.lock() }
    }
    override fun onCleared() { worker.execute { repository.lock() }; cryptoDispatcher.close(); super.onCleared() }
}
