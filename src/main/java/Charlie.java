import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.Function;
import org.mariuszgromada.math.mxparser.mXparser;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class Charlie extends TelegramLongPollingBot {

    private String userName;

    private String token;

    private long opChatId;

    private ArrayList<Argument> arguments = new ArrayList<>();

    private ArrayList<Function> functions = new ArrayList<>();

    public Charlie(String userName, String token, long opChatId) {
        this.userName = userName;
        this.token = token;
        this.opChatId = opChatId;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String receivedMessage = update.getMessage().getText();
            String response = parse(receivedMessage, update);
            SendMessage message = new SendMessage().setChatId(update.getMessage().getChatId()).setText(response);
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private String parse(String receivedMessage, Update update) {

        StringBuilder response = new StringBuilder();

        Expression expression = new Expression(receivedMessage, functions.toArray(new Function[0]));

        if (expression.checkLexSyntax() == Expression.NO_SYNTAX_ERRORS) {
            response.append(parseExpression(response, receivedMessage, expression));
        } else {
            response.append(parseMessage(receivedMessage, update));
        }

        return response.toString();
    }

    private String parseFunction(StringBuilder response, Function function) {
        response.append("Function saved:\n")
                .append(function.getFunctionName()).append(" = ")
                .append(function.getFunctionExpressionString());
        functions.removeIf(a -> a.getFunctionName().equals(function.getFunctionName()));
        functions.add(function);
        for (int i = 0; i < function.getArgumentsNumber(); i++) {
            arguments.add(function.getArgument(i));
        }
        return response.toString();
    }

    private String parseArgument(StringBuilder response, Argument argument) {
        response.append("Argument saved:\n")
                .append(argument.getArgumentName())
                .append(" = ")
                .append(argument.getArgumentValue());
        arguments.removeIf(a -> a.getArgumentName().equals(argument.getArgumentName()));
        arguments.add(argument);
        return response.toString();
    }

    private String parseExpression(StringBuilder response, String receivedMessage, Expression expression) {

        mXparser.enableUlpRounding();

        Function function = new Function(receivedMessage, arguments.toArray(new Argument[0]));
        Argument argument = new Argument(receivedMessage, arguments.toArray(new Argument[0]));

        response.append(expression.getExpressionString()).append(" = ")
                .append(expression.calculate());

        if (function.checkSyntax() == Function.NO_SYNTAX_ERRORS) {
            response.append(parseFunction(response, function));
        } else if (argument.checkSyntax() == Argument.NO_SYNTAX_ERRORS) {
            response.append(parseArgument(response, argument));
        } else {
            response.append(expression.getErrorMessage());
        }
        return response.toString();
    }

    private String parseMessage(String receivedMessage, Update update) {
        String response;
        switch (receivedMessage) {
            case "/start":
                response = getStartMessage(update);
                break;
            case "/help":
                response = getHelpMessage();
                break;
            case "/args":
                response = "Saved arguments:\n" + arguments.stream()
                        .sorted(comparing(Argument::getArgumentName))
                        .map(a -> a.getArgumentName() + " = " + a.getArgumentValue() + "\n")
                        .collect(Collectors.joining());
                break;
            case "/funcs":
                response = "Saved functions:\n" + functions.stream()
                        .sorted(comparing(Function::getFunctionName))
                        .map(f -> f.getFunctionName() + " = " + f.getFunctionExpressionString() + "\n")
                        .collect(Collectors.joining());
                break;
            default:
                response = "Unknown command";
        }
        return response;
    }

    private String getHelpMessage() {
        return "/help - display this message\n\n"
                + "/args - display list of saved arguments (variables)\n\n"
                + "/funcs - display list of saved symbolic functions\n\n"
                + "Declaring a function: \"f(x, y, z, ...) = ...\" \n\n"
                + "Declaring an argument (variable):\n"
                + "\"x\", \"y\", \"z\", etc. \n"
                + "\"x = 10\", \"y = e (2,71..)\", \"z = pi (3.14..)\", etc. \n\n";
    }

    private String getStartMessage(Update update) {
        String botName = "(undefined)";

        try {
            botName = getMe().getFirstName();
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        return "Hey, " + update.getMessage().getFrom().getFirstName() + "!\n"
                + "I'm " + botName + "! "
                + "A telegram bot able to parse mathematical expressions.\n"
                + "My creator is @st4s1k.\n"
                + "I am using the library mXparser to evaluate your expressions, "
                + "you can check it's abilities here:\n"
                + "http://mathparser.org/mxparser-tutorial/\n"
                + "Type /help for help.";
    }

    @Override
    public String getBotUsername() {
        return userName;
    }

    @Override
    public String getBotToken() {
        return token;
    }
}