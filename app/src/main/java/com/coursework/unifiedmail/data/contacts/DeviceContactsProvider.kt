package com.coursework.unifiedmail.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.coursework.unifiedmail.data.repository.EmailContact
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads email addresses out of the phone's own Contacts — a second source for the compose
 * screen's autocomplete alongside MailRepository.getAutocompleteContacts (which only knows about
 * people this account has actually emailed). Read-only; nothing here ever writes to Contacts.
 */
@Singleton
class DeviceContactsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Empty (not a crash/exception) when READ_CONTACTS hasn't been granted — see ComposeScreen's permission request. */
    suspend fun getContacts(): List<EmailContact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val contacts = mutableListOf<EmailContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val address = (if (addressIndex >= 0) cursor.getString(addressIndex) else null)?.trim()
                if (address.isNullOrBlank()) continue
                val name = (if (nameIndex >= 0) cursor.getString(nameIndex) else null)?.trim()?.takeIf { it.isNotBlank() }
                contacts.add(EmailContact(name, address))
            }
        }
        contacts
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
}
