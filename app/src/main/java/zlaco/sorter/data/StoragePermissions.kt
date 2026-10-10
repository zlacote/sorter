package zlaco.sorter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * Screenshots belong to the shell that took them, so moving one needs the
 * "All files access" grant. It is requested once, in the system settings, and
 * never again: with it in place the media store accepts a move without asking.
 */
object StoragePermissions {

    fun hasAllFilesAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

    /**
     * Opens the settings page for this app specifically, rather than the list of
     * every app holding the permission, so the toggle to flip is on screen
     * already.
     */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
