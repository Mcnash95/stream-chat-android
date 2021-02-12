package io.getstream.chat.ui.sample.feature.channel.add

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.call.Call
import io.getstream.chat.android.client.channel.ChannelClient
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.livedata.ChatDomain
import io.getstream.chat.ui.sample.common.CHANNEL_ARG_DRAFT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddChannelViewModel : ViewModel() {

    private val chatDomain = ChatDomain.instance()
    private val _state: MutableLiveData<State> = MutableLiveData()
    private val _paginationState: MutableLiveData<PaginationState> = MutableLiveData()
    val state: LiveData<State> = _state
    val paginationState: LiveData<PaginationState> = _paginationState

    private var channelClient: ChannelClient? = null
    private var searchQuery: String = ""
    private var offset: Int = 0
    private var latestSearchCall: Call<List<User>>? = null

    init {
        requestUsers(isRequestingMore = false)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.ReachedEndOfList -> requestMoreUsers()
            Event.MessageSent -> createChannel()
            is Event.MembersChanged -> createDraftChannel(event.members)
            is Event.SearchInputChanged -> searchUsers(event.query)
        }
    }

    private fun requestUsers(isRequestingMore: Boolean) {
        if (!isRequestingMore) {
            _state.value = State.Loading
        }
        latestSearchCall?.cancel()
        latestSearchCall = chatDomain.useCases.searchUsersByName(searchQuery, offset, USERS_LIMIT)
        latestSearchCall?.enqueue { result ->
            if (result.isSuccess) {
                val users = result.data()
                _state.postValue(if (isRequestingMore) State.ResultMoreUsers(users) else State.Result(users))
                updatePaginationData(users)
            }
        }
    }

    private fun createChannel() {
        val client = requireNotNull(channelClient) { "Cannot create Channel without initializing ChannelClient" }
        viewModelScope.launch(Dispatchers.IO) {
            val result = client.update(message = null, extraData = mapOf(CHANNEL_ARG_DRAFT to false)).execute()
            if (result.isSuccess) {
                _state.postValue(State.NavigateToChannel(result.data().cid))
            }
        }
    }

    private fun createDraftChannel(members: List<User>) {
        if (members.isEmpty()) {
            channelClient = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = ChatClient.instance()
                .createChannel(
                    channelType = CHANNEL_MESSAGING_TYPE,
                    members = members.map { it.id } + chatDomain.currentUser.id,
                    extraData = mapOf(CHANNEL_ARG_DRAFT to true)
                ).execute()
            if (result.isSuccess) {
                val cid = result.data().cid
                channelClient = ChatClient.instance().channel(cid)
                _state.postValue(State.InitializeChannel(cid))
            }
        }
    }

    private fun searchUsers(query: String) {
        offset = 0
        searchQuery = query
        requestUsers(isRequestingMore = false)
    }

    private fun requestMoreUsers() {
        _paginationState.value = _paginationState.value?.copy(loadingMore = true) ?: PaginationState(loadingMore = true)
        requestUsers(isRequestingMore = true)
    }

    private fun updatePaginationData(result: List<User>) {
        offset += result.size
        _paginationState.postValue(PaginationState(loadingMore = false, endReached = result.size < USERS_LIMIT))
    }

    companion object {
        private const val USERS_LIMIT = 30
        private const val CHANNEL_MESSAGING_TYPE = "messaging"
    }

    sealed class State {
        object Loading : State()
        data class InitializeChannel(val cid: String) : State()
        data class Result(val users: List<User>) : State()
        data class ResultMoreUsers(val users: List<User>) : State()
        data class NavigateToChannel(val cid: String) : State()
    }

    sealed class Event {
        object ReachedEndOfList : Event()
        object MessageSent : Event()
        data class MembersChanged(val members: List<User>) : Event()
        data class SearchInputChanged(val query: String) : Event()
    }

    data class PaginationState(
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
    )
}
