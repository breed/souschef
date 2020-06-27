import 'package:android_intent/android_intent.dart';
import 'package:flutter/services.dart';

import 'storage.dart';

void scheduleAlarm(int seconds, String message) async =>
    await AndroidIntent(action: "android.intent.action.SET_TIMER", arguments: {
      "android.intent.extra.alarm.LENGTH": seconds,
      "android.intent.extra.alarm.MESSAGE": message,
      "android.intent.extra.alarm.SKIP_UI": true
    }).launch();

String trimFirstWord(String words) =>
    words.replaceFirst(RegExp(r'\s*[^\s]+\s*'), "");

String extractFirstWord(String words) =>
    RegExp(r'\s*[^\s]+\s*').stringMatch(words).trim();

String formatDuration(Duration duration) => duration.inMinutes > 90
    ? "${(duration.inMinutes / 60).toStringAsPrecision(2)} hours"
    : "${duration.inMinutes} mins";

String formatDateTime(DateTime dateTime) => dateTime.hour == 0
    ? "12:${dateTime.minute.toString().padLeft(2, '0')}a"
    : dateTime.hour > 12
        ? "${dateTime.hour - 12}:${dateTime.minute.toString().padLeft(2, '0')}p"
        : "${dateTime.hour}:${dateTime.minute.toString().padLeft(2, '0')}a";

class RecipeStep {
  RecipeStep(this.recipe, this.index, this.description, this.time,
      this.autostartTimer, this.relativeTime);

  final Recipe recipe;
  final int index; // step number
  final String description; // from markdown
  final Duration time; // from markdown
  final bool autostartTimer; // from prefs
  final bool relativeTime; // from prefs
  DateTime started; // from DB (null if not set)
  DateTime finished; // from DB (null if not set)
  bool timerSet = false; // transient (not from DB)
  RecipeStep get prev => index == 0 ? null : recipe.steps[index - 1];

  RecipeStep get next =>
      index == recipe.steps.length - 1 ? null : recipe.steps[index + 1];

  void start() {
    started = DateTime.now();
    storeCurrentRecipe(recipe);
    if (autostartTimer) {
      scheduleAlarm(time.inSeconds, description);
      timerSet = true;
    }
  }

  void finish() {
    finished = DateTime.now();
    if (next != null) {
      if (next.started == null) next.start();
      recipe.advanceStep();
    } else {
      recipe.finishRecipe();
    }
    storeCurrentRecipe(recipe);
  }

  DateTime get plannedStart => prev == null
      ? started == null ? DateTime.now() : started
      : prev.plannedStart.add(prev.time);

  String get durationString =>
      time.inSeconds > 0 ? "${formatDuration(time)}" : "";

  String get runInterval =>
      "planned start ${formatDateTime(plannedStart)} " +
          (finished != null
              ? " finished ${formatDateTime(finished)}"
              : "");
}

class PastRecipe {
  PastRecipe(this.startTime, this.recipeName);

  DateTime startTime;
  DateTime finishedTime;
  String recipeName;

  String toString() => "${recipeName}@${startTime}-${finishedTime}";
}

class Recipe {
  static const int START_STATE = 0;
  static const int FINISH_STATE = 1;
  static final emptyRecipe = Recipe._("");

  DateTime startTime;
  DateTime finishTime;
  final String recipeName;
  String text = "# start baking!"; // this should be markdown

  Recipe._(this.recipeName);

  List<RecipeStep> steps = [];
  int activeStepIndex = 0;

  void findActiveStep() {
    for (activeStepIndex = 0;
        activeStepIndex < steps.length;
        activeStepIndex++) {
      if (activeStep.started != null && activeStep.finished == null) break;
    }
    if (activeStep == null) {
      // this means we haven't started so set the first step as active.
      activeStepIndex = 0;
    }
  }

  void advanceStep() => activeStepIndex++;

  RecipeStep get activeStep =>
      activeStepIndex < steps.length && activeStepIndex >= 0
          ? steps[activeStepIndex]
          : null;

  static Future<Recipe> setupRecipe(String recipeName, bool autostartTimer,
      bool relativeTime) async {
    print("starting $recipeName");
    Recipe recipe = await initRecipe(recipeName, autostartTimer, relativeTime);
    return recipe;
  }

  void startRecipe() async {
    startTime = DateTime.now();
    if (steps[0].started == null) {
      steps[0].start();
    }
  }

  void finishRecipe() async {
    finishTime = DateTime.now();
  }

  static List<List<String>> recipeList = [];

  static Future<void> loadList() async {
    print("loading list");
    String list = await rootBundle.loadString("assets/recipes/list");
    for (var line in list.split("\n")) {
      var fname = extractFirstWord(line);
      var title = trimFirstWord(line);
      print("BR $fname $title");
      recipeList.add([fname, title]);
    }
  }

  static Future<Recipe> initRecipe(String recipeName, bool autostartTimer,
      bool relativeTime) async {
    var recipeFileName = "assets/recipes/${recipeName}";
    String text;
    try {
      text = await rootBundle.loadString(recipeFileName);
    } catch (e) {
      return null;
    }
    Recipe recipe = Recipe._(recipeName);
    recipe.steps = List<RecipeStep>();
    recipe.text = "";
    int index = 0;
    for (var line in text.split("\n")) {
      if (line.startsWith("=>")) {
        line = trimFirstWord(line);
        int mins = 0;
        var firstChar = line.substring(0, 1);
        String desc = line;
        bool stepAutostartTimer = autostartTimer;
        if (firstChar == "*" || firstChar == "+") {
          mins = int.parse(extractFirstWord(line.substring(1)));
          desc = trimFirstWord(line);
          stepAutostartTimer = stepAutostartTimer || firstChar == "*";
        }
        var step = RecipeStep(
            recipe, index, desc.trim(), Duration(minutes: mins),
            stepAutostartTimer, relativeTime);
        recipe.steps.add(step);
        index++;
      } else {
        recipe.text += line + '\n';
      }
    }
    return recipe;
  }
}
