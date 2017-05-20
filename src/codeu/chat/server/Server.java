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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.Map;

import codeu.chat.common.Conversation;
import codeu.chat.common.ConversationSummary;
import codeu.chat.common.LinearUuidGenerator;
import codeu.chat.common.Message;
import codeu.chat.common.NetworkCode;
import codeu.chat.common.Relay;
import codeu.chat.common.User;
import codeu.chat.util.Logger;
import codeu.chat.util.Serializers;
import codeu.chat.util.Time;
import codeu.chat.util.Timeline;
import codeu.chat.util.Uuid;
import codeu.chat.util.connections.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public final class Server {

  private static final Logger.Log LOG = Logger.newLog(Server.class);

  private static final int RELAY_REFRESH_MS = 5000;  // 5 seconds

  private final Timeline timeline = new Timeline();

  private final Uuid id;
  private final byte[] secret;

  private final Model model = new Model();
  private final View view = new View(model);
  private final Controller controller;

  private final Relay relay;
  private Uuid lastSeen = Uuid.NULL;

  private Jedis db;
  private JedisPool pool = new JedisPool(new JedisPoolConfig(), "localhost");
  private final String CONVERSATION_HASH = "CONVERSATION_HASH";

  public Server(final Uuid id, final byte[] secret, final Relay relay) {

    this.id = id;
    this.secret = Arrays.copyOf(secret, secret.length);

    this.controller = new Controller(id, model);
    this.relay = relay;

    try {
      db = pool.getResource();
<<<<<<< HEAD
=======
      // db.flushAll();
>>>>>>> conversation-persistence
      loadUsers();
      reloadPastConversations();
    } catch (Exception e) {
      LOG.error(e, "Could not load Jedis database");
    }

    timeline.scheduleNow(new Runnable() {
      @Override
      public void run() {
        try {

          LOG.info("Reading update from relay...");

          for (final Relay.Bundle bundle : relay.read(id, secret, lastSeen, 32)) {
            onBundle(bundle);
            lastSeen = bundle.id();
          }

        } catch (Exception ex) {

          LOG.error(ex, "Failed to read update from relay.");

        }

        timeline.scheduleIn(RELAY_REFRESH_MS, this);
      }
    });
  }

  // add previously stored users to model
  private void loadUsers() {
    Set<String> idKeys = db.hkeys("nameHash");

    for (String key : idKeys) {
        String name = db.hget("nameHash", key);
        String timeStr = db.hget("timeHash", key);
        String password = db.hget("passwordHash", key);

        if (timeStr == null)
          LOG.info("Error: user id with no creation time");
        else if (password == null)
          LOG.info("Error: user id with no password");
        else {
<<<<<<< HEAD
          Uuid id = null;
          try {
            id = Uuid.parse(key);
          } catch (IOException ex) {
            LOG.info("Error in parsing id from database");
          }
          long timeInMs = Long.parseLong(timeStr);
          Time creationTime = new Time(timeInMs);

          User user = controller.newUser(id, name, creationTime, password);
          model.add(user);
=======
          try {
            Uuid id = Uuid.parse(key);
            long timeInMs = Long.parseLong(timeStr);
            Time creationTime = new Time(timeInMs);

            User user = controller.newUser(id, name, creationTime);
            model.add(user);
          } catch (Exception ex) {
            LOG.error(ex, "Failed to load user");
          }
>>>>>>> conversation-persistence
        }
    }
  }

  public void handleConnection(final Connection connection) {
    timeline.scheduleNow(new Runnable() {
      @Override
      public void run() {
        try {

          LOG.info("Handling connection...");

          final boolean success = onMessage(
              connection.in(),
              connection.out());

          LOG.info("Connection handled: %s", success ? "ACCEPTED" : "REJECTED");
        } catch (Exception ex) {

          LOG.error(ex, "Exception while handling connection.");

        }

        try {
          connection.close();
        } catch (Exception ex) {
          LOG.error(ex, "Exception while closing connection.");

        }
      }
    });
  }

  private void reloadPastConversations() {
    Set<String> idList = db.smembers(CONVERSATION_HASH);
    for (String convoId: idList) {
        String[] convoDetails = db.lindex(convoId, 0).split("\n");
        try {
          controller.newConversation(convoId, db.lrange(convoId, 0, db.llen(convoId)));
        } catch (Exception ex) {
          LOG.error(ex, "Could not load conversation " + convoId);
        }

    }
  }

  private boolean onMessage(InputStream in, OutputStream out) throws IOException {

    final int type = Serializers.INTEGER.read(in);

    if (type == NetworkCode.NEW_MESSAGE_REQUEST) {
      final Uuid author = Uuid.SERIALIZER.read(in);
      final Uuid conversation = Uuid.SERIALIZER.read(in);
      final String content = Serializers.STRING.read(in);

      final Message message = controller.newMessage(author, conversation, content);

      Serializers.INTEGER.write(out, NetworkCode.NEW_MESSAGE_RESPONSE);
      Serializers.nullable(Message.SERIALIZER).write(out, message);

      timeline.scheduleNow(createSendToRelayEvent(
          author,
          conversation,
          message.id));

      /*
        Adds to Jedis database. Key = conversation title, Value = List of all messages
        Each message has an author, message content, time (all separated on different lines)
      */
      // final String authorName = model.userById().at(author).iterator().next().name;
      final long timeInMs = message.creation.inMs();
      final String timeStr = Long.toString(timeInMs);
      final String id = conversation.toStrippedString();

      //Update db (Conversation id -> author, creation time, message id, message body)
      db.rpush(id, author.toStrippedString() + "\n" + timeStr + "\n" + message.id.toStrippedString() + "\n" + content + "\n");

    } else if (type == NetworkCode.NEW_USER_REQUEST) {

      final String name = Serializers.STRING.read(in);
      final String password = Serializers.STRING.read(in);

      if (db.hexists("nameHashRev", name)) {
        LOG.info(
          "addUser fail - username taken (user.name = %s)",
          name);
        return false;
      }

      final User user = controller.newUser(name, password);

      boolean addSuccess = addToDatabase(name, user, password);
      if (!addSuccess) return false;

      Serializers.INTEGER.write(out, NetworkCode.NEW_USER_RESPONSE);
      Serializers.nullable(User.SERIALIZER).write(out, user);

    } else if (type == NetworkCode.DELETE_USER_REQUEST) {
      final String name = Serializers.STRING.read(in);

      boolean deleteSuccess = deleteFromDatabase(name);
      if (!deleteSuccess) return false;

      final User user = controller.deleteUser(name);

      Serializers.INTEGER.write(out, NetworkCode.DELETE_USER_RESPONSE);
      Serializers.nullable(User.SERIALIZER).write(out, user);

    } else if (type == NetworkCode.CHANGE_USERNAME_REQUEST){
      final String oldName = Serializers.STRING.read(in);
      final String newName = Serializers.STRING.read(in);

      boolean changeSuccess = changeNameInDatabase(oldName, newName);
      if (!changeSuccess) return false;

      final User user = controller.changeUserName(oldName, newName);

      Serializers.INTEGER.write(out, NetworkCode.CHANGE_USERNAME_RESPONSE);
      Serializers.nullable(User.SERIALIZER).write(out, user);

    } else if (type == NetworkCode.NEW_CONVERSATION_REQUEST) {

      final String title = Serializers.STRING.read(in);
      final Uuid owner = Uuid.SERIALIZER.read(in);

      final Conversation conversation = controller.newConversation(title, owner);
      final long timeInMs = Time.now().inMs();
      final String timeStr = Long.toString(timeInMs);
      final String conversationId = conversation.id.toStrippedString() + "";

      //UPDATE DB
      db.sadd(CONVERSATION_HASH, conversationId);
      db.rpush(conversationId, owner.toStrippedString() + "\n" + timeStr + "\n" + title);

      Serializers.INTEGER.write(out, NetworkCode.NEW_CONVERSATION_RESPONSE);
      Serializers.nullable(Conversation.SERIALIZER).write(out, conversation);

    } else if (type == NetworkCode.GET_USERS_BY_ID_REQUEST) {

      final Collection<Uuid> ids = Serializers.collection(Uuid.SERIALIZER).read(in);

      final Collection<User> users = view.getUsers(ids);

      Serializers.INTEGER.write(out, NetworkCode.GET_USERS_BY_ID_RESPONSE);
      Serializers.collection(User.SERIALIZER).write(out, users);

    } else if (type == NetworkCode.GET_ALL_CONVERSATIONS_REQUEST) {

      final Collection<ConversationSummary> conversations = view.getAllConversations();

      Serializers.INTEGER.write(out, NetworkCode.GET_ALL_CONVERSATIONS_RESPONSE);
      Serializers.collection(ConversationSummary.SERIALIZER).write(out, conversations);

    } else if (type == NetworkCode.GET_CONVERSATIONS_BY_ID_REQUEST) {

      final Collection<Uuid> ids = Serializers.collection(Uuid.SERIALIZER).read(in);

      final Collection<Conversation> conversations = view.getConversations(ids);

      Serializers.INTEGER.write(out, NetworkCode.GET_CONVERSATIONS_BY_ID_RESPONSE);
      Serializers.collection(Conversation.SERIALIZER).write(out, conversations);

    } else if (type == NetworkCode.GET_MESSAGES_BY_ID_REQUEST) {

      final Collection<Uuid> ids = Serializers.collection(Uuid.SERIALIZER).read(in);

      final Collection<Message> messages = view.getMessages(ids);

      Serializers.INTEGER.write(out, NetworkCode.GET_MESSAGES_BY_ID_RESPONSE);
      Serializers.collection(Message.SERIALIZER).write(out, messages);

    } else if (type == NetworkCode.GET_USER_GENERATION_REQUEST) {

      Serializers.INTEGER.write(out, NetworkCode.GET_USER_GENERATION_RESPONSE);
      Uuid.SERIALIZER.write(out, view.getUserGeneration());

    } else if (type == NetworkCode.GET_USERS_EXCLUDING_REQUEST) {

      final Collection<Uuid> ids = Serializers.collection(Uuid.SERIALIZER).read(in);

      final Collection<User> users = view.getUsersExcluding(ids);

      Serializers.INTEGER.write(out, NetworkCode.GET_USERS_EXCLUDING_RESPONSE);
      Serializers.collection(User.SERIALIZER).write(out, users);

    } else if (type == NetworkCode.GET_CONVERSATIONS_BY_TIME_REQUEST) {

      final Time startTime = Time.SERIALIZER.read(in);
      final Time endTime = Time.SERIALIZER.read(in);

      final Collection<Conversation> conversations = view.getConversations(startTime, endTime);

      Serializers.INTEGER.write(out, NetworkCode.GET_CONVERSATIONS_BY_TIME_RESPONSE);
      Serializers.collection(Conversation.SERIALIZER).write(out, conversations);

    } else if (type == NetworkCode.GET_CONVERSATIONS_BY_TITLE_REQUEST) {

      final String filter = Serializers.STRING.read(in);

      final Collection<Conversation> conversations = view.getConversations(filter);

      Serializers.INTEGER.write(out, NetworkCode.GET_CONVERSATIONS_BY_TITLE_RESPONSE);
      Serializers.collection(Conversation.SERIALIZER).write(out, conversations);

    } else if (type == NetworkCode.GET_MESSAGES_BY_TIME_REQUEST) {

      final Uuid conversation = Uuid.SERIALIZER.read(in);
      final Time startTime = Time.SERIALIZER.read(in);
      final Time endTime = Time.SERIALIZER.read(in);

      final Collection<Message> messages = view.getMessages(conversation, startTime, endTime);

      Serializers.INTEGER.write(out, NetworkCode.GET_MESSAGES_BY_TIME_RESPONSE);
      Serializers.collection(Message.SERIALIZER).write(out, messages);

    } else if (type == NetworkCode.GET_MESSAGES_BY_RANGE_REQUEST) {
      final Uuid rootMessage = Uuid.SERIALIZER.read(in);

      final int range = Serializers.INTEGER.read(in);

      final Collection<Message> messages = view.getMessages(rootMessage, range);

      Serializers.INTEGER.write(out, NetworkCode.GET_MESSAGES_BY_RANGE_RESPONSE);
      // the type "NO_MESSAGE" so that the client still gets something.
      Serializers.collection(Message.SERIALIZER).write(out, messages);

    } else {

      // In the case that the message was not handled make a dummy message with

      Serializers.INTEGER.write(out, NetworkCode.NO_MESSAGE);

    }

    return true;
  }

  private boolean addToDatabase(String name, User user, String password) {
    final Time creationTime = user.creation;
    final Uuid id = user.id;
    final String idStr = id.toStrippedString();
    final long timeInMs = creationTime.inMs();
    final String timeStr = Long.toString(timeInMs);

    // hash table for storing ids, creation times
    db.hset("timeHash", idStr, timeStr);
    // hash table with ids for keys, usernames for values
    db.hset("nameHash", idStr, name);
    // hash table with usernames for keys, ids for values
    db.hset("nameHashRev", name, idStr);
    // hash table for storing ids, passwords
    db.hset("passwordHash", idStr, password);

    LOG.info(
         "newUser success (user.id=%s user.name=%s user.time=%s)",
         id,
         name,
         creationTime);
    return true;
  }

  private boolean deleteFromDatabase(String name) {
    if (!db.hexists("nameHashRev", name)) {
      LOG.info(
        "deleteUser fail - user not in database (user.id=NULL user.name=%s)",
        name);
      return false;
    }

    final String idStr = db.hget("nameHashRev", name);
<<<<<<< HEAD
    Uuid id = null;
    try {
      id = Uuid.parse(idStr);
    } catch (IOException ex) {
      LOG.info("Failure to parse id from database");
      return false;
    }
=======
    try {
      Uuid id = Uuid.parse(idStr);
>>>>>>> conversation-persistence

      if (!db.hget("nameHash", idStr).equals(name)) {
        LOG.info(
          "deleteUser fail - database mismatch error (user.id=%s user.name=%s)",
          id, name);
        return false;
      }

      String timeStr = db.hget("timeHash", idStr);
      long timeInMs = Long.parseLong(timeStr);
      Time creationTime = new Time(timeInMs);

      if (!db.hget("timeHash", idStr).equals(timeStr)) {
        LOG.info(
          "deleteUser fail - user not in database (user.id=%s user.name=%s user.time=%s)",
          id,
          name,
          creationTime);
          return false;
      }

<<<<<<< HEAD
    db.hdel("timeHash", idStr);
    db.hdel("nameHash", idStr);
    db.hdel("nameHashRev", name);
    db.hdel("passwordHash", idStr);

    return true;
  }

  private boolean changeNameInDatabase(String oldName, String newName) {
    if (db.hexists("nameHashRev", newName)) {
      LOG.info(
        "changeUserName fail - username taken (user.name = %s)",
        newName);
      return false;
    }

    if (!db.hexists("nameHashRev", oldName)) {
      LOG.info(
        "changeUserName fail - old user not in database (user.id=NULL user.name=%s)",
        oldName);
      return false;
    }

    final String idStr = db.hget("nameHashRev", oldName);
    try {
      Uuid id = Uuid.parse(idStr);
    } catch (IOException ex) {
      LOG.info("changeUserName fail - failure in parsing id from database");
      return false;
    }

    if (!db.hget("nameHash", idStr).equals(oldName)) {
      LOG.info(
        "changeUserName fail - database mismatch error (user.id=%s user.name=%s)",
        id, oldName);
      return false;
    }

    db.hdel("nameHashRev", oldName);
    db.hset("nameHashRev", newName, idStr);
    db.hset("nameHash", idStr, newName);
=======
      db.hdel("nameHash", idStr);
      db.hdel("timeHash", idStr);
      db.hdel("nameHashRev", name);

      return true;
    } catch (Exception ex) {
      LOG.error(ex, "Couldn't delete user");
      return false;
    }
>>>>>>> conversation-persistence

  }

  private void onBundle(Relay.Bundle bundle) {

    final Relay.Bundle.Component relayUser = bundle.user();
    final Relay.Bundle.Component relayConversation = bundle.conversation();
    final Relay.Bundle.Component relayMessage = bundle.user();

    User user = model.userById().first(relayUser.id());

    if (user == null) {
      user = controller.newUser(relayUser.id(), relayUser.text(), relayUser.time(), "");
    }

    Conversation conversation = model.conversationById().first(relayConversation.id());

    if (conversation == null) {

      // As the relay does not tell us who made the conversation - the first person who
      // has a message in the conversation will get ownership over this server's copy
      // of the conversation.
      conversation = controller.newConversation(relayConversation.id(),
                                                relayConversation.text(),
                                                user.id,
                                                relayConversation.time());
    }

    Message message = model.messageById().first(relayMessage.id());

    if (message == null) {
      message = controller.newMessage(relayMessage.id(),
                                      user.id,
                                      conversation.id,
                                      relayMessage.text(),
                                      relayMessage.time());
    }
  }

  private Runnable createSendToRelayEvent(final Uuid userId,
                                          final Uuid conversationId,
                                          final Uuid messageId) {
    return new Runnable() {
      @Override
      public void run() {
        final User user = view.findUser(userId);
        final Conversation conversation = view.findConversation(conversationId);
        final Message message = view.findMessage(messageId);
        relay.write(id,
                    secret,
                    relay.pack(user.id, user.name, user.creation),
                    relay.pack(conversation.id, conversation.title, conversation.creation),
                    relay.pack(message.id, message.content, message.creation));
      }
    };
  }

}
