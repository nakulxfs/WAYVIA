package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.navigation.CommuteNavGraph
import com.example.ui.CommuteViewModel
import com.example.ui.theme.CommuteBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CommuteBuddyApp
        setContent {
            CommuteBuddyTheme {
                val viewModel: CommuteViewModel = viewModel(
                    factory = CommuteViewModel.factory(app.container)
                )
                CommuteNavGraph(viewModel = viewModel)
            }
        }
    }
}
