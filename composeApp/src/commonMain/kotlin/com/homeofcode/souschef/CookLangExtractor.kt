package com.homeofcode.souschef

import org.yaml.snakeyaml.Yaml
import java.util.Locale
import java.util.regex.Pattern
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

data class CookLangIngredient(val name: String, val quantity: Float, val unit: String)

data class CookLangStep(val text: String, val duration: Duration?)

class CookLangExtractor(var markDown: String) {
    val ingredients: HashMap<String, CookLangIngredient> = HashMap()
    val steps: ArrayList<CookLangStep> = ArrayList()
    var meta: Map<String?, Any?> = emptyMap()

    init {
        if (markDown.startsWith("---")) {
            // we have meta data, so lets parse it
            // NOTE: we are being lax on meta data detection ---NOMETA- will be detected as metadata
            val eol = markDown.indexOf("\n") + 1
            val eoMeta = markDown.indexOf("\n---") + 1
            val eolMeta = markDown.indexOf("\n", eoMeta) + 1
            val yamlContent = markDown.substring(eol, eoMeta)
            try {
                meta = Yaml().load(yamlContent) as Map<String?, Any?>
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