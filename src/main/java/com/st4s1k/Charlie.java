package com.st4s1k;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.Function;
import org.mariuszgromada.math.mxparser.mXparser;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Map.Entry;
import static java.util.Map.Entry.comparingByKey;

public class Charlie extends TelegramLongPollingBot {

  private String token;
  private String botUserName;
  private Map<Long, Session> sessions = new HashMap<>();
  private Map<String, java.util.function.Function<Session, String>> commands = new HashMap<>();

  {
    commands.put("/start", Charlie::getStartMessage);

    commands.put("/help", session -> getHelpMessage());

    commands.put("/args", session -> "Saved arguments:\n" +
        session.getArguments().entrySet().stream()
            .sorted(comparingByKey())
            .map(Entry::getValue)
            .map(a -> a.getArgumentName() + " = " + a.getArgumentValue() + "\n")
            .collect(Collectors.joining()));

    commands.put("/funcs", session -> "Saved functions:\n" +
        session.getFunctions().entrySet().stream()
            .sorted(comparingByKey())
            .map(Entry::getValue)
            .map(f -> f.getFunctionName() + " = " + f.getFunctionExpressionString() + "\n")
            .collect(Collectors.joining()));

    commands.put("/clra", session -> {
      session.getArguments().clear();
      return "Arguments successfully cleared!";
    });

    commands.put("/clrf", session -> {
      session.getFunctions().clear();
      return "Functions successfully cleared!";
    });
  }

  public Charlie(
      final String token,
      final String botUserName) {
    this.token = token;
    this.botUserName = botUserName;
  }

  @Override
  public String getBotUsername() {
    return botUserName;
  }

  @Override
  public String getBotToken() {
    return token;
  }

  @Override
  public void onUpdateReceived(final Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {

      final var message = update.getMessage();
      final var chat = message.getChat();
      final var user = message.getFrom();
      final var chatId = chat.getId();

      sessions.putIfAbsent(chatId, new Session(chat, user));

      final var session = sessions.get(chatId);
      final var receivedMessage = message.getText();
      final var response = parse(receivedMessage, session);

      if (!response.isEmpty()) {
        SendMessage sendMessage = new SendMessage()
            .setChatId(chatId)
            .setText(response);
        try {
          execute(sendMessage);
        } catch (Throwable e) {
          e.printStackTrace();
        }
      }
    }
  }

  private String parse(
      final String receivedMessage,
      final Session session) {

    final var response = new StringBuilder();

    try {
      final var expression = new Expression(receivedMessage);
      final var arguments = session.getArgumentArray();
      final var functions = session.getFunctionArray();

      expression.addArguments(arguments);
      expression.addFunctions(functions);

      if (expression.checkLexSyntax() == Expression.NO_SYNTAX_ERRORS) {
        parseExpression(expression, session, response);
      } else {
        final String user = session.getUser().getFirstName();
        response.append(Optional.ofNullable(commands.get(receivedMessage))
            .map(c -> c.apply(session))
            .orElse("I don't understand, " + user + " ..."));
      }
    } catch (Throwable t) {
      response.append("\n").append(t.getMessage());
    }

    return response.toString();
  }

  private void parseExpression(
      final Expression expression,
      final Session session,
      final StringBuilder response) {

    mXparser.enableUlpRounding();

    if (expression.checkSyntax() == Expression.NO_SYNTAX_ERRORS) {
      response
          .append(expression.getExpressionString())
          .append(" = ")
          .append(expression.calculate());
    } else {
      final var sessionArguments = session.getArgumentArray();
      final var sessionFunctions = session.getFunctionArray();
      final var argument = new Argument(expression.getExpressionString());
      final var function = new Function(expression.getExpressionString());

      if (function.checkSyntax() == Function.NO_SYNTAX_ERRORS) {
        function.addArguments(sessionArguments);
        function.addFunctions(sessionFunctions);
        session.addFunction(function);
        response.append("\n");
        addFunction(function, session, response);
      } else if (argument.checkSyntax() == Argument.NO_SYNTAX_ERRORS) {
        argument.addArguments(sessionArguments);
        argument.addFunctions(sessionFunctions);
        response.append("\n");
        addArgument(argument, session, response);
      } else {
        response.append("\n").append(expression.getErrorMessage());
      }
    }
  }

  private void addFunction(
      final Function function,
      final Session session,
      final StringBuilder response) {
    session.addFunction(function);
    response
        .append("Function saved:\n")
        .append(function.getFunctionName()).append(" = ")
        .append(function.getFunctionExpressionString());
  }

  private void addArgument(
      final Argument argument,
      final Session session,
      final StringBuilder response) {
    session.addArgument(argument);
    response.append("Argument saved:\n")
        .append(argument.getArgumentName());
    if (!Double.isNaN(argument.getArgumentValue())) {
      response.append(" = ").append(argument.getArgumentValue());
    }
  }

  private static String getHelpMessage() {
    return "/help - display this message\n\n"
        + "/args - display list of saved arguments (variables)\n\n"
        + "/funcs - display list of saved symbolic functions\n\n"
        + "/clra - remove all saved arguments\n\n"
        + "/clrf - remove all saved functions\n\n"
        + "Declaring a function: \"f(x, y, z, ...) = ...\" \n\n"
        + "Declaring an argument (variable):\n"
        + "\"x\", \"y\", \"z\", etc. \n"
        + "\"x = 10\", \"y = e (2,71..)\", \"z = pi (3.14..)\", etc. \n\n";
  }

  private static String getStartMessage(final Session session) {
    final var user = session.getUser().getFirstName();
    return "Hey, " + user + "!\n"
        + "I'm Charlie! "
        + "A telegram bot able to parse mathematical expressions.\n"
        + "My creator is @st4s1k.\n"
        + "I am using the library mXparser to evaluate your expressions, "
        + "you can check it's abilities here:\n"
        + "http://mathparser.org/mxparser-tutorial/\n"
        + "Type /help for help.";
  }
}