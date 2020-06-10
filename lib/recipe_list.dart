import 'package:flutter/material.dart';
import 'recipe.dart';

void dismissAndStart(BuildContext context, String recipeName) {
    Navigator.of(context).pop(recipeName);

}

Widget RecipeListContainer() => Container(
    width: double.maxFinite,
        child: ListView.builder(
      scrollDirection: Axis.vertical,
      shrinkWrap: true,
      itemCount: Recipe.recipeList.length,
      itemBuilder: (BuildContext context, int index) =>
          ListTile(title: Text(Recipe.recipeList[index][1]), enabled: true,onTap: () => dismissAndStart(context, Recipe.recipeList[index][0]),)
    ));
