package com.coursework.unifiedmail.data.remote

import java.io.IOException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

/**
 * Turns a raw JavaMail/IO exception into a message worth showing a user — distinguishing "your
 * password is wrong" from "you're offline" from "the server rejected this," which otherwise all
 * surface as similarly unhelpful low-level exception text.
 */
fun describeMailError(throwable: Throwable): String = when (throwable) {
    is AuthenticationFailedException -> "Authentication failed — check username and password"
    is UnknownHostException -> "Couldn't reach the server — check the host name and your network connection"
    is IOException -> "Network error — check your connection and try again"
    is MessagingException -> throwable.message ?: "Mail server error"
    else -> throwable.message ?: "Unexpected error"
}
