package com.gios.brightsteps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gios.brightsteps.alarm.SampleScheduler
import com.gios.brightsteps.ui.DiagnosticsScreen
import com.gios.brightsteps.ui.GoalScreen
import com.gios.brightsteps.ui.HomeScreen
import com.gios.brightsteps.ui.StepsViewModel
import com.gios.brightsteps.ui.theme.BrightStepsTheme

private enum class Screen { HOME, GOAL, DIAGNOSTICS }

class MainActivity : ComponentActivity() {

    private var granted by mutableStateOf(false)
    private var vm: StepsViewModel? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            if (ok) vm?.sampleNow()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        granted = hasRecognitionPermission()
        // Arm both chains now: a force-stop or reinstall cancels every alarm, and a launch is
        // the reliable moment to put them back.
        SampleScheduler.armAll(this)

        setContent {
            val model: StepsViewModel = viewModel()
            vm = model
            val state by model.state.collectAsStateWithLifecycle()
            val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
            val probe by model.probeState.collectAsStateWithLifecycle()
            var screen by remember { mutableStateOf(Screen.HOME) }

            BrightStepsTheme {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        state = state,
                        granted = granted,
                        permissionLost = model.permissionLost && granted,
                        onGrant = { requestRecognition() },
                        onOpenGoal = { screen = Screen.GOAL },
                        onOpenDiagnostics = {
                            model.refreshDiagnostics()
                            screen = Screen.DIAGNOSTICS
                        },
                    )
                    Screen.GOAL -> GoalScreen(
                        goal = state.goal,
                        onChange = { model.setGoal(it) },
                        onDone = { screen = Screen.HOME },
                    )
                    Screen.DIAGNOSTICS -> DiagnosticsScreen(
                        d = diagnostics,
                        probe = probe,
                        onToggleProbe = {
                            if (probe.running) model.stopProbe() else model.startProbe()
                        },
                        onFixBattery = { requestBatteryExemption() },
                        onFixPermission = { requestRecognition() },
                        onDone = {
                            model.stopProbe()
                            screen = Screen.HOME
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        granted = hasRecognitionPermission()
        if (granted) vm?.sampleNow()
        vm?.refreshDiagnostics()
    }

    private fun hasRecognitionPermission(): Boolean {
        // The runtime permission only exists on API 29+; below that it is install-time.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecognition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    /**
     * Sends the user to the battery-optimization prompt. Asked for, never assumed: the system
     * dialog is the only way an app can leave the optimized bucket, and being in it is what
     * lets the OS defer the quarter-hour reading for hours at a time.
     */
    private fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            // Some ROMs hide the direct request; the battery settings page always exists.
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }
}
