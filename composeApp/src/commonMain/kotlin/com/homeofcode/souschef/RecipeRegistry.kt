package com.homeofcode.souschef

data class RecipeInfo(
    val id: String,
    val name: String,
    val description: String,
    val filePath: String
)

object RecipeRegistry {
    val recipes = listOf(
        RecipeInfo(
            id = "sourdough",
            name = "Sourdough Bread",
            description = "Classic sourdough bread with a crispy crust",
            filePath = "files/sourdough.cooklang"
        ),
        RecipeInfo(
            id = "focaccia",
            name = "Focaccia",
            description = "Italian olive oil flatbread with herbs",
            filePath = "files/focaccia.cooklang"
        ),
        RecipeInfo(
            id = "cinnamon-rolls",
            name = "Cinnamon Rolls",
            description = "Sweet rolls with cinnamon filling and cream cheese frosting",
            filePath = "files/cinnamon-rolls.cooklang"
        ),
        RecipeInfo(
            id = "pizza-dough",
            name = "Pizza Dough",
            description = "Neapolitan-style pizza dough for crispy pizzas",
            filePath = "files/pizza-dough.cooklang"
        ),
        RecipeInfo(
            id = "banana-bread",
            name = "Banana Bread",
            description = "Moist and delicious banana bread",
            filePath = "files/banana-bread.cooklang"
        )
    )

    fun getRecipeById(id: String): RecipeInfo? = recipes.find { it.id == id }
}
