import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

/**
 * Output of `node --version`, or `"absent"` when Node cannot be run, so a missing Node surfaces as
 * the contract harness's own error at test time instead of a configuration-time exec failure.
 */
abstract class NodeVersionSource : ValueSource<String, ValueSourceParameters.None> {
  @get:Inject abstract val execOperations: ExecOperations

  override fun obtain(): String =
      try {
        val out = ByteArrayOutputStream()
        execOperations.exec {
          commandLine("node", "--version")
          standardOutput = out
        }
        out.toString().trim()
      } catch (_: Exception) {
        "absent"
      }
}
