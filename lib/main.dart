import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:android_intent/android_intent.dart';
import 'dart:async';
import 'dart:io';

/**
 * a simple app to help with managing the steps of a recipe. it is
 * targeted at recipes with multiple steps and multiple different timers.
 *
 * recipes are written in markdown with a special added syntax:
 *    any lines that start with => will be interpreted as a recipe step
 *    with the format time_in_minutes followed by a description. for example,
 *    => 60 mix flour, water and milk, let sit
 *    will be interpreted as a step that has a 60 minute timer associated with
 *    it and the description "mix flour, water and milk, let sit"
 *
 * a "bakes" sqllite DB holds the state of instances of the recipe. each
 * instance is uniquely identified by the time the recipe was started. the
 * current schema is:
 *    ts INTEGER PRIMARY KEY - this is time in milliseconds it simply
 *                             corresponds to when an event happened
 *    start INTEGER - when the bake started (the recipe began). it uniquely
 *                    identifies an instance of the recipe
 *    recipe TEXT - the name of the recipe. it should correspond to the recipe
 *                  markdown name without the .md extension
 *    step INTEGER - the step that this entry corresponds to. -1 means the
 *                   recipe itself (either the start or the end)
 *    state INTEGER - shoud be either START_STATE or FINISH_STATE
 */

/**
 * quick and dirty alarm scheduler. i'm not sure how to do this for iOS...
 */
void scheduleAlarm(int seconds, String message) async =>
    await AndroidIntent(action: "android.intent.action.SET_TIMER", arguments: {
      "android.intent.extra.alarm.LENGTH": seconds,
      "android.intent.extra.alarm.MESSAGE": message,
      "android.intent.extra.alarm.SKIP_UI": true
    }).launch();

void main() => runApp(SousChefApp());

class SousChefApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'sous chef',
        theme: ThemeData(
          primarySwatch: Colors.deepPurple,
          visualDensity: VisualDensity.adaptivePlatformDensity,
        ),
        // hardcoded for now, but hopefully there will be more in the future
        home: RecipeSteps(title: 'sourdough'),
      );
}

class RecipeSteps extends StatefulWidget {
  RecipeSteps({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _RecipeStepsState createState() => _RecipeStepsState(title);
}

String formatDuration(Duration duration) =>
    duration.toString().split(".").first;

String formatDateTime(DateTime dateTime) => dateTime.year == DateTime.now().year
    ? dateTime.toString().split(".").first.substring(4)
    : dateTime.toString().split(".").first;

class Step {
  Step(this.recipe, this.index, this.description, this.time);

  final Recipe recipe;
  final int index; // step number
  final String description; // from markdown
  final Duration time; // from markdown
  DateTime started; // from DB (null if not set)
  DateTime finished; // from DB (null if not set)
  bool alarmScheduled = false; // transient (not from DB)
  Step get prev => index == 0 ? null : recipe.steps[index - 1];
  Step get next =>
      index == recipe.steps.length - 1 ? null : recipe.steps[index + 1];

  void start() {
    started = DateTime.now();
    recipe.logToDB(index, Recipe.START_STATE);
  }

  void finish() {
    finished = DateTime.now();
    recipe.logToDB(index, Recipe.FINISH_STATE);
    if (next != null) {
      if (next.started == null) next.start();
    } else {
      recipe.finishRecipe();
    }
  }

  String get runInterval => started == null
      ? "not started. takes ${formatDuration(time)}"
      : finished == null
          ? "estimated ${formatDateTime(started)} to ${formatDateTime(started.add(time))}"
          : finished == started
              ? "completed ${formatDateTime(started)}"
              : "${formatDateTime(started)} to ${formatDateTime(finished)}";
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

  DateTime startTime;
  DateTime finishTime;
  final String recipe;
  String text; // this should be markdown

  Future<void> logToDB(int step, int state) async {
    var now = DateTime.now();
    var entry = {
      'ts': now.millisecondsSinceEpoch,
      'start': startTime.millisecondsSinceEpoch,
      'recipe': recipe,
      'step': step,
      'state': state,
    };
    await db.insert('bakes', entry);
    /* the timestamp needs to be unique, so wait a bit to ensure that happens */
    sleep(Duration(milliseconds: 2));
  }

  Recipe._(this.recipe);

  List<Step> steps;
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

  Step get activeStep => activeStepIndex < steps.length && activeStepIndex >= 0
      ? steps[activeStepIndex]
      : null;

  static Future<Recipe> startRecipe(String recipeName) async {
    Recipe recipe = await initRecipe(recipeName);
    recipe.startTime = DateTime.now();
    await recipe.logToDB(-1, START_STATE);
    return recipe;
  }

  void finishRecipe() async {
    finishTime = DateTime.now();
    await logToDB(-1, FINISH_STATE);
  }

  static Future<Recipe> initRecipe(String recipeName) async {
    String text =
        await rootBundle.loadString("assets/recipes/${recipeName}.md");
    Recipe recipe = Recipe._(recipeName);
    recipe.text = "";
    recipe.steps = List<Step>();
    int index = 0;
    for (var line in text.split("\n")) {
      if (line.startsWith("=>")) {
        var parts = line.split(" ");
        int mins = int.parse(parts[1]);
        String desc = parts.sublist(2).join(" ");
        var step = Step(recipe, index, desc, Duration(minutes: mins));
        recipe.steps.add(step);
        index++;
      } else {
        recipe.text += line + '\n';
      }
    }
    return recipe;
  }

  static Future<Recipe> continueRecipe(DateTime startTime) async {
    List<Map> results = await db.query('bakes',
        columns: ['start', 'step', 'ts', 'state', 'recipe'],
        where: 'start = ?',
        whereArgs: [startTime.millisecondsSinceEpoch]);
    assert(results.length > 0);
    Recipe recipe = await initRecipe(results.first['recipe']);
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

class _RecipeStepsState extends State<RecipeSteps> {
  /* all the async stuff is tracked in this Future. it returns a recipe, but
   * it is supposed to set the recipe member before it finishes. */
  Future<Recipe> setupFuture;
  Recipe recipe;
  final String recipeName;

  _RecipeStepsState(this.recipeName);

  @override
  void initState() {
    super.initState();
    Future<Recipe> recipeFuture =
        Recipe.loadDB().then((value) => Recipe.pastRecipes().then((recipes) {
              if (recipes.length > 0 && recipes.last.finishedTime == null) {
                return Recipe.continueRecipe(recipes.last.startTime)
                    .then((recipe) {
                  return recipe;
                });
              } else {
                return Recipe.startRecipe(recipeName);
              }
            }));
    setupFuture = recipeFuture
        .then((Recipe recipe) => this.recipe = recipe)
        .then((recipe) => this.recipe = recipe);
  }

  Container constructStepList() {
    /* make a step tile */
    ListTile makeListTile(Step step, bool active) => ListTile(
          contentPadding:
              EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
          leading: step.index == 0 && step.started == null
              ? IconButton(
                  icon: Icon(Icons.play_arrow, size: 30.0),
                  onPressed: () {
                    step.start();
                    setState(() {});
                  })
              : Checkbox(
                  value: step.finished != null,
                  onChanged: (value) {
                    if (value &&
                        (step.prev == null || step.prev.finished != null)) {
                      step.finish();
                      setState(() {});
                    }
                  },
                ),
          title: Text(step.description),
          subtitle: Text("${step.runInterval}"),
          trailing: step.finished == null &&
                  (step.index == 0 ||
                      recipe.steps[step.index - 1].finished != null)
              ? (step.alarmScheduled
                  ? Icon(Icons.hourglass_full)
                  : IconButton(
                      icon: Icon(Icons.alarm_add, size: 30.0),
                      onPressed: () {
                        scheduleAlarm(
                            step.time.inSeconds, "${step.description}");
                        step.alarmScheduled = true;
                        setState(() {});
                      }))
              : null,
        );
    Card makeCard(Step step, bool active) => Card(
        elevation: 8.0,
        margin: EdgeInsets.symmetric(horizontal: 10.0, vertical: 6.0),
        child: Container(
          decoration: BoxDecoration(),
          child: makeListTile(step, active),
        ));
    /* the list starts with a markdown widgit with the recipe. then all the
     * steps, finally a button to restart the recipe. that is why there are +2
     * items. since the first item is not a step. we need to subtract one from
     * the item index to know the actual (zero-based) step we are on. */
    return Container(
        child: ListView.builder(
      scrollDirection: Axis.vertical,
      shrinkWrap: true,
      itemCount: recipe.steps.length + 2,
      itemBuilder: (BuildContext context, int index) {
        if (index == 0)
          return Markdown(
            data: recipe.text,
            shrinkWrap: true,
          );
        index--;
        if (index == recipe.steps.length) {
          return RaisedButton(
              onPressed: () => Recipe.startRecipe(recipeName).then((r) {
                    setState(() => this.recipe = r);
                  }),
              child: Text("start new bake"));
        }
        return makeCard(
            recipe.steps[index],
            (recipe.steps[index].finished == null) &&
                (index == 0 || recipe.steps[index - 1].finished != null));
      },
    ));
  }

  @override
  Widget build(BuildContext buildContext) => Scaffold(
      body: FutureBuilder(
          future: setupFuture,
          builder: (BuildContext buildContext, AsyncSnapshot<void> snapshot) {
            if (recipe == null) {
              return Text("loading...");
            }
            return constructStepList();
          }));
}
