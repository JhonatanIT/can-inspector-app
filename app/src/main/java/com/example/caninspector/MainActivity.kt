package com.example.caninspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.caninspector.ui.DefectTableScreen
import com.example.caninspector.ui.PalletSetupScreen
import com.example.caninspector.ui.SortedPalletReportScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CanInspectorApp()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Extra flush on top of the ViewModel's own save-after-every-edit
        // behavior, so the session survives the app being closed/killed.
        ViewModelProvider(this)[AppViewModel::class.java].persistState()
    }
}

private object Routes {
    const val SETUP = "setup"
    const val DEFECTS = "defects"
    const val REPORT = "report"
}

@Composable
fun CanInspectorApp() {
    val navController: NavHostController = rememberNavController()
    // Scoped to the activity so all screens share the same pallet-cycle state.
    val viewModel: AppViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.SETUP) {
        composable(Routes.SETUP) {
            PalletSetupScreen(
                viewModel = viewModel,
                onStartInspection = { navController.navigate(Routes.DEFECTS) },
                onViewReport = { navController.navigate(Routes.REPORT) }
            )
        }
        composable(Routes.DEFECTS) {
            DefectTableScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCycleClosed = {
                    // Finishing or completing a cycle always returns to Setup,
                    // pre-filled either blank (new pallet) or with the
                    // recalculated leftover (continuation of the same pallet).
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.REPORT) {
            SortedPalletReportScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
