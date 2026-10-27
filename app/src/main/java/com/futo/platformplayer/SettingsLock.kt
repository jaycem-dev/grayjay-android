package com.futo.platformplayer;

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.states.StateApp

/**
 * A system based lock on the settings of the app, using the device credentials (PIN, pattern, password or biometrics).
 * When enabled, every entry into the settings or sources tab requires confirming the device credentials.
 * When no device credentials are configured, the lock is not enforced, since there would be nothing to verify.
 */
object SettingsLock {
    private var _authenticating = false;

    fun isDeviceSecure(context: Context): Boolean {
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return false;
        return keyguard.isDeviceSecure;
    }

    fun isLocked(context: Context): Boolean {
        if (!Settings.instance.security.settingsLock)
            return false;
        return isDeviceSecure(context);
    }

    fun requiresUnlock(context: Context): Boolean {
        return isLocked(context);
    }

    /**
     * Clears an authentication that was interrupted by the activity being destroyed.
     */
    fun reset() {
        _authenticating = false;
    }

    /**
     * Processes a locked interaction (setting change, button, tab navigation).
     * Returns true when the interaction can proceed right away,
     * otherwise starts the device credentials authentication and returns false.
     * Reached callbacks are invoked when the authentication result is received.
     */
    fun processInteraction(context: Context, description: String? = null, onSuccess: (() -> Unit)? = null, onFailure: (() -> Unit)? = null): Boolean {
        if (!requiresUnlock(context))
            return true;

        val activity = context as? Activity ?: StateApp.instance.activity;
        if (activity is MainActivity)
            authenticate(activity, activity.getString(R.string.settings_lock_auth_title), description ?: activity.getString(R.string.settings_lock_auth_description), onSuccess, onFailure);
        else {
            Logger.i(TAG, "Unable to start authentication, no activity available");
            onFailure?.invoke();
        }
        return false;
    }

    fun authenticate(activity: MainActivity, title: String, description: String, onSuccess: (() -> Unit)? = null, onFailure: (() -> Unit)? = null) {
        if (_authenticating) {
            Logger.i(TAG, "Authentication already in progress, ignoring request");
            return;
        }

        _authenticating = true;
        Logger.i(TAG, "Starting authentication: $title / $description");
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                _authenticating = false;
                Logger.i(TAG, "Settings unlocked");
                try {
                    onSuccess?.invoke();
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failure in unlock callback", e);
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                _authenticating = false;
                Logger.i(TAG, "Authentication error $errorCode: $errString");
                try {
                    onFailure?.invoke();
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failure in unlock callback", e);
                }
            }

            override fun onAuthenticationFailed() {
                //Biometric not recognized, keep the prompt open so the user can retry
                Logger.i(TAG, "Authentication failed, retrying");
            }
        };

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback);
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setDescription(description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true);
        }

        try {
            prompt.authenticate(builder.build());
        } catch (e: Throwable) {
            _authenticating = false;
            Logger.e(TAG, "Failed to start authentication", e);
            onFailure?.invoke();
        }
    }

    private const val TAG = "SettingsLock";
}
