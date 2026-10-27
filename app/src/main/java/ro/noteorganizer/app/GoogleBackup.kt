package ro.noteorganizer.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * The app's part in Android's own backup: to the person's Google account
 * when backup is on in the phone's settings, and straight to a new phone
 * when they move to one (Android 12's device-to-device transfer). Both
 * come here, as a full backup of the app's files by the rules in
 * `data_extraction_rules.xml` and, on Android 11 and older,
 * `backup_rules.xml`.
 *
 * Settings → Backup can turn the app's part off ([Settings.googleBackupOn]).
 * Off, this hands Android nothing: no file of the app's is read into the
 * backup, so the copy Android keeps holds none of the notes. On -- the
 * default, as it always was -- Android's own rules apply unchanged.
 *
 * The key-value half of Android's backup is not used: the two methods it
 * needs do nothing.
 */
class NotesBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (Settings.googleBackupOn(Settings.load(File(filesDir, "notebook")))) super.onFullBackup(data)
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
    }
}
