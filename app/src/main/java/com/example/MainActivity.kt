package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.ShopRepository
import com.example.ui.ShopAppShell
import com.example.ui.ShopViewModel
import com.example.ui.ShopViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize local Room storage environment (our backend layer)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ShopRepository(database)
        
        // Setup state ViewModel coordinator
        val factory = ShopViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[ShopViewModel::class.java]
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ShopAppShell(viewModel = viewModel)
            }
        }
    }
}

