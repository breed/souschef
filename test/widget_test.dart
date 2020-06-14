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

void main() {
  testWidgets('startup smoke test', (WidgetTester tester) async {
    WidgetsFlutterBinding.ensureInitialized();
    await Recipe.loadList();
    // Build our app and trigger a frame.
    await tester.pumpWidget(SousChefApp());

    await Future.delayed(Duration(seconds: 3));
    expect(find.text('start baking!'), findsOneWidget);
    expect(find.text('sour'), findsNothing);

    // Tap the '+' icon and trigger a frame.
    await tester.tap(find.byType(RaisedButton));
    await tester.pump();

    // Verify that our counter has incremented.
    expect(find.text('select recipe'), findsOneWidget);
    expect(find.text('sourdough bread hacked'), findsOneWidget);
    expect(find.text('sourdough pizza'), findsOneWidget);
  });
}
