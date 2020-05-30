import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:android_intent/android_intent.dart';
import 'dart:async';

void scheduleAlarm(int seconds, String message) async {
  var intent =
      AndroidIntent(action: "android.intent.action.SET_TIMER", arguments: {
    "android.intent.extra.alarm.LENGTH": seconds,
    "android.intent.extra.alarm.MESSAGE": message,
    "android.intent.extra.alarm.SKIP_UI": true
  });
  await intent.launch();
}

void main() {
  runApp(SousChefApp());
}

class SousChefApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'sourdough bread (a couple cooks)',
        theme: ThemeData(
          primarySwatch: Colors.deepPurple,
          visualDensity: VisualDensity.adaptivePlatformDensity,
        ),
        home: RecipeSteps(title: 'sourdough bread (a couple cooks)'),
      );
}

class RecipeSteps extends StatefulWidget {
  RecipeSteps({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _RecipeStepsState createState() => _RecipeStepsState();
}

String formatDuration(Duration duration) =>
    duration.toString().split(".").first;

String formatDateTime(DateTime dateTime) =>
    dateTime.toString().split(".").first;

class Step {
  Step(this.recipe, this.index, this.prev, this.description, this.time);

  final Recipe recipe;
  final int index;
  final Step prev;
  Step next;
  final String description;
  final Duration time;
  Duration pausedTime = Duration(seconds: 0);

  Duration get runTime => started == null
      ? Duration(seconds: 0)
      : DateTime.now().difference(started) - pausedTime;

  Duration get remaining => time - runTime;

  DateTime started;

  void start() {
    started = DateTime.now();
    recipe.logToDB(index, "", Recipe.START_STATE);
  }

  void finish() {
    finished = DateTime.now();
    recipe.logToDB(index, "", Recipe.FINISH_STATE);
    if (next != null && next.started == null) next.start();
  }

  DateTime finished;

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

  static Future<void> loadDB() async {
    var dbsPath = await getApplicationDocumentsDirectory();
    var dbPath = join(dbsPath.path, 'history.db');
    db = await openDatabase(
      dbPath,
      onCreate: (db, version) => db.execute(
        "CREATE TABLE bakes(ts INTEGER PRIMARY KEY, start INTEGER, recipe TEXT, step INTEGER, note TEXT, state INTEGER)",
      ),
      version: 1,
    );
  }

  static const int START_STATE = 0;
  static const int FINISH_STATE = 1;

  DateTime startTime = DateTime.now();
  DateTime finishTime;
  String recipe = "sourdough";
  String text;

  Future<void> logToDB(int step, String note, int state) async {
    var now = DateTime.now();
    var entry = {
      'ts': now.millisecondsSinceEpoch,
      'start': startTime.millisecondsSinceEpoch,
      'recipe': recipe,
      'step': step,
      'note': note,
      'state': state,
    };
    db.insert('bakes', entry);
  }

  BuildContext buildContext;

  Recipe._();

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

  static Future<Recipe> startRecipe() async {
    Recipe recipe = await initRecipe();

    await recipe.logToDB(-1, "", START_STATE);
    return recipe;
  }

  void finishRecipe() async {
    await logToDB(-1, "", FINISH_STATE);
  }

  static Future<Recipe> initRecipe() async {
    String text = await rootBundle.loadString("assets/recipes/sourdough.md");
    Recipe recipe = Recipe._();
    recipe.text = "";
    recipe.steps = List<Step>();
    int index = 0;
    Step prev = null;
    for (var line in text.split("\n")) {
      print("processing ${line}");
      if (line.startsWith("=>")) {
        var parts = line.split(" ");
        int mins = int.parse(parts[1]);
        String desc = parts.sublist(2).join(" ");
        recipe.steps.add(Step(recipe, index, prev, desc, Duration(minutes: mins)));
        if (index > 0) recipe.steps[index-1].next = recipe.steps[index];
        index++;
      } else {
        recipe.text += line + '\n';
      }
    }
    return recipe;
  }

  static Future<Recipe> continueRecipe(DateTime startTime) async {
    var startTimeMillis = startTime.millisecondsSinceEpoch;
    List<Map> results = await db.query(
      'bakes',
      columns: ['start', 'step', 'ts', 'state'],
    );
    Recipe recipe = await initRecipe();
    recipe.startTime = startTime;
    print("continuing recipe from ${startTimeMillis}");
    print(results);
    for (var entry in results) {
      print("Processing $entry");
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
    List<PastRecipe> past = List<PastRecipe>();
    List<Map> results = await db.query(
      'bakes',
      columns: ['start', 'recipe', 'ts'],
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
      if (ts != start) {
        pastRecipe.finishedTime = DateTime.fromMillisecondsSinceEpoch(ts);
      }
    }

    var keys = map.keys.toList(growable: false);
    keys.sort((a, b) => a - b);
    for (var key in keys) {
      past.add(map[key]);
    }
    print("past: ${past}");
    return past;
  }
}

class _RecipeStepsState extends State<RecipeSteps> {
  Future<Recipe> setupFuture;
  Recipe recipe;

  @override
  void initState() {
    print("calling initState");
    super.initState();
    Future<Recipe> recipeFuture =
        Recipe.loadDB().then((value) => Recipe.pastRecipes().then((recipes) {
              if (recipes.length > 0 && recipes.last.finishedTime == null) {
                return Recipe.continueRecipe(recipes.last.startTime)
                    .then((recipe) {
                  return recipe;
                });
              } else {
                return Recipe.startRecipe();
              }
            }));
    setupFuture = recipeFuture
        .then((Recipe recipe) => this.recipe = recipe)
        .then((recipe) => this.recipe = recipe);
  }

  Duration tickTime = Duration(seconds: 1);

  bool checked = false;

  Container constructStepList() {
    ListTile makeListTile(Step step, bool active) => ListTile(
          contentPadding:
              EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
          leading: Checkbox(
            value: step.finished != null,
            onChanged: (value) {
              if (value && (step.prev == null || step.prev.finished != null)) {
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
              ? IconButton(
                  icon: Icon(Icons.alarm_add, size: 30.0),
                  onPressed: () {
                    scheduleAlarm(step.time.inSeconds, "${step.description}");
                  })
              : null,
        );
    Card makeCard(Step step, bool active) => Card(
        elevation: 8.0,
        margin: EdgeInsets.symmetric(horizontal: 10.0, vertical: 6.0),
        child: Container(
          decoration: BoxDecoration(/*color: Colors.amber*/),
          child: makeListTile(step, active),
        ));
    return Container(
        child: ListView.builder(
      scrollDirection: Axis.vertical,
      shrinkWrap: true,
      itemCount: recipe.steps.length + 2,
      itemBuilder: (BuildContext context, int index) {
        if (index == 0) {
          return Markdown(
            data: recipe.text,
            shrinkWrap: true,
          );
        }
        index--;
        if (index == recipe.steps.length) {
          return RaisedButton(
              onPressed: () => Recipe.startRecipe().then((r) {
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
  Widget build(BuildContext buildContext) {
    return Scaffold(
        body: FutureBuilder(
            future: setupFuture,
            builder: (BuildContext buildContext, AsyncSnapshot<void> snapshot) {
              if (recipe == null) {
                return Text("loading");
              }
              return constructStepList();
            }));
  }
}
