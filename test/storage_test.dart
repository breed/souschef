// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility that Flutter provides. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:souschef/main.dart';
import 'package:souschef/recipe.dart';
import 'package:souschef/storage.dart';

void main() {
  testWidgets('storage smoke test', (WidgetTester tester) async {
    WidgetsFlutterBinding.ensureInitialized();
    await setupStorage();
    await Recipe.loadList();
    // Build our app and trigger a frame.
    await tester.pumpWidget(SousChefApp());

    Recipe recipe = await getCurrentRecipe(false, false);

    expect(recipe, null);

    final RECIPE_NAME = "sourdough_bread.md";
    recipe = await Recipe.initRecipe(RECIPE_NAME, false, false);

    expect(recipe.recipeName, RECIPE_NAME);

    for (var step in recipe.steps) {
      expect(step.started, null);
      expect(step.finished, null);
    }

    var now = DateTime.now();

    recipe.steps[0].started = now;
    await storeCurrentRecipe(recipe);

    recipe = null;

    recipe = await getCurrentRecipe(false, false);

    expect(recipe.recipeName, RECIPE_NAME);

    bool first = true;
    for (var step in recipe.steps) {
      expect(step.started, first ? now : null);
      expect(step.finished, null);
      first = false;
    }
  });
}
