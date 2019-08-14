import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.Function;
import org.mariuszgromada.math.mxparser.mXparser;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class Charlie extends TelegramLongPollingBot {

    private String userName;

    private String token;

    private long opChatId;

    private static Map<String, Argument> arguments = new HashMap<>();

    private static Map<String, Function> functions = new HashMap<>();

    private static Map<String, java.util.function.Function<Update, String>> commands = new HashMap<>();

    static {
        commands.put("/start", Charlie::getStartMessage);

        commands.put("/help", update -> getHelpMessage());

        commands.put("/args", update -> "Saved arguments:\n" + arguments.entrySet().stream()
                .sorted(comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .map(a -> a.getArgumentName() + " = " + a.getArgumentValue() + "\n")
                .collect(Collectors.joining()));

        commands.put("/funcs", update -> "Saved functions:\n" + functions.entrySet().stream()
                .sorted(comparing(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .map(f -> f.getFunctionName() + " = " + f.getFunctionExpressionString() + "\n")
                .collect(Collectors.joining()));

        commands.put("/clrargs", update -> {
            arguments = new HashMap<>();
            return "Arguments successfully cleared!";
        });

        commands.put("/clrfuncs", update -> {
            functions = new HashMap<>();
            return "Functions successfully cleared!";
        });
    }

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

            SendMessage message = new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText(response);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private String parse(String receivedMessage, Update update) {

        StringBuilder response = new StringBuilder();

        Expression expression = new Expression(receivedMessage, functions.values().toArray(new Function[0]));

        if (expression.checkLexSyntax() == Expression.NO_SYNTAX_ERRORS) {
            response.append(parseExpression(response, receivedMessage, expression));
        } else {
            response.append(Optional.ofNullable(commands.get(receivedMessage).apply(update))
                    .orElse("Unknown command"));
        }

        return response.toString();
    }

    private String parseFunction(StringBuilder response, Function function) {
        functions.put(function.getFunctionName(), function);
        function = functions.get(function.getFunctionName());
        response.append("Function saved:\n")
                .append(function.getFunctionName()).append(" = ")
                .append(function.getFunctionExpressionString());
        return response.toString();
    }

    private String parseArgument(StringBuilder response, Argument argument) {
        arguments.put(argument.getArgumentName(), argument);
        argument = arguments.get(argument.getArgumentName());
        response.append("Argument saved:\n")
                .append(argument.getArgumentName())
                .append(" = ")
                .append(argument.getArgumentValue());
        return response.toString();
    }

    private String parseExpression(StringBuilder response, String receivedMessage, Expression expression) {

        mXparser.enableUlpRounding();

        Function function = new Function(receivedMessage, arguments.values().toArray(new Argument[0]));
        Argument argument = new Argument(receivedMessage, arguments.values().toArray(new Argument[0]));

        response.append(expression.getExpressionString())
                .append(" = ")
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

    private static String getHelpMessage() {
        return "/help - display this message\n\n"
                + "/args - display list of saved arguments (variables)\n\n"
                + "/funcs - display list of saved symbolic functions\n\n"
                + "/clrargs - remove all saved arguments\n\n"
                + "/clrfuncs - remove all saved functions\n\n"
                + "Declaring a function: \"f(x, y, z, ...) = ...\" \n\n"
                + "Declaring an argument (variable):\n"
                + "\"x\", \"y\", \"z\", etc. \n"
                + "\"x = 10\", \"y = e (2,71..)\", \"z = pi (3.14..)\", etc. \n\n";
    }

    private static String getStartMessage(Update update) {
        return "Hey, " + update.getMessage().getFrom().getFirstName() + "!\n"
                + "I'm Charlie! "
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