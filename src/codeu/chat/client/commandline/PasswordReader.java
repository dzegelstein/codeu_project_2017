package codeu.chat.client.commandline;

import java.io.Console;

/**
* The PasswordReader class uses the readPassword function in
* java.io.Console to mask the password input as it is being entered
* in the command line.
*
* @author Sherry Bai
*/
public final class PasswordReader {
  private Console console;

  public PasswordReader() {
    console = null;
  }

  /**
  * This method attempts to print a prompt to the current terminal
  * and read a password input to that terminal.
  * @param prompt This is the prompt printed to the terminal.
  * @return String This is the password read in by the readPassword function.
  */
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
