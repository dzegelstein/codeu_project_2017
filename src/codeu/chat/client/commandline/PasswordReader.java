package codeu.chat.client.commandline;

import java.io.Console;

public final class PasswordReader {
  private Console console;

  public PasswordReader() {
    console = null;
  }

  public String read(String prompt) {
      String password = null;
      try {
        console = System.console();
        if (console != null) {
          password = new String(console.readPassword(prompt));
        }
      }
      catch(Exception ex) {
          System.out.println("Error while reading password.");
      }

      return password;
  }
}
