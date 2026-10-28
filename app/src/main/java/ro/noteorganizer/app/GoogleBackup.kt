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
 * Off, Android is handed one small file saying so
 * ([Settings.GOOGLE_BACKUP_OFF_MARK]) and nothing of the notebook. Handed
 * nothing at all, as it first was, Android kept the copy made while the
 * switch was on -- notes and all, with a settings file that could not say
 * off -- and a reinstall or a new phone brought it back, switch on. The
 * mark replaces that copy at the next backup, and a restore brings back
 * the answer, not the notes. On -- the default, as it always was --
 * Android's own rules apply unchanged.
 *
 * The key-value half of Android's backup is not used: the two methods it
 * needs do nothing.
 */
class NotesBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        val dir = File(filesDir, "notebook")
        val mark = File(dir, Settings.GOOGLE_BACKUP_OFF_MARK)
        if (Settings.googleBackupOn(Settings.load(dir))) {
            // Not carried in a backup made while on: restored, it would turn
            // off what the person had turned on.
            mark.delete()
            super.onFullBackup(data)
        } else if (Files.writeAtomic(mark, Settings.offMark()) == null) {
            fullBackupFile(mark, data)
        }
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) {
    }

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) {
    }
}
