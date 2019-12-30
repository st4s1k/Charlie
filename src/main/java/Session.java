import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Function;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("ReturnPrivateMutableField")
class Session {

  private final Chat chat;
  private final User user;
  private final Map<String, Argument> arguments;
  private final Map<String, Function> functions;

  public Session(
      final Chat chat,
      final User user) {
    this.chat = chat;
    this.user = user;
    this.arguments = new HashMap<>();
    this.functions = new HashMap<>();
  }

  public Chat getChat() {
    return chat;
  }

  public User getUser() {
    return user;
  }

  public void addFunction(final Function function) {
    functions.put(function.getFunctionName(), function);
  }

  public void addArgument(final Argument argument) {
    arguments.put(argument.getArgumentName(), argument);
  }

  public Map<String, Argument> getArguments() {
    return arguments;
  }

  public Map<String, Function> getFunctions() {
    return functions;
  }

  public Function[] getFunctionArray() {
    final Function[] functionsEmptyArray = new Function[functions.size()];
    return functions.values().toArray(functionsEmptyArray);
  }

  public Argument[] getArgumentArray() {
    final Argument[] argumentsEmptyArray = new Argument[arguments.size()];
    return arguments.values().toArray(argumentsEmptyArray);
  }

  @Override
  public boolean equals(Object o) {
    if (Objects.equals(this, o)) return true;
    if (!(o instanceof Session)) return false;
    Session session = (Session) o;
    return Objects.equals(chat.getId(), session.chat.getId()) &&
        Objects.equals(user.getId(), session.user.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(chat.getId(), user.getId());
  }
}
