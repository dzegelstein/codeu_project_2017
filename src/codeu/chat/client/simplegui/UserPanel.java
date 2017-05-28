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

package codeu.chat.client.simplegui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import codeu.chat.client.ClientContext;
import codeu.chat.common.User;

// NOTE: JPanel is serializable, but there is no need to serialize UserPanel
// without the @SuppressWarnings, the compiler will complain of no override for serialVersionUID
@SuppressWarnings("serial")
public final class UserPanel extends JPanel {

  private final ClientContext clientContext;

  public UserPanel(ClientContext clientContext) {
    super(new GridBagLayout());
    this.clientContext = clientContext;
    initialize();
  }

  private void initialize() {

    // This panel contains from top to bottom; a title bar, a list of users,
    // information about the current (selected) user, and a button bar.

    // Title bar - also includes name of currently signed-in user.
    final JPanel titlePanel = new JPanel(new GridBagLayout());
    final GridBagConstraints titlePanelC = new GridBagConstraints();

    final JLabel titleLabel = new JLabel("Users", JLabel.LEFT);
    final GridBagConstraints titleLabelC = new GridBagConstraints();
    titleLabelC.gridx = 0;
    titleLabelC.gridy = 0;
    titleLabelC.anchor = GridBagConstraints.PAGE_START;

    final GridBagConstraints titleGapC = new GridBagConstraints();
    titleGapC.gridx = 1;
    titleGapC.gridy = 0;
    titleGapC.fill = GridBagConstraints.HORIZONTAL;
    titleGapC.weightx = 0.9;

    final JLabel userSignedInLabel = new JLabel("not signed in", JLabel.RIGHT);
    final GridBagConstraints titleUserC = new GridBagConstraints();
    titleUserC.gridx = 2;
    titleUserC.gridy = 0;
    titleUserC.anchor = GridBagConstraints.LINE_END;

    titlePanel.add(titleLabel, titleLabelC);
    titlePanel.add(Box.createHorizontalGlue(), titleGapC);
    titlePanel.add(userSignedInLabel, titleUserC);
    titlePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    // User List panel.
    final JPanel listShowPanel = new JPanel();
    final GridBagConstraints listPanelC = new GridBagConstraints();

    final DefaultListModel<String> listModel = new DefaultListModel<>();
    final JList<String> userList = new JList<>(listModel);
    userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    userList.setVisibleRowCount(10);
    userList.setSelectedIndex(-1);

    final JScrollPane userListScrollPane = new JScrollPane(userList);
    listShowPanel.add(userListScrollPane);
    userListScrollPane.setPreferredSize(new Dimension(150, 150));

    // Current User panel
    final JPanel currentPanel = new JPanel();
    final GridBagConstraints currentPanelC = new GridBagConstraints();

    final JTextArea userInfoPanel = new JTextArea();
    final JScrollPane userInfoScrollPane = new JScrollPane(userInfoPanel);
    currentPanel.add(userInfoScrollPane);
    userInfoScrollPane.setPreferredSize(new Dimension(245, 85));

    // Button bar
    final JPanel buttonPanel = new JPanel();
    final GridBagConstraints buttonPanelC = new GridBagConstraints();

    final JButton userUpdateButton = new JButton("Update");
    final JButton userSignInButton = new JButton("Sign In");
    final JButton userSignOutButton = new JButton("Sign Out");
    final JButton userAddButton = new JButton("Add");
    final JButton userDeleteButton = new JButton("Delete");
    final JButton userChangeNameButton = new JButton("Change Username");

    buttonPanel.add(userUpdateButton);
    buttonPanel.add(userSignInButton);
    buttonPanel.add(userSignOutButton);
    buttonPanel.add(userAddButton);
    buttonPanel.add(userDeleteButton);
    buttonPanel.add(userChangeNameButton);

    // Placement of title, list panel, buttons, and current user panel.
    titlePanelC.gridx = 0;
    titlePanelC.gridy = 0;
    titlePanelC.gridwidth = 10;
    titlePanelC.gridheight = 1;
    titlePanelC.fill = GridBagConstraints.HORIZONTAL;
    titlePanelC.anchor = GridBagConstraints.FIRST_LINE_START;

    listPanelC.gridx = 0;
    listPanelC.gridy = 1;
    listPanelC.gridwidth = 10;
    listPanelC.gridheight = 8;
    listPanelC.fill = GridBagConstraints.BOTH;
    listPanelC.anchor = GridBagConstraints.FIRST_LINE_START;
    listPanelC.weighty = 0.8;

    currentPanelC.gridx = 0;
    currentPanelC.gridy = 9;
    currentPanelC.gridwidth = 10;
    currentPanelC.gridheight = 3;
    currentPanelC.fill = GridBagConstraints.HORIZONTAL;
    currentPanelC.anchor = GridBagConstraints.FIRST_LINE_START;

    buttonPanelC.gridx = 0;
    buttonPanelC.gridy = 12;
    buttonPanelC.gridwidth = 10;
    buttonPanelC.gridheight = 1;
    buttonPanelC.fill = GridBagConstraints.HORIZONTAL;
    buttonPanelC.anchor = GridBagConstraints.FIRST_LINE_START;

    this.add(titlePanel, titlePanelC);
    this.add(listShowPanel, listPanelC);
    this.add(buttonPanel, buttonPanelC);
    this.add(currentPanel, currentPanelC);

    userUpdateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UserPanel.this.getAllUsers(listModel);
      }
    });

    userSignInButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (userList.getSelectedIndex() != -1) {
          final String data = userList.getSelectedValue();
          String password = getPasswordFromDialog("Please enter your password:");
          if (clientContext.user.signInUser(data, password))
            userSignedInLabel.setText("Hello " + data);
          else
            JOptionPane.showMessageDialog(UserPanel.this,
                                          "Sign in failure",
                                          "Error",
                                          JOptionPane.ERROR_MESSAGE);
        }
      }
    });

    userSignOutButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        clientContext.user.signOutUser();
        userSignedInLabel.setText("not signed in");
      }
    });

    userAddButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String s = (String) JOptionPane.showInputDialog(
            UserPanel.this, "Enter user name:", "Add User", JOptionPane.PLAIN_MESSAGE,
            null, null, "");
        if (s != null && s.length() > 0) {
          String password = getPasswordFromDialog("Please enter a new password:");
          String confirmPwd = getPasswordFromDialog("Please confirm your password:");
          if (password != null && confirmPwd != null &&
              !confirmPwd.equals(password)) {
            JOptionPane.showMessageDialog(UserPanel.this,
                                          "Passwords do not match",
                                          "Password Confirmation Error",
                                          JOptionPane.ERROR_MESSAGE);
          }
          clientContext.user.addUser(s, password);
          UserPanel.this.getAllUsers(listModel);
        }
      }
    });

    userDeleteButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (userList.getSelectedIndex() != -1) {
            final String data = userList.getSelectedValue();
            String password = getPasswordFromDialog("Please enter your password:");
            if (password != null)
              clientContext.user.deleteUser(data, password);
            UserPanel.this.getAllUsers(listModel);
          }
        }
    });

    userChangeNameButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (userList.getSelectedIndex() != -1) {
          final String data = userList.getSelectedValue();
          final String s = (String) JOptionPane.showInputDialog(
            UserPanel.this, "Enter a new username:", "Change Username", JOptionPane.PLAIN_MESSAGE,
            null, null, "");
          String password = getPasswordFromDialog("Please enter your password:");

          if (s != null && s.length() > 0 && password != null) {
            clientContext.user.changeUserName(data, s, password);
            UserPanel.this.getAllUsers(listModel);
            userSignedInLabel.setText("not signed in");
          }
        }
      }
    });

    userList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (userList.getSelectedIndex() != -1) {
          final String data = userList.getSelectedValue();
          userInfoPanel.setText(clientContext.user.showUserInfo(data));
        }
      }
    });

    getAllUsers(listModel);
  }

  // Takes in dialog message as argument, returns password as string
  private String getPasswordFromDialog(String prompt) {
    JTextField passwordField = new JPasswordField();
    String passwordValue = null;
    Object[] objectArray = {new JLabel(prompt), passwordField};
    int result = JOptionPane.showConfirmDialog(UserPanel.this,
                                               objectArray,
                                               "Password",
                                               JOptionPane.OK_CANCEL_OPTION);

    if (result == JOptionPane.OK_OPTION)
      passwordValue = passwordField.getText();

    return passwordValue;
  }

  // Swing UI: populate ListModel object - updates display objects.
  private void getAllUsers(DefaultListModel<String> usersList) {
    clientContext.user.updateUsers();
    usersList.clear();

    for (final User u : clientContext.user.getUsers()) {
      usersList.addElement(u.name);
    }
  }
}
