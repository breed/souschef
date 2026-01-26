package app.s4h.souschef

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class CookLangIngredient(val name: String, val quantity: Float, val unit: String)

data class CookLangStep(val text: String, val duration: Duration?)

// Simple multiplatform YAML frontmatter parser
private fun parseYamlFrontmatter(yaml: String): Map<String?, Any?> {
    val result = mutableMapOf<String?, Any?>()
    var currentListKey: String? = null
    var currentList: MutableList<String>? = null

    for (line in yaml.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

        // Check for list item
        if (line.startsWith("  - ") || line.startsWith("- ")) {
            val value = trimmed.removePrefix("- ").trim()
            if (currentListKey != null && currentList != null) {
                currentList.add(value)
            }
            continue
        }

        // Save previous list if exists
        if (currentListKey != null && currentList != null) {
            result[currentListKey] = currentList
            currentListKey = null
            currentList = null
        }

        // Parse key: value
        val colonIdx = trimmed.indexOf(':')
        if (colonIdx > 0) {
            val key = trimmed.substring(0, colonIdx).trim()
            val value = trimmed.substring(colonIdx + 1).trim()
            if (value.isEmpty()) {
                // Start of a list
                currentListKey = key
                currentList = mutableListOf()
            } else {
                result[key] = value
            }
        }
    }

    // Save final list if exists
    if (currentListKey != null && currentList != null) {
        result[currentListKey] = currentList
    }

    return result
}

class CookLangExtractor(var markDown: String) {
    val ingredients: HashMap<String, CookLangIngredient> = HashMap()
    val steps: ArrayList<CookLangStep> = ArrayList()
    var meta: Map<String?, Any?> = emptyMap()

    // Extract images list from metadata
    val images: List<String>
        get() {
            val imagesValue = meta["images"]
            return when (imagesValue) {
                is List<*> -> imagesValue.filterIsInstance<String>()
                is String -> listOf(imagesValue)
                else -> emptyList()
            }
        }

    init {
        if (markDown.startsWith("---")) {
            // we have meta data, so lets parse it
            // NOTE: we are being lax on meta data detection ---NOMETA- will be detected as metadata
            val eol = markDown.indexOf("\n") + 1
            val eoMeta = markDown.indexOf("\n---") + 1
            val eolMeta = markDown.indexOf("\n", eoMeta) + 1
            val yamlContent = markDown.substring(eol, eoMeta)
            try {
                meta = parseYamlFrontmatter(yamlContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            markDown = markDown.substring(eolMeta)
        }

        val lines = markDown.lines()
        var lineNum = 0

        var nextStep = StringBuilder()
        while (lineNum < lines.size) {
            // skip empty lines
            while (lineNum < lines.size && lines[lineNum].isEmpty()) lineNum++

            // collect all the step text
            while (lineNum < lines.size && lines[lineNum].isNotEmpty()) {
                nextStep.append(lines[lineNum++])
            }
            val stepText = nextStep.toString()

            val processedText = extractIngredients(stepText)
            val step = extractDurationAndGenerateStep(processedText);
            steps.add(step)

            nextStep = StringBuilder()
        }
    }
    // "@(?<item>[\\w\\s}]+)\\{(?<quantity>\\d+([.]\\d+)?)%(?<unit>[\\w\\s]+)\\}"

    private fun extractIngredients(text: String): String {
        return Regex("@(?<item>[^{]+)\\{(?<quantity>\\d+([.]\\d+)?)%(?<unit>[\\w\\s]+)\\}").replace(text, transform = {
            val item = it.groups["item"]?.value ?: ""
            val quantity = it.groups["quantity"]?.value?.toFloat() ?: 0f
            val unit = it.groups["unit"]?.value ?: ""
            if (ingredients.containsKey(item)) {
                val existing = ingredients[item]!!
                if (existing.unit == unit) {
                    ingredients[item] = CookLangIngredient(item, existing.quantity + quantity, unit)
                } else {
                    // a simple hack now to handle different units. it would be nice to convert them in the future
                    ingredients[item] = CookLangIngredient(item, existing.quantity, "${existing.unit} and $quantity $unit")
                }
            } else {
                ingredients[item] = CookLangIngredient(item, quantity, unit)
            }
            val formattedQuantity = if (quantity % 1 == 0f) quantity.toInt() else quantity
            "$item $formattedQuantity $unit"
        })
    }

    /**
     * Transforms the processedText into a CookLangStep. Everything should be converted from
     * the string except for any duration which will be extracted and used in the CookLangStep
     * construction.
     */
    private fun extractDurationAndGenerateStep(processedText: String): CookLangStep {
        var duration: Duration? = null
        val finalText = Regex("~\\{(?<amount>\\d+)%(?<unit>\\w+)\\}").replace(processedText, transform = {
            val num = it.groups["amount"]?.value?.toInt() ?: 0
            val timeUnit = it.groups["unit"]?.value ?: ""
            val durationUnit = when (timeUnit.substring(0, 1).uppercase()) {
                "S" -> DurationUnit.SECONDS
                "M" -> DurationUnit.MINUTES
                "H" -> DurationUnit.HOURS
                "D" -> DurationUnit.DAYS
                else -> DurationUnit.MINUTES
            }
            duration = num.toDuration(durationUnit)
            "$num $timeUnit"
        })

        return CookLangStep(text = finalText, duration = duration);
    }

}