import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

fun main() {
    val disposable = Disposer.newDisposable()
    try {
        val env = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(env.project)
        var count = 0
        var errors = 0
        File("app/src").walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            count++
            val tree = factory.createFile(file.name, file.readText())
            PsiTreeUtil.collectElementsOfType(tree, PsiErrorElement::class.java).forEach { error ->
                val line = tree.text.take(error.textOffset).count { it == '\n' } + 1
                println("${file.path}:$line ${error.errorDescription}")
                errors++
            }
        }
        println("Parsed $count Kotlin files; $errors syntax errors")
        check(errors == 0)
    } finally { Disposer.dispose(disposable) }
}
