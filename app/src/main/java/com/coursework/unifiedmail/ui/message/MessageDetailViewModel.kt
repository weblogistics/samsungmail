package com.coursework.unifiedmail.ui.message

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MessageEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])
    val uid: Long = checkNotNull(savedStateHandle["uid"])

    private val _message = MutableStateFlow<MessageEntity?>(null)
    val message: StateFlow<MessageEntity?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = mailRepository.getInboxMessage(accountId, uid)
            _message.value = loaded
            if (loaded != null && !loaded.isRead) {
                mailRepository.setMessageRead(accountId, uid, true)
                _message.value = loaded.copy(isRead = true)
            }
        }
    }
}
