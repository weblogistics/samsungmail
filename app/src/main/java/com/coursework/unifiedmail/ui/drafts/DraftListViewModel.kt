package com.coursework.unifiedmail.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.DraftEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftListViewModel @Inject constructor(
    private val mailRepository: MailRepository,
) : ViewModel() {

    val drafts: StateFlow<List<DraftEntity>> = mailRepository.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            mailRepository.deleteDraft(draftId)
        }
    }
}
