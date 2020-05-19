import 'package:flutter/material.dart';
import 'dart:async';

void main() => runApp(SousChefApp());

class SousChefApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'sourdough bread (a couple cooks)',
      theme: ThemeData(
        // This is the theme of your application.
        //
        // Try running your application with "flutter run". You'll see the
        // application has a blue toolbar. Then, without quitting the app, try
        // changing the primarySwatch below to Colors.green and then invoke
        // "hot reload" (press "r" in the console where you ran "flutter run",
        // or simply save your changes to "hot reload" in a Flutter IDE).
        // Notice that the counter didn't reset back to zero; the application
        // is not restarted.
        primarySwatch: Colors.deepPurple,
        // This makes the visual density adapt to the platform that you run
        // the app on. For desktop platforms, the controls will be smaller and
        // closer together (more dense) than on mobile platforms.
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
    time = descAndTime[1].toDouble();
    remaining = time;
  }
  String description;
  double time;
  double remaining;
  TimeOfDay started;
  TimeOfDay finished;
  Timer timer;
}
class _RecipeStepsState extends State<RecipeSteps> {
  var steps = [
    ['mix flour and water', 60],
    ['mix sourdough and salt', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
    ['fold', 30],
  ];
  Duration tickTime = Duration(seconds: 1);

  bool checked = false;

  @override
  Widget build(BuildContext context) {
    ListTile makeListTile(Step step) => ListTile(
          contentPadding:
              EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
          leading: Checkbox(value: step.remaining == 0.0,
          onChanged: (bool) {
            step.timer = Timer.periodic(tickTime, (timer) { step.remaining--;
            print(step.remaining);});
          },),
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
          //trailing: Icon(Icons.play_arrow, size: 30.0),
        );
    Card makeCard(Step step) => Card(
        elevation: 8.0,
        margin: EdgeInsets.symmetric(horizontal: 10.0, vertical: 6.0),
        child: Container(
          decoration: BoxDecoration(/*color: Colors.amber*/),
          child: makeListTile(step),
        ));
    final makeBody = Container(
        child: ListView.builder(
      scrollDirection: Axis.vertical,
      shrinkWrap: true,
      itemCount: steps.length,
      itemBuilder: (BuildContext context, int index) =>
          makeCard(Step(steps[index])),
    ));
    return Scaffold(
//      backgroundColor: Colors.blueAccent,
      body: makeBody,
    );
  }
}
