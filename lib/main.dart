import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_ringtone_player/flutter_ringtone_player.dart';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'dart:async';

void main() => runApp(SousChefApp());

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
  Step(this.index, this.description, this.time);

  final int index;
  final String description;
  final Duration time;
  Duration pausedTime = Duration(seconds: 0);

  Duration get runTime => started == null
      ? Duration(seconds: 0)
      : DateTime.now().difference(started) - pausedTime;
  Duration get remaining => time - runTime;

  DateTime started;
  DateTime finished;
  String get runInterval => "${formatDateTime(started)} - ${formatDateTime(finished)}";
  Timer timer;
}

class PastRecipe {
  DateTime startTime;
  String recipeName;
}

class Recipe {
  List<Step> steps;

  static Recipe startRecipe() {}
  static Recipe continueRecipe(DateTime startTime) {}
  static List<PastRecipe> pastRecipes() {}
}

class _RecipeStepsState extends State<RecipeSteps> {
  static var rawSteps = [];
  Future<void> setupFuture;

  @override
  void initState() {
    super.initState();
    Future<void> recipeFuture = loadRecipe("sourdough");
    Future<void> dbFuture = loadDB();
    setupFuture = recipeFuture;
  }

  Future<void> loadDB() async => openDatabase(
        join(await getDatabasesPath(), 'history.db'),
        onCreate: (db, version) => db.execute(
          "CREATE TABLE bakes(ts DATETIME PRIMARY KEY, start DATETIME, recipe TEXT, step INTEGER, note TEXT, state INTEGER)",
        ),
        version: 1,
      );

  Future<void> loadRecipe(String recipe) async => rootBundle
          .loadString("assets/recipes/${recipe}.md")
          .then((String recipe) {
        steps = [];
        int index = 0;
        for (var line in recipe.split("\n")) {
          print("processing ${line}");
          if (line.startsWith("=>")) {
            var parts = line.split(" ");
            int mins = int.parse(parts[1]);
            String desc = parts.sublist(2).join(" ");
            steps.add(Step(index++, desc, Duration(seconds: mins)));
          }
        }
      });

  var steps;

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
                      print(elapsedDialogTime);
                      timeString = "- ${dialogTime(elapsedDialogTime)}";
                    });
                  });
            return dialog;
          });
        });
  }

  AlertDialog createAlertDialog(
          String timeString, BuildContext context, Step step) =>
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

  @override
  Widget build(BuildContext buildContext) => Scaffold(
      body: FutureBuilder(
          future: setupFuture,
          builder: (BuildContext context, AsyncSnapshot<void> snapshot) {
            if (steps == null) {
              return Text("loading");
            }
            return constructStepList(buildContext);
          }));

  Container constructStepList(BuildContext buildContext) {
    ListTile makeListTile(Step step, bool active) => ListTile(
          contentPadding:
              EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
          leading: Checkbox(value: step.finished != null),
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
                    child: Text("${formatDuration(step.remaining)}"))),
          ]),
          trailing: active
              ? (step.timer == null
                  ? IconButton(
                      icon: Icon(Icons.play_arrow, size: 30.0),
                      onPressed: () {
                        if (step.started == null) {
                          step.started = DateTime.now();
                        }
                        step.timer = Timer.periodic(
                            tickTime,
                            (timer) => setState(() {
                                  checked = !checked;
                                  if (step.remaining.inSeconds <= 0) {
                                    timer.cancel();
                                    step.timer = null;
                                    _showDialog(step, buildContext);
                                    step.finished = DateTime.now();
                                  }
                                  print(step.remaining);
                                }));
                      })
                  : IconButton(
                      icon: Icon(Icons.pause, size: 30.0),
                      onPressed: () => setState(() {
                            step.timer.cancel();
                            step.timer = null;
                          })))
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
      itemCount: steps.length,
      itemBuilder: (BuildContext context, int index) => makeCard(
          steps[index],
          (steps[index].finished == null) &&
              (index == 0 || steps[index - 1].finished != null)),
    ));
  }
}
