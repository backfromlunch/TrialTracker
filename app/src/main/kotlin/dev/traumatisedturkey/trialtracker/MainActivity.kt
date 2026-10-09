package dev.traumatisedturkey.trialtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.traumatisedturkey.trialtracker.data.ThemeMode
import dev.traumatisedturkey.trialtracker.data.TrialDatabase
import dev.traumatisedturkey.trialtracker.trialsetup.TrialSetupViewModel
import dev.traumatisedturkey.trialtracker.ui.TrialTrackerApp
import dev.traumatisedturkey.trialtracker.ui.TrialTrackerTheme

class MainActivity : ComponentActivity() {

    private lateinit var db: TrialDatabase

    // Owns the startup DB check and the ServiceRequest/Questionnaire read/parse/confirm/persist sequences.
    //
    // `db` is read lazily inside the factory lambda (not at property-initializer time), so this
    // is safe despite `db` itself being assigned in onCreate: the `viewModels` delegate doesn't
    // actually construct the ViewModel until first accessed, which happens from setContent after
    // onCreate has assigned `db`.

    private val loaderViewModel: TrialSetupViewModel by viewModels {
        TrialSetupViewModel.Factory(db.trialDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() - see the platform splash screen API's docs.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        db = TrialDatabase.getInstance(this)
        // The splash screen covers the gap until startup is complete.
        splashScreen.setKeepOnScreenCondition { !loaderViewModel.startupCheckComplete }

        setContent {
            // Theming has to apply to the whole app not just the screen where it's changed.
            // `initial = null` covers the gap before the first emission; ThemeMode.LIGHT is
            // the fallback for that gap and for any unrecognized stored value.
            val settings by db.settingsDao().observe().collectAsState(initial = null)
            val themeMode = settings?.let { ThemeMode.fromStored(it.themeMode) } ?: ThemeMode.LIGHT

            TrialTrackerTheme(themeMode = themeMode) {
                // The splash screen covers the gap before startupCheckComplete, so nothing renders here until then.
                if (loaderViewModel.startupCheckComplete) {
                    // The shell always renders, whether or not a trial exists yet.
                    // `db` handle is constructed only here, so passed to the app.
                    TrialTrackerApp(db = db)
                }
            }
        }
    }
}
