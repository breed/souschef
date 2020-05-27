import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_ringtone_player/flutter_ringtone_player.dart';
import 'package:path/path.dart';
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'alarm_manager.dart' show AlarmManager;
import 'dart:async';

void main() => runApp(SousChefApp());

class SousChefApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) =>
      MaterialApp(
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
    duration
        .toString()
        .split(".")
        .first;

String formatDateTime(DateTime dateTime) =>
    dateTime
        .toString()
        .split(".")
        .first;

class Step {
  Step(this.recipe, this.index, this.description, this.time);

  final Recipe recipe;
  final int index;
  final String description;
  final Duration time;
  Duration pausedTime = Duration(seconds: 0);

  Duration get runTime =>
      started == null
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
  }

  DateTime finished;

  String get runInterval =>
      "${formatDateTime(started)} until ${formatDateTime(finished)}";
  Timer timer;
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
      onCreate: (db, version) =>
          db.execute(
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
    for (var line in text.split("\n")) {
      print("processing ${line}");
      if (line.startsWith("=>")) {
        var parts = line.split(" ");
        int mins = int.parse(parts[1]);
        String desc = parts.sublist(2).join(" ");
        recipe.steps.add(Step(recipe, index++, desc, Duration(seconds: mins)));
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
        pastRecipe = PastRecipe(
            DateTime.fromMillisecondsSinceEpoch(start),
            recipeName);
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
  Timer timer;
  int activeStep;
  BuildContext buildContext;

  void _startTimer() {
    AlarmManager.oneShot(recipe.steps[activeStep].remaining, 17, (id) {
        recipe.steps[activeStep].finish();
        activeStep++;
        _showDialog(recipe.steps[activeStep], buildContext);
        _stopTimer();
    });
    timer = Timer.periodic(
        tickTime,
            (timer) =>
            setState(() {
              checked = !checked;
              print(recipe.steps[activeStep].remaining.inSeconds);
              if (recipe.steps[activeStep].remaining.inSeconds <= 0) {
                timer.cancel();
                this.timer = null;
              }
            }));
  }

  void _stopTimer() {
    if (timer != null) {
      setState(() {
        timer.cancel();
        timer = null;
      });
    }
  }

  @override
  void initState() {
    print("calling initState");
    super.initState();
    Future<bool> alarmFuture = AlarmManager.init(alarmClock: true,
        allowWhileIdle: true,
        exact: true,
        wakeup: true,
        rescheduleOnReboot: true);
    Future<Recipe> recipeFuture =
    Recipe.loadDB().then((value) =>
        Recipe.pastRecipes().then((recipes) {
          if (recipes.length > 0 && recipes.last.finishedTime == null) {
            return Recipe.continueRecipe(recipes.last.startTime)
                .then((recipe) {
              for (activeStep = 0;
              activeStep < recipe.steps.length;
              activeStep++) {
                if (recipe.steps[activeStep].finished == null) break;
              }
              if (activeStep < recipe.steps.length &&
                  recipe.steps[activeStep].started != null) {
                _startTimer();
              }
              return recipe;
            });
          } else {
            return Recipe.startRecipe();
          }
        }));
    setupFuture = alarmFuture.then((v) =>
        recipeFuture.then((Recipe recipe) => this.recipe = recipe).then(
                (recipe) => this.recipe = recipe));
  }

  Duration tickTime = Duration(seconds: 1);

  bool checked = false;
  Timer dialogTimer;
  int elapsedDialogTime;

  static String dialogTime(elapsedTime) =>
      "${(elapsedTime ~/ 60).toString().padLeft(2, '0')}:"
          "${(elapsedTime % 60).toString().padLeft(2, '0')}";

  void _showDialog(Step step, BuildContext buildContext) {
    elapsedDialogTime = 0;
    FlutterRingtonePlayer.play(
        ios: IosSounds.alarm, android: AndroidSounds.alarm, looping: true);
    showDialog(
        context: buildContext,
        builder: (BuildContext bcontext) {
          return StatefulBuilder(builder: (bcontext, setState) {
            String timeString = "- ${dialogTime(elapsedDialogTime)}";
            AlertDialog dialog = createAlertDialog(timeString, bcontext, step);
            dialogTimer = dialogTimer != null
                ? dialogTimer
                : Timer.periodic(tickTime, (timer) {
              setState(() {
                elapsedDialogTime++;
                timeString = "- ${dialogTime(elapsedDialogTime)}";
              });
            });
            return dialog;
          });
        });
  }

  AlertDialog createAlertDialog(String timeString, BuildContext context,
      Step step) =>
      AlertDialog(
        title: Text("completed"),
        content: Text(timeString),
        actions: [
          FlatButton(
              child: Text("dismiss"),
              onPressed: () {
                FlutterRingtonePlayer.stop();
                dialogTimer.cancel();
                dialogTimer = null;
                checked = !checked;
                Navigator.of(context).pop();
              })
        ],
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.all(Radius.circular(10.0))),
        elevation: 2.0,
      );

  Container constructStepList(BuildContext buildContext) {
    ListTile makeListTile(Step step, bool active) =>
        ListTile(
          contentPadding:
          EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
          leading: Checkbox(
            value: step.finished != null,
            onChanged: (value) => print(value),
          ),
          title: Text(step.description),
          subtitle: Row(children: <Widget>[
            Expanded(
                flex: 4,
                child: Container(
                    child: step.remaining.inSeconds > 0
                        ? LinearProgressIndicator(
                      backgroundColor: Colors.grey,
                      value:
                      step.remaining.inSeconds / step.time.inSeconds,
                    )
                        : Text("${step.runInterval}"))),
            Expanded(
                flex: 2,
                child: Padding(
                    padding: EdgeInsets.only(left: 10.0),
                    child: Text((step.remaining.inSeconds) < 0
                        ? "${formatDuration(step.time)}"
                        : "${formatDuration(step.remaining)}"))),
          ]),
          trailing: step.index == activeStep && timer == null ?
          IconButton(
              icon: Icon(Icons.play_arrow, size: 30.0),
              onPressed: () {
                print(step.started);
                if (step.started == null) {
                  step.start();
                }
                _startTimer();
              })
              : null,
        );
    Card makeCard(Step step, bool active) =>
        Card(
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
              return Markdown(data: recipe.text, shrinkWrap: true,);
            }
            index--;
            if (index == recipe.steps.length) {
              return RaisedButton(onPressed: () =>
                  Recipe.startRecipe().then((r) {
                    this.activeStep = 0;
                    _stopTimer();
                    setState(() => this.recipe = r);
                  }), child: Text("start new bake"));
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
    this.buildContext = buildContext;
    return Scaffold(
        body: FutureBuilder(
            future: setupFuture,
            builder: (BuildContext context, AsyncSnapshot<void> snapshot) {
              if (recipe == null) {
                return Text("loading");
              }
              return constructStepList(buildContext);
            }));
  }
}
