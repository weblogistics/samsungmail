package com.coursework.unifiedmail.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    mailRepository: MailRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    val folderKey: String = checkNotNull(savedStateHandle["folderKey"])
    val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    val messages: StateFlow<List<MessageEntity>> =
        mailRepository.observeConversationMessages(accountId, folderKey, conversationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
