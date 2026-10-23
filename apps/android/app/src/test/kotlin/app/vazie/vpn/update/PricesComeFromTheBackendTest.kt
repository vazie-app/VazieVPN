package app.vazie.vpn.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The app names no price: it sells nothing, and a price written into a string resource would be the first sign
 * of a purchase screen creeping back. */
class PricesComeFromTheBackendTest {

    /** Any digits followed by a currency sign or name: `299 ₽`, `2,990 ₽`, `$5`, `199 RUB`, `299 руб.` */
    private val priceInText = Regex("""(\d[\d\s .,]*\s?(₽|руб|RUB|USD|EUR|\$|€))|((₽|\$|€)\s?\d)""", RegexOption.IGNORE_CASE)

    /** `%1$s`, `%2$d`, `%s`: placeholders, not money. */
    private val formatSpecifier = Regex("""%(\d+\$)?[sdf]""")

    @Test
    fun `no string resource of any module names a price`() {
        val offenders = resourceFiles().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> "<string" in line || "<item" in line }
                .filter { (_, line) -> priceInText.containsMatchIn(line.replace(formatSpecifier, "")) }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }

        assertTrue(offenders.isEmpty(), "a price is written into a resource:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `the scan really looks at the screens' resources`() {
        val names = resourceFiles().map { it.path.replace('\\', '/') }
        assertTrue(names.any { "feature/home/src/main/res/values-ru" in it }, names.toString())
        assertTrue(names.any { "feature/onboarding/src/main/res/values/" in it }, names.toString())
        assertTrue(names.any { "feature/settings/src/main/res/values/" in it }, names.toString())
    }

    private fun resourceFiles(): List<File> {
        val root = File("..").canonicalFile
        return listOf("app", "core", "feature", "data", "update", "vpn")
            .map { File(root, it) }
            .filter { it.exists() }
            .flatMap { module ->
                module.walkTopDown()
                    .onEnter { it.name != "build" }
                    .filter { it.isFile && it.name.endsWith(".xml") && "src/main/res/values" in it.path.replace('\\', '/') }
                    .toList()
            }
    }
}
