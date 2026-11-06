package ro.noteorganizer.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * The app's part in Android's own backup -- to the person's Google account
 * when backup is on in the phone's settings, and to a new phone in Android
 * 12's device-to-device transfer -- as a full backup of the app's files by
 * the rules in `data_extraction_rules.xml` (`backup_rules.xml` on Android 11
 * and older). Settings → Backup turns it off ([Settings.googleBackupOn]):
 * Android is then handed one small file saying so
 * ([Settings.GOOGLE_BACKUP_OFF_MARK]) and nothing of the notebook, which
 * replaces the copy made while it was on (smaller lessons: the backup's off
 * mark). The key-value half of Android's backup is not used.
 */
class NotesBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        // Decided by the rule Settings → Backup shows, the mark of a restore
        // not yet opened included: read from settings.json alone, that phone
        // read as on, and this deleted the only record of the person's off.
        when (val take = Settings.forFullBackup(File(filesDir, "notebook"))) {
            Settings.FullBackup.Everything -> super.onFullBackup(data)
            is Settings.FullBackup.OnlyMark -> fullBackupFile(take.mark, data)
            Settings.FullBackup.Nothing -> {}
        }
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
    }
}
