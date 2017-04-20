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

package codeu.chat.server;

import java.util.*;

import codeu.chat.common.BasicController;
import codeu.chat.common.Conversation;
import codeu.chat.common.Message;
import codeu.chat.common.RawController;
import codeu.chat.common.User;
import codeu.chat.util.Logger;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public final class Controller implements RawController, BasicController {

  private final static Logger.Log LOG = Logger.newLog(Controller.class);

  private Model model;
  private final Uuid.Generator uuidGenerator;

  private Jedis db;
  private JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");

  public Controller(Uuid serverId, Model model) {
    this.model = model;
    this.uuidGenerator = new RandomUuidGenerator(serverId, System.currentTimeMillis());

    // create persistent instance of Jedis
    try {
      db = pool.getResource();
      loadUsers();
    }
    catch (Exception ex) {
        LOG.error(ex, "Failed to load Jedis database");
    }
  }

  @Override
  public Message newMessage(Uuid author, Uuid conversation, String body) {
    return newMessage(createId(), author, conversation, body, Time.now());
  }

  @Override
  public User newUser(String name) {
    return newUser(createId(), name, Time.now());
  }

  @Override
  public Conversation newConversation(String title, Uuid owner) {
    return newConversation(createId(), title, owner, Time.now());
  }

  @Override
  public Message newMessage(Uuid id, Uuid author, Uuid conversation, String body, Time creationTime) {

    final User foundUser = model.userById().first(author);
    final Conversation foundConversation = model.conversationById().first(conversation);

    Message message = null;

    if (foundUser != null && foundConversation != null && isIdFree(id)) {

      message = new Message(id, Uuid.NULL, Uuid.NULL, creationTime, author, body);
      model.add(message);
      LOG.info("Message added: %s", message.id);

      // Find and update the previous "last" message so that it's "next" value
      // will point to the new message.

      if (Uuid.equals(foundConversation.lastMessage, Uuid.NULL)) {

        // The conversation has no messages in it, that's why the last message is NULL (the first
        // message should be NULL too. Since there is no last message, then it is not possible
        // to update the last message's "next" value.

      } else {
        final Message lastMessage = model.messageById().first(foundConversation.lastMessage);
        lastMessage.next = message.id;
      }

      // If the first message points to NULL it means that the conversation was empty and that
      // the first message should be set to the new message. Otherwise the message should
      // not change.

      foundConversation.firstMessage =
          Uuid.equals(foundConversation.firstMessage, Uuid.NULL) ?
          message.id :
          foundConversation.firstMessage;

      // Update the conversation to point to the new last message as it has changed.

      foundConversation.lastMessage = message.id;

      if (!foundConversation.users.contains(foundUser)) {
        foundConversation.users.add(foundUser.id);
      }
    }

    return message;
  }

  public User newUser(Uuid id, String name, Time creationTime) {

     User user = null;

     if (isIdInUse(id)) {
       LOG.info(
           "newUser fail - id in use (user.id=%s user.name=%s user.time=%s)",
           id,
           name,
           creationTime);
     }
     else {

       user = new User(id, name, creationTime);
       model.add(user);

      /* ------------------------------------- */
      /* add user to database                  */
      /* ------------------------------------- */
      final String idStr = id.toStrippedString();
      final long timeInMs = creationTime.inMs();
      final String timeStr = Long.toString(timeInMs);

      LOG.info("ADDING A NEW USER");

      // hash table for storing ids, usernames
      db.hset("nameHash", idStr, name);
      // hash table for storing ids, creation times
      db.hset("timeHash", idStr, timeStr);

      Map<String, String> resName = db.hgetAll("nameHash");
      for (String value : resName.values()) {
           LOG.info(value);
      }

      Map<String, String> resTime = db.hgetAll("timeHash");
      for (String value : resTime.values()) {
           LOG.info(value);
      }

      LOG.info(
           "newUser success (user.id=%s user.name=%s user.time=%s)",
           id,
           name,
           creationTime);
     }

     return user;
  }

  @Override
  public void deleteUser(String idStr) {
    Uuid id = Uuid.fromString(idStr);

    if (db.hget("nameHash", idStr).equals(null)) {
      LOG.info(
        "deleteUser fail - user not in database (user.id=%s user.name=NULL)",
        id);
    }
    else if (db.hget("timeHash", idStr).equals(null)) {
      LOG.info(
        "deleteUser fail - user not in database (user.id=%s user.time=NULL)",
        id);
    }
    else {
      String name = db.hget("nameHash", idStr);
      long timeInMs = Long.parseLong(db.hget("timeHash", idStr));
      Time creationTime = new Time(timeInMs);

      User user = new User(id, name, creationTime);

      deleteUser(user);
    }
  }

  // delete user from model, database
  @Override
  public void deleteUser(User user) {
    final String idStr = user.id.toStrippedString();
    final String name = user.name;
    final long timeInMs = user.creation.inMs();
    final String timeStr = Long.toString(timeInMs);

    LOG.info("DELETING USER");

    // remove from database\
    if (!db.hget("nameHash", idStr).equals(name)) {
      LOG.info(
        "deleteUser fail - user not in database (user.id=%s user.name=%s user.time=%s)",
        user.id,
        user.name,
        user.creation);
    }
    else if (!db.hget("timeHash", idStr).equals(timeStr)) {
      LOG.info(
        "deleteUser fail - user not in database (user.id=%s user.name=%s user.time=%s)",
        user.id,
        user.name,
        user.creation);
    }
    else {
        db.hdel("nameHash", idStr);
        db.hdel("timeHash", idStr);
    }

    // update model
    model = new Model();
    loadUsers();
  }

  @Override
  public Conversation newConversation(Uuid id, String title, Uuid owner, Time creationTime) {

    final User foundOwner = model.userById().first(owner);

    Conversation conversation = null;

    if (foundOwner != null && isIdFree(id)) {
      conversation = new Conversation(id, owner, creationTime, title);
      model.add(conversation);

      LOG.info("Conversation added: " + conversation.id);
    }

    return conversation;
  }

  // add previously stored users to model
  private void loadUsers() {
    Set<String> idKeys = db.hkeys("nameHash");

    for (String key : idKeys) {
        String name = db.hget("nameHash", key);
        String timeStr = db.hget("timeHash", key);

        if (timeStr == null)
          LOG.info("Error: user id with no creation time");
        else {

          Uuid id = Uuid.fromString(key);
          long timeInMs = Long.parseLong(timeStr);
          Time creationTime = new Time(timeInMs);

          User user = new User(id, name, creationTime);
          model.add(user);
        }
    }
  }

  private Uuid createId() {

    Uuid candidate;

    for (candidate = uuidGenerator.make();
         isIdInUse(candidate);
         candidate = uuidGenerator.make()) {

     // Assuming that "randomUuid" is actually well implemented, this
     // loop should never be needed, but just incase make sure that the
     // Uuid is not actually in use before returning it.

    }

    return candidate;
  }

  private boolean isIdInUse(Uuid id) {
    return model.messageById().first(id) != null ||
           model.conversationById().first(id) != null ||
           model.userById().first(id) != null;
  }

  private boolean isIdFree(Uuid id) { return !isIdInUse(id); }

}
