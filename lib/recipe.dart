import 'dart:io';

import 'package:android_intent/android_intent.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

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
  RecipeStep(this.recipe, this.index, this.description, this.time, this.autoSetTimer);

  final Recipe recipe;
  final int index; // step number
  final String description; // from markdown
  final Duration time; // from markdown
  final bool autoSetTimer; // from markdown
  DateTime started; // from DB (null if not set)
  DateTime finished; // from DB (null if not set)
  bool timerSet = false; // transient (not from DB)
  RecipeStep get prev => index == 0 ? null : recipe.steps[index - 1];

  RecipeStep get next =>
      index == recipe.steps.length - 1 ? null : recipe.steps[index + 1];

  void start() {
    started = DateTime.now();
    recipe.logToDB(index, Recipe.START_STATE);
    if (autoSetTimer) {
      scheduleAlarm(time.inSeconds, description);
      timerSet = true;
    }
  }

  void finish() {
    finished = DateTime.now();
    recipe.logToDB(index, Recipe.FINISH_STATE);
    if (next != null) {
      if (next.started == null) next.start();
      recipe.advanceStep();
    } else {
      recipe.finishRecipe();
    }
  }

  DateTime get plannedFinish =>
      prev == null ? willStart.add(time) : prev.plannedFinish.add(time);

  DateTime get willFinish => finished != null
      ? finished
      : started != null
          ? started.add(time)
          : prev == null ? DateTime.now().add(time) : prev.willFinish.add(time);

  DateTime get willStart => started != null
      ? started
      : prev == null ? DateTime.now() : prev.willFinish;

  String get durationString =>
      time.inSeconds > 0 ? "${formatDuration(time)}" : "";

  String get runInterval => time.inSeconds > 0 || finished != null
      ? "${formatDateTime(willStart)} to ${formatDateTime(willFinish)} planned ${formatDateTime(plannedFinish)}"
      : "";
}

class PastRecipe {
  PastRecipe(this.startTime, this.recipeName);

  DateTime startTime;
  DateTime finishedTime;
  String recipeName;

  String toString() => "${recipeName}@${startTime}-${finishedTime}";
}

class Recipe {
  static Database db;

  static Future<void> loadDB() async => db = await openDatabase(
        join((await getApplicationDocumentsDirectory()).path, 'history.db'),
        onCreate: (db, version) => db.execute(
          "CREATE TABLE "
          "bakes(ts INTEGER PRIMARY KEY, start INTEGER, recipe TEXT, step INTEGER, state INTEGER)",
        ),
        version: 1,
      );

  static const int START_STATE = 0;
  static const int FINISH_STATE = 1;
  static final emptyRecipe = Recipe._("");

  DateTime startTime;
  DateTime finishTime;
  final String recipeName;
  String text = "# start baking!"; // this should be markdown

  Future<void> logToDB(int step, int state) async {
    var now = DateTime.now();
    var entry = {
      'ts': now.millisecondsSinceEpoch,
      'start': startTime.millisecondsSinceEpoch,
      'recipe': recipeName,
      'step': step,
      'state': state,
    };
    print("logging ${entry}");
    await db.insert('bakes', entry);
    /* the timestamp needs to be unique, so wait a bit to ensure that happens */
    sleep(Duration(milliseconds: 2));
  }

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

  static Future<Recipe> setupRecipe(String recipeName) async {
    print("starting $recipeName");
    Recipe recipe = await initRecipe(recipeName);
    return recipe;
  }

  void startRecipe() async {
    startTime = DateTime.now();
    await logToDB(-1, START_STATE);
    if (steps[0].started == null) {
      steps[0].start();
    }
  }

  void finishRecipe() async {
    finishTime = DateTime.now();
    await logToDB(-1, FINISH_STATE);
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

  static Future<Recipe> initRecipe(String recipeName) async {
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
        bool autoSetTimer = false;
        var firstChar = line.substring(0, 1);
        String desc = line;
        if (firstChar == "*" || firstChar == "+") {
          mins = int.parse(extractFirstWord(line.substring(1)));
          desc = trimFirstWord(line);
          autoSetTimer = firstChar == "*";
        }
        var step = RecipeStep(
            recipe, index, desc.trim(), Duration(minutes: mins), autoSetTimer);
        recipe.steps.add(step);
        index++;
      } else {
        recipe.text += line + '\n';
      }
    }
    return recipe;
  }

  static Future<Recipe> continueRecipe(DateTime startTime) async {
    print("continuing $startTime");
    List<Map> results = await db.query('bakes',
        columns: ['start', 'step', 'ts', 'state', 'recipe'],
        where: 'start = ?',
        whereArgs: [startTime.millisecondsSinceEpoch]);
    assert(results.length > 0);
    Recipe recipe = await initRecipe(results.first['recipe']);
    if (recipe == null) {
      return null;
    }
    recipe.startTime = startTime;
    for (var entry in results) {
      if (entry['step'] != -1) {
        DateTime dt = DateTime.fromMillisecondsSinceEpoch(entry['ts']);
        switch (entry['state']) {
          case START_STATE:
            recipe.steps[entry['step']].started = dt;
            break;
          case FINISH_STATE:
            recipe.steps[entry['step']].finished = dt;
            break;
        }
      } else {
        if (entry['state'] == FINISH_STATE) {
          recipe.finishTime = DateTime.fromMillisecondsSinceEpoch(entry['ts']);
        }
      }
    }
    return recipe;
  }

  static Future<List<PastRecipe>> pastRecipes() async {
    List<Map> results = await db.query(
      'bakes',
      columns: ['start', 'recipe', 'ts', 'state', 'step'],
      orderBy: 'start',
      where: 'step == -1',
    );
    Map<int, PastRecipe> map = Map<int, PastRecipe>();
    for (var entry in results) {
      int start = entry['start'];
      int ts = entry['ts'];
      String recipeName = entry['recipe'];
      PastRecipe pastRecipe = map[start];
      if (pastRecipe == null) {
        pastRecipe =
            PastRecipe(DateTime.fromMillisecondsSinceEpoch(start), recipeName);
        map[start] = pastRecipe;
      }
      if (entry['state'] == FINISH_STATE) {
        pastRecipe.finishedTime = DateTime.fromMillisecondsSinceEpoch(ts);
      }
    }

    var keys = map.keys.toList(growable: false);
    keys.sort((a, b) => a - b);
    List<PastRecipe> past = List<PastRecipe>();
    for (var key in keys) past.add(map[key]);
    return past;
  }
}
