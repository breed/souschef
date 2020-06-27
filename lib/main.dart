import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:souschef/recipe_list.dart';

import 'recipe.dart';

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
 *    if the time has an * in front of it, an alarm will automatically be set:
 *    => *17 bake for at 515 degrees
 *
 * a "bakes" sqllite DB holds the state of instances of the recipe. each
 * instance is uniquely identified by the time the recipe was started. the
 * current schema is:
 *    ts INTEGER PRIMARY KEY - this is time in milliseconds it simply
 *                             corresponds to when an event happened
 *    start INTEGER - when the bake started (the recipe began). it uniquely
 *                    identifies an instance of the recipe
 *    recipe TEXT - the name of the recipe. it should correspond to the recipe
 *                  markdown name
 *    step INTEGER - the step that this entry corresponds to. -1 means the
 *                   recipe itself (either the start or the end)
 *    state INTEGER - shoud be either START_STATE or FINISH_STATE
 */

/**
 * quick and dirty alarm scheduler. i'm not sure how to do this for iOS...
 */

SharedPreferences prefs;
/* keys for prefs */
final AUTOSTART_TIMERS = "autostart_timers";
final RELATIVE_TIME = "relative_time";

bool get autostartTimers {
  bool v = prefs.get(AUTOSTART_TIMERS);
  return v == null ? false : v;
}

void setAutostartTimers(bool value) => prefs.setBool(AUTOSTART_TIMERS, value);

bool get relativeTime {
  bool v = prefs.get(RELATIVE_TIME);
  return v == null ? false : v;
}

void setRelativeTime(bool value) => prefs.setBool(RELATIVE_TIME, value);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Recipe.loadList();
  prefs = await SharedPreferences.getInstance();
  runApp(SousChefApp());
}

class SousChefApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'sous chef',
        theme: ThemeData(
          primarySwatch: Colors.deepPurple,
          visualDensity: VisualDensity.adaptivePlatformDensity,
        ),
        // hardcoded for now, but hopefully there will be more in the future
        home: RecipeSteps(title: 'sous chef'),
      );
}

class RecipeSteps extends StatefulWidget {
  RecipeSteps({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _RecipeStepsState createState() => _RecipeStepsState();
}

Future<String> _showSelectionDialog(BuildContext context) async {
  return showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext buildContext) =>
          AlertDialog(
              title: Text('select recipe'),
              content: makeRecipeListContainer()));
}

Future<void> _showSettingsDialog(BuildContext context) async {
  return showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (BuildContext buildContext) =>
          AlertDialog(
            title: Text('settings'),
            content: ListView(children: <Widget>[
              CheckboxListTile(
                title: Text("autostart timers"),
                subtitle: Text(
                    "auto start the timer for a step when the previous step is checked complete."),
                value: autostartTimers,
                onChanged: (value) => setAutostartTimers(value),
              ),
              CheckboxListTile(
                title: Text("relative times"),
                subtitle:
                Text("each step starts when previous step actually ends."),
                value: relativeTime,
                onChanged: (value) => setRelativeTime(value),
              ),
              Text("Version 1.0"),
            ]),
          ));
}

class _RecipeStepsState extends State<RecipeSteps> {
  /* all the async stuff is tracked in this Future. it returns a recipe, but
   * it is supposed to set the recipe member before it finishes. */
  Future<Recipe> setupFuture;
  Recipe recipe;

  _RecipeStepsState();

  @override
  void initState() {
    super.initState();
    Future<Recipe> recipeFuture =
    Recipe.loadDB().then((value) =>
        Recipe.pastRecipes().then((recipes) {
          if (recipes.length > 0 && recipes.last.finishedTime == null) {
            return Recipe.continueRecipe(
                recipes.last.startTime, autostartTimers, relativeTime)
                .then((recipe) {
              return recipe == null ? Recipe.emptyRecipe : recipe;
            });
          } else {
            return Recipe.emptyRecipe;
          }
        }));
    setupFuture = recipeFuture
        .then((Recipe recipe) => this.recipe = recipe)
        .then((recipe) => this.recipe = recipe);
  }

  Container constructStepList() {
    /* make a step tile */
    ListTile makeListTile(RecipeStep step, bool active) => ListTile(
      contentPadding:
      EdgeInsets.symmetric(horizontal: 20.0, vertical: 10.0),
      leading: step.index == 0 && step.started == null
          ? IconButton(
          icon: Icon(Icons.play_arrow, size: 30.0),
          onPressed: () {
            recipe.startRecipe();
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
      title: Text("${step.description} ${step.durationString}"),
      subtitle: Text("${step.runInterval}"),
          trailing: step.time.inSeconds > 0 &&
                  step.finished == null &&
                  (step.index == 0 ||
                      recipe.steps[step.index - 1].finished != null)
              ? (step.timerSet
              ? Icon(Icons.hourglass_full)
              : IconButton(
              icon: Icon(Icons.alarm_add, size: 30.0),
              onPressed: () {
                scheduleAlarm(
                    step.time.inSeconds, "${step.description}");
                step.timerSet = true;
                setState(() {});
              }))
              : null,
    );
    Card makeCard(RecipeStep step, bool active) => Card(
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
                  onPressed: () =>
                      _showSelectionDialog(context).then(
                              (recipeName) =>
                              Recipe.setupRecipe(
                                  recipeName, autostartTimers, relativeTime)
                                  .then((r) =>
                                  setState(() {
                                    this.recipe = r;
                                    print("starting recipe $r");
                                  }))),
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
      appBar: AppBar(
        title: Text("sous chef"),
        leading: FlatButton(
          child: Icon(Icons.settings),
          onPressed: () => _showSettingsDialog(buildContext),
        ),
      ),
      body: FutureBuilder(
          future: setupFuture,
          builder: (BuildContext buildContext, AsyncSnapshot<void> snapshot) {
            if (recipe == null) {
              return Text("loading...");
            }
            return constructStepList();
          }));
}
