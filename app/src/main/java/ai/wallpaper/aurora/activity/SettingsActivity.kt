package ai.wallpaper.aurora.activity

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import ai.wallpaper.aurora.MainActivity
import ai.wallpaper.aurora.R
import ai.wallpaper.aurora.service.VideoLiveWallpaperService
import java.io.File

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings, rootKey)

            findPreference<SwitchPreferenceCompat>(getString(R.string.preference_play_video_with_sound))?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    val shouldUnmute = newValue as Boolean
                    val unmuteFile = File(requireContext().filesDir, "unmute")

                    if (shouldUnmute) {
                        unmuteFile.createNewFile()
                        VideoLiveWallpaperService.unmuteMusic(requireContext())
                    } else {
                        unmuteFile.delete()
                        VideoLiveWallpaperService.muteMusic(requireContext())
                    }
                    true
                }
            }

            findPreference<SwitchPreferenceCompat>(getString(R.string.preference_hide_icon_from_launcher))?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    val shouldHide = newValue as Boolean
                    val componentName = ComponentName(requireContext(), MainActivity::class.java)
                    val newState = if (shouldHide) {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    }
                    requireContext().packageManager.setComponentEnabledSetting(
                        componentName,
                        newState,
                        PackageManager.DONT_KILL_APP
                    )
                    true
                }
            }
        }
    }
}
