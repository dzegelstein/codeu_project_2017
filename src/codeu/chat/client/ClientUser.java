// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package codeu.chat.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import codeu.chat.common.User;
import codeu.chat.util.Logger;
import codeu.chat.util.Uuid;
import codeu.chat.util.store.Store;

public final class ClientUser {

  private final static Logger.Log LOG = Logger.newLog(ClientUser.class);

  private static final Collection<Uuid> EMPTY = Arrays.asList(new Uuid[0]);
  private final Controller controller;
  private final View view;

  private User current = null;

  private final Map<Uuid, User> usersById = new HashMap<>();

  // This is the set of users known to the server, sorted by name.
  private Store<String, User> usersByName = new Store<>(String.CASE_INSENSITIVE_ORDER);

  public ClientUser(Controller controller, View view) {
    this.controller = controller;
    this.view = view;
  }

  // Validate the username string
  static public boolean isValidName(String userName) {
    boolean clean = true;
    if (userName.length() == 0) {
      clean = false;
    } else {

      // TODO: check for invalid characters

    }
    return clean;
  }

  public boolean hasCurrent() {
    return (current != null);
  }

  public User getCurrent() {
    return current;
  }

  public boolean signInUser(String name, String password) {
    updateUsers();

    final User prev = current;
    if (name != null) {
      final User newCurrent = usersByName.first(name);
      if (newCurrent != null && validatePassword(newCurrent, password)) {
        current = newCurrent;
      }
    }
    return (prev != current);
  }

  public boolean signOutUser() {
    boolean hadCurrent = hasCurrent();
    current = null;
    return hadCurrent;
  }

  public void showCurrent() {
    printUser(current);
  }

  public void addUser(String name, String password) {
    final boolean validInputs = isValidName(name);

    final User user = (validInputs) ? controller.newUser(name, password) : null;

    if (user == null) {
      System.out.format("Error: user not created - %s.\n",
          (validInputs) ? "server failure" : "bad input value");
    } else {
      LOG.info("New user complete, Name= \"%s\" UUID=%s", user.name, user.id);
      updateUsers();
    }
  }

  public void deleteUser(String name, String password) {
    User user = usersByName.first(name);
    final boolean validUser = (user != null);
    final boolean validPassword = validatePassword(user, password);

    user = null;

    if (validUser && validPassword) user = controller.deleteUser(name);

    if (user == null) {
      String errorMessage = "server failure";
      if (!validUser) errorMessage = "user does not exist";
      else if (!validPassword) errorMessage = "incorrect password";
      System.out.format("Error: user not deleted - %s.\n", errorMessage);
    } else {
      if (hasCurrent() && user.id.equals(current.id)) {
        System.out.println("You are currently signed in as this user. " +
                            "You will now be signed out.");
        if (!signOutUser()) {
          System.out.println("Error: sign out failed (not signed in?)");
        }
      }
      LOG.info("User deleted, Name= \"%s\" UUID=%s", user.name, user.id);
      updateUsers();
    }
  }

  public void changeUserName(String oldName, String newName, String password) {
    final boolean validInputs = isValidName(newName);
    User user = usersByName.first(oldName);
    final boolean validOldUser = (user != null);
    final boolean validPassword = validatePassword(user, password);

    user = null;

    if (validInputs && validPassword) user = controller.changeUserName(oldName, newName);

    if (user == null) {
      String errorMessage = "server failure";
      if (!validInputs) errorMessage = "bad input value";
      else if (!validOldUser) errorMessage = "user does not exist";
      else if (!validPassword) errorMessage = "incorrect password";
      System.out.format("Error: user not deleted - %s.\n", errorMessage);
    } else {
      if (hasCurrent() && user.id.equals(current.id)) {
        System.out.println("You are currently signed in as this user." +
                            "You will now be signed out.");
        if (!signOutUser()) {
          System.out.println("Error: sign out failed (not signed in?)");
        }
      }
      LOG.info("Username changed, New name= \"%s\" Old name = \"%s\" UUID=%s",
                user.name, oldName, user.id);
      updateUsers();
    }
  }

  private boolean validatePassword(User user, String password) {
    return user != null && password.equals(user.password);
  }

  public void showAllUsers() {
    updateUsers();
    for (final User u : usersByName.all()) {
      printUser(u);
    }
  }

  public User lookup(Uuid id) {
    return (usersById.containsKey(id)) ? usersById.get(id) : null;
  }

  public String getName(Uuid id) {
    final User user = lookup(id);
    if (user == null) {
      LOG.warning("userContext.lookup() failed on ID: %s", id);
      return null;
    } else {
      return user.name;
    }
  }

  public Iterable<User> getUsers() {
    return usersByName.all();
  }

  public void updateUsers() {
    usersById.clear();
    usersByName = new Store<>(String.CASE_INSENSITIVE_ORDER);

    for (final User user : view.getUsersExcluding(EMPTY)) {
      usersById.put(user.id, user);
      usersByName.insert(user.name, user);
    }
  }

  public static String getUserInfoString(User user) {
    return (user == null) ? "Null user" :
        String.format(" User: %s\n   Id: %s\n   created: %s\n", user.name, user.id, user.creation);
  }

  public String showUserInfo(String uname) {
    return getUserInfoString(usersByName.first(uname));
  }

  // Move to User's toString()
  public static void printUser(User user) {
    System.out.println(getUserInfoString(user));
  }
}
