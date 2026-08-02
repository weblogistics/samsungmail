package com.coursework.unifiedmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.coursework.unifiedmail.ui.nav.MailNavGraph
import com.coursework.unifiedmail.ui.theme.UnifiedMailTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnifiedMailTheme {
                MailNavGraph()
            }
        }
    }
}
