package app.s4h.souschef

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CookLangExtractorTest {
    val recipe =
        """---
title: sourdough bread hacked
description: this recipes was derived from a couple of cooks excellent recipe. there are great videos on how to do each step at https://www.acouplecooks.com/sourdough-bread-recipe-simplified-guide/ .
---

mix @all purpose or bread flour{400%g}, @whole wheat flour{50%g}, @room temperature water or milk (50/50 works well){350%ml}.

let sit for ~{60%min} to autolyse.

add @active sourdough{80%g}, @salt{10%g}.

proof for ~{30%min}.

fold dough, proof for ~{30%min}.

fold dough, proof for ~{30%min}.

fold dough, proof for ~{45%min}.

fold dough, proof for ~{90%min}.

shape and put in banneton

proof for ~{60%min}.

refrigerate for at least ~{3%hours}.

preheat dutch oven to ~{515%F} for ~{30%min}.

place on parchment in dutch oven and bake for ~{7%min} at 515F.

score loaf and bake for ~{10%min}.

remove from dutch oven, change oven temp to 400F, and bake for ~{17%min}."""

    @Test
    fun testExtractRecipeMetadata() {

        val extractor = CookLangExtractor(recipe)

        assertEquals("sourdough bread hacked", extractor.meta["title"])
        assertEquals(
            "this recipes was derived from a couple of cooks excellent recipe. there are great videos on how to do each step at https://www.acouplecooks.com/sourdough-bread-recipe-simplified-guide/ .",
            extractor.meta["description"]
        )
    }

    @Test
    fun testExtractIngredients() {
        val extractor = CookLangExtractor(recipe)
        val ingredients = extractor.ingredients

        assertEquals(5, ingredients.size)
        assertEquals(
            CookLangIngredient("all purpose or bread flour", 400f, "g"),
            ingredients["all purpose or bread flour"]
        )
        assertEquals(
            CookLangIngredient("whole wheat flour", 50f, "g"),
            ingredients["whole wheat flour"]
        )
        assertEquals(
            CookLangIngredient(
                "room temperature water or milk (50/50 works well)",
                350f,
                "ml"
            ), ingredients["room temperature water or milk (50/50 works well)"]
        )
        assertEquals(
            CookLangIngredient("active sourdough", 80f, "g"),
            ingredients["active sourdough"]
        )
        assertEquals(CookLangIngredient("salt", 10f, "g"), ingredients["salt"])
    }

    @Test
    fun testExtractInstructions() {
        val extractor = CookLangExtractor(recipe)
        val instructions = extractor.steps

        assertEquals(15, instructions.size)
        // just check the first and last
        assertEquals(
            "mix all purpose or bread flour 400 g, whole wheat flour 50 g, room temperature water or milk (50/50 works well) 350 ml.",
            instructions[0].text
        )
        assertNull(instructions[0].duration)
        assertEquals("let sit for 60 min to autolyse.", instructions[1].text)
        assertEquals(60.minutes, instructions[1].duration)
        assertEquals(
            "remove from dutch oven, change oven temp to 400F, and bake for 17 min.",
            instructions[14].text
        )
        assertEquals(17.minutes, instructions[14].duration)
    }
}