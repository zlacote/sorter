package zlaco.sorter.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import zlaco.sorter.data.StoragePermissions
import zlaco.sorter.ui.theme.SorterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SorterTheme {
                MainScreen(
                    viewModel = viewModel,
                    onOpenSettings = ::openPermissionSettings,
                )
            }
        }
    }

    /**
     * The permission is granted in the system settings, so the app leaves for
     * there and gets no callback. onResume is what notices the grant, which is
     * why refresh runs there rather than after the intent.
     */
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun openPermissionSettings() {
        runCatching { startActivity(StoragePermissions.settingsIntent(this)) }
            .recoverCatching {
                // Some builds ship without the per-app page; the list is slower
                // to use but better than a dead button.
                startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
    }
}
