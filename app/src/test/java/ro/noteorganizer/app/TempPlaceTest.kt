package ro.noteorganizer.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files as NioFiles

/**
 * Where the tests' temporary folders go: under the app's build folder, which
 * build.gradle.kts empties at the start of each test task -- never the
 * system's temp folder, where they were never removed and 30,640 of them
 * had piled up by 2026-10-23.
 */
class TempPlaceTest {

    @Test
    fun `a test's temporary folder is made under build, not in the system's temp folder`() {
        val made = NioFiles.createTempDirectory("tempplace").toFile()
        val path = made.canonicalPath.replace(File.separatorChar, '/')
        assertTrue("made at $path", "/build/test-tmp/" in path)
    }

    @Test
    fun `the folder holds only this run's folders -- the last run's were emptied out`() {
        // Gradle empties it before it starts this JVM, so anything older
        // than the JVM is left over: a run's 665 folders, piling up again.
        val started = ManagementFactory.getRuntimeMXBean().startTime
        val left = File(System.getProperty("java.io.tmpdir")).listFiles().orEmpty()
            .filter { it.lastModified() < started - 1000 }
        assertTrue("left from an earlier run: ${left.take(3).map { it.name }}", left.isEmpty())
    }
}
