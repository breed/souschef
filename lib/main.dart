import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_ringtone_player/flutter_ringtone_player.dart';
import 'dart:async';

void main() => runApp(SousChefApp());

class SousChefApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'sourdough bread (a couple cooks)',
      theme: ThemeData(
        primarySwatch: Colors.deepPurple,
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      home: RecipeSteps(title: 'sourdough bread (a couple cooks)'),
    );
  }
}

class RecipeSteps extends StatefulWidget {
  RecipeSteps({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _RecipeStepsState createState() => _RecipeStepsState();
}

class Step {
  Step(var descAndTime) {
    description = descAndTime[0];
    time = descAndTime[1];
    remaining = time;
  }
  String description;
  int time;
  int remaining;
  TimeOfDay started;
  TimeOfDay finished;
  Timer timer;
}

class _RecipeStepsState extends State<RecipeSteps> {
  static var rawSteps = [];
  Future<void> setupFuture;

  @override
  void initState() {
    super.initState();
    setupFuture = rootBundle.loadString("assets/recipes/sourdough.md").then((String recipe) {
      for (var line in recipe.split("\n")) {
        print("processing ${line}");
        if (line.startsWith("=>")) {
          var parts = line.split(" ");
          int mins = int.parse(parts[1]);
          String desc = parts.sublist(2).join(" ");
          rawSteps.add([desc, mins]);
        }
      }
      steps = (rawSteps.map((s) => Step(s))).toList();
    });
  }

  var steps;

  Duration tickTime = Duration(seconds: 1);

  bool checked = false;
  Timer dialogTimer;
  int elapsedDialogTime;

  static String dialogTime(elapsedTime) {
    return
          "${(elapsedTime~/60).toString().padLeft(2, '0')}:"
          "${(elapsedTime%60).toString().padLeft(2, '0')}";
  }

  void _showDialog() {
    elapsedDialogTime = 0;
    FlutterRingtonePlayer.play(ios: IosSounds.alarm, android: AndroidSounds.alarm, looping: true);
    showDialog(
        context: context,
        builder: (BuildContext context) {
          return StatefulBuilder(builder: (context, setState) {
            String timeString = "- ${dialogTime(elapsedDialogTime)}";
            AlertDialog dialog = createAlertDialog(timeString, context);
            dialogTimer = dialogTimer != null ? dialogTimer : Timer.periodic(tickTime, (timer) {
              setState(() {
                elapsedDialogTime++;
                print(elapsedDialogTime);
                timeString = "- ${dialogTime(elapsedDialogTime)}";
              });
            });
            return dialog;
          });
        }
    );
  }

  AlertDialog createAlertDialog(String timeString, BuildContext context) {
    var dialog = AlertDialog(
      title: Text("completed"),
      content: Text(timeString),
      actions: [FlatButton(child: Text("dismiss"),
      onPressed: () {
        FlutterRingtonePlayer.stop();
        dialogTimer.cancel();
        dialogTimer = null;
        Navigator.of(context).pop();
      })],
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(10.0))),
      elevation: 2.0,
    );
    return dialog;
  }
  @override
  Widget build(BuildContext context) {
    ListTile makeListTile(Step step, bool active) => ListTile(
          contentPadding:
              EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
          leading: Checkbox(value: step.remaining <= 0.0),
          title: Text(step.description),
          subtitle: Row(children: <Widget>[
            Expanded(
                flex: 4,
                child: Container(
                    child: LinearProgressIndicator(
                  backgroundColor: Colors.grey,
                  value: step.remaining/step.time,
//                  valueColor: AlwaysStoppedAnimation(Colors.green),
                ))),
            Expanded(
                flex: 2,
                child: Padding(
                    padding: EdgeInsets.only(left: 10.0),
                    child: Text("${step.remaining} mins"))),
          ]),
          trailing: active ? (step.timer == null ? IconButton(icon: Icon(Icons.play_arrow, size: 30.0),
              onPressed: () =>
                  step.timer = Timer.periodic(tickTime, (timer) => setState(() {
                    step.remaining--;
                    checked = !checked;
                    if (step.remaining <= 0.0) {
                      timer.cancel();
                      step.timer = null;
                      _showDialog();
                    }
                    print(step.remaining);
                })))
          : IconButton(icon: Icon(Icons.pause, size: 30.0),
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
    return Scaffold(
//      backgroundColor: Colors.blueAccent,
      body: FutureBuilder(
        future: setupFuture,
        builder: (BuildContext context, AsyncSnapshot<void> snapshot) {
          if (steps == null) {
            print("nothing yet");
            return Text("nothing");
          }
          print("got steps ${steps}");
          return Container(
              child: ListView.builder(
                scrollDirection: Axis.vertical,
                shrinkWrap: true,
                itemCount: steps.length,
                itemBuilder: (BuildContext context, int index) =>
                    makeCard(steps[index],
                        (steps[index].remaining > 0.0) && (index == 0 || steps[index-1].remaining == 0.0)),
              ));
    })
    );
  }
}
