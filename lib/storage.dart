import 'package:shared_preferences/shared_preferences.dart';
import 'package:souschef/recipe.dart';

/**
 * encapsulates the functions for storing both preferences and ongoing
 * bakes. we are only going to track a single recipe. we are going to store
 * the recipe state in preferences. (it's an abuse of the service...)
 */

SharedPreferences prefs;

Future<void> setupStorage() async {
  prefs = await SharedPreferences.getInstance();
}

/* keys for prefs */
final AUTOSTART_TIMERS = "autostart_timers";
final RELATIVE_TIME = "relative_time";
final CURRENT_RECIPE = "current_recipe";
final CURRENT_STEPS = "current_steps";

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

Future<Recipe> getCurrentRecipe(bool autostartTimer, bool relativeTime) async {
  var name = await prefs.getString(CURRENT_RECIPE);
  print("CURRENT NAME is ${name}");
  if (name == null) return null;

  Recipe recipe = await Recipe.initRecipe(name, autostartTimer, relativeTime);
  var stepTimeStamps = await prefs.getStringList(CURRENT_STEPS);
  for (int i = 0; i < stepTimeStamps.length; i++) {
    var ts = stepTimeStamps[i].split(",");
    recipe.steps[i].started = DateTime.parse(ts[0]);
    if (i == 0) recipe.startTime = recipe.steps[0].started;
    if (ts.length > 1) {
      recipe.steps[i].finished = DateTime.parse(ts[1]);
    }
  }
  return recipe;
}

void storeCurrentRecipe(Recipe recipe) async {
  print("saveing CURRENT RECIPE ${recipe.recipeName}");
  await prefs.setString(CURRENT_RECIPE, recipe.recipeName);
  List<String> stepTimeStamps = List<String>();
  for (var step in recipe.steps) {
    if (step.started == null) break;
    var stepTimeStamp = step.started.toIso8601String();
    if (step.finished != null) {
      stepTimeStamp += "," + step.finished.toIso8601String();
    }
    stepTimeStamps.add(stepTimeStamp);
  }
  await prefs.setStringList(CURRENT_STEPS, stepTimeStamps);
}
