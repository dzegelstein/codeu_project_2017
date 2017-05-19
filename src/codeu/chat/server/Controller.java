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

import java.util.Collection;
import java.util.List;

import codeu.chat.common.BasicController;
import codeu.chat.common.Conversation;
import codeu.chat.common.Message;
import codeu.chat.common.RandomUuidGenerator;
import codeu.chat.common.RawController;
import codeu.chat.common.User;
import codeu.chat.util.Logger;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;

public final class Controller implements RawController, BasicController {

  private final static Logger.Log LOG = Logger.newLog(Controller.class);

  private Model model;
  private final Uuid.Generator uuidGenerator;

  public Controller(Uuid serverId, Model model) {
    this.model = model;
    this.uuidGenerator = new RandomUuidGenerator(serverId, System.currentTimeMillis());
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

  public Conversation newConversation(String convoId, List<String> pastConversationInfo) throws Exception {
    try {
      String[] parsedConversation = pastConversationInfo.get(0).split("\n");
      String owner = parsedConversation[0];
      Uuid ownerId = Uuid.parse(owner);
      Long time = Long.parseLong(parsedConversation[1]);
      Time creationTime = Time.fromMs(time);
      String title = parsedConversation[2];
      Uuid id = Uuid.parse(convoId);
      return newConversation(id, title, ownerId, creationTime, pastConversationInfo);
    } catch (Exception ex) {
      LOG.error(ex, "Couldn't load messages in past conversation");
      throw new Exception("Couldn't load past conversation");
    }
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
     }

     return user;
  }

  @Override
  public User deleteUser(String name) {
    User user = model.getUserByName(name);
    user = deleteUser(user.id, user.name, user.creation);
    return user;
  }

  // delete user from model, database
  @Override
  public User deleteUser(Uuid id, String name, Time creationTime){
    // update model
    User user = new User(id, name, creationTime);
    boolean deleteSuccess = model.delete(user);
    if (!deleteSuccess) return null;
    return user;
  }

  @Override
  public User changeUserName(String oldName, String newName) {
    User user = model.getUserByName(oldName);
    if (user != null) user = changeUserName(user.id, user.name, newName, user.creation);
    return user;
  }

  @Override
  public User changeUserName(Uuid id, String oldName, String newName, Time creationTime) {
    // update model
    User oldUser = new User(id, oldName, creationTime);
    User newUser = new User(id, newName, creationTime);

    boolean deleteSuccess = model.delete(oldUser);
    if (!deleteSuccess) return null;
    model.add(newUser);

    return newUser;
  }

  private Conversation newConversationHelper(Uuid id, String title, Uuid owner, Time creationTime) {
    final User foundOwner = model.userById().first(owner);

    Conversation conversation = null;

    if (foundOwner != null && isIdFree(id)) {
      conversation = new Conversation(id, owner, creationTime, title);
      model.add(conversation);

      LOG.info("Conversation added: " + conversation.id);
    }

    return conversation;
  }

  @Override
  public Conversation newConversation(Uuid id, String title, Uuid owner, Time creationTime) {
    return newConversationHelper(id, title, owner, creationTime);
  }

  public Conversation newConversation(Uuid id, String title, Uuid owner, Time creationTime, List<String> oldMessages) {
    Conversation conversation = newConversationHelper(id, title, owner, creationTime);
    addMessagesToConversation(conversation, oldMessages);
    return conversation;
  }

  private void addMessagesToConversation(Conversation conversation, List<String> oldMessages) {
    //Starts at 1 because the first value in the array is info about conversation
    for (int i = 1; i < oldMessages.size(); i++) {

      String[] messageInfo = oldMessages.get(i).split("\n");
      String authorIdString = messageInfo[0];
      String messageSentTime = messageInfo[1];
      String messageId = messageInfo[2];
      String messageBody = messageInfo[3];

      Time creationTime = Time.fromMs(Long.parseLong(messageSentTime));
      Uuid id, authorId;

      try {
        id = Uuid.parse(messageId);
      } catch (Exception ex) {
        LOG.error(ex, "Couldn't load message id while loading past conversation");
        break;
      }

      try {
        authorId = Uuid.parse(authorIdString);
      } catch (Exception ex) {
        LOG.error(ex, "Couldn't load message author while loading past convesation");
        break;
      }

      newMessage(id, authorId, conversation.id, messageBody, creationTime);

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
