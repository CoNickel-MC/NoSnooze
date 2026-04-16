package com.CoNickel.nosnooze

import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CoNickel.nosnooze.ui.theme.NoSnoozeTheme
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopAlarmRunnable = Runnable { 
        isRingingGlobal.value = false
        isChallengeGlobal.value = true
        stopRinging()
    }

    companion object {
        val isRingingGlobal = mutableStateOf(false)
        val isChallengeGlobal = mutableStateOf(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            NoSnoozeTheme {
                val isRinging by isRingingGlobal
                val isChallenge by isChallengeGlobal
                
                Surface(modifier = Modifier.fillMaxSize()) {
                    when {
                        isRinging -> {
                            AlarmRingingScreen(onSnooze = {
                                isRingingGlobal.value = false
                                isChallengeGlobal.value = true

                            })
                        }
                        isChallenge -> {
                            MathChallengeScreen(onSolved = {
                                isChallengeGlobal.value = false
                                stopRinging()
                            })
                        }
                        else -> {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                AlarmScreen(modifier = Modifier.padding(innerPadding))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("RINGING", false) == true) {
            isRingingGlobal.value = true
            isChallengeGlobal.value = false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            
            startRinging()
        }
    }

    private fun startRinging() {
        stopRinging()
        val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ringtone?.isLooping = true
        }
        ringtone?.play()

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
        }

        handler.postDelayed(stopAlarmRunnable, 120000)
    }

    private fun stopRinging() {
        ringtone?.stop()
        vibrator?.cancel()
        handler.removeCallbacks(stopAlarmRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }
}

@Composable
fun AlarmRingingScreen(onSnooze: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WAKE UP!", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("SNOOZE", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun MathChallengeScreen(onSolved: () -> Unit) {
    val context = LocalContext.current
    val alarmScheduler = remember { AlarmScheduler(context) }
    var problem by remember { mutableStateOf(alarmScheduler.generateProblem()) }
    var answerText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Solve to stop the alarm!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Text(problem, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Answer") }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (alarmScheduler.verifyAnswer(answerText)) {
                    val onTime = alarmScheduler.isBeforeDeadline()
                    alarmScheduler.processChallengeResult(onTime)
                    onSolved()
                } else {
                    answerText = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Submit")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alarmScheduler = remember { AlarmScheduler(context) }
    
    // Check for missed deadline every time the screen is opened
    LaunchedEffect(Unit) {
        alarmScheduler.checkMissedDeadline()
    }

    var startTime by remember { mutableStateOf(Calendar.getInstance()) }
    
    var endTime by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.HOUR, 1) }) }

    var showDialog1 by remember { mutableStateOf(false) }
    var showDialog2 by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("NoSnooze", style = MaterialTheme.typography.displayMedium)
        
        // Streak display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Current Streak", style = MaterialTheme.typography.labelLarge)
                Text("${alarmScheduler.getStreak()} Days", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TimeButton("Alarm Time", startTime) { showDialog1 = true }
        TimeButton("Later Bound", endTime) { showDialog2 = true }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                alarmScheduler.scheduleAlarm(
                    startTime.get(Calendar.HOUR_OF_DAY),
                    startTime.get(Calendar.MINUTE),
                    endTime.get(Calendar.HOUR_OF_DAY),
                    endTime.get(Calendar.MINUTE)
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Set Alarm")
        }
    }

    if (showDialog1) {
        M3TimePickerDialog(
            calendar = startTime,
            onDismiss = { showDialog1 = false },
            onConfirm = { h, m ->
                startTime = (startTime.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                }
                showDialog1 = false
            }
        )
    }

    if (showDialog2) {
        M3TimePickerDialog(
            calendar = endTime,
            onDismiss = { showDialog2 = false },
            onConfirm = { h, m ->
                endTime = (endTime.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, m)
                }
                showDialog2 = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3TimePickerDialog(
    calendar: Calendar,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            TimePicker(state = state)
        }
    )
}

@Composable
fun TimeButton(label: String, calendar: Calendar, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = String.format(Locale.getDefault(), "%02d:%02d", 
                    calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE)),
                style = MaterialTheme.typography.displayMedium
            )
        }
    }
}
