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

            // Receiving

            String receivedMessage = update.getMessage().getText();

            // Handling

            StringBuilder messageString = new StringBuilder();

            mXparser.enableUlpRounding();

            Expression expression = new Expression(
                    receivedMessage,
                    functions.toArray(new Function[0])
            );

            Function function = new Function(
                    receivedMessage,
                    arguments.toArray(new Argument[0])
            );

            Argument argument = new Argument(
                    receivedMessage,
                    arguments.toArray(new Argument[0])
            );

            try {
                if (expression.checkLexSyntax()) {

                    messageString
                            .append("isExpression: ")
                            .append(expression.checkSyntax() == Expression.NO_SYNTAX_ERRORS)
                            .append('\n')
                            .append(expression.getErrorMessage())
                            .append("\n\n")
                            .append("isFunction: ")
                            .append(function.checkSyntax() == Function.NO_SYNTAX_ERRORS)
                            .append('\n')
                            .append(function.getErrorMessage())
                            .append("\n\n")
                            .append("isArgument: ")
                            .append(argument.checkSyntax() == Argument.NO_SYNTAX_ERRORS)
                            .append('\n')
                            .append(argument.getErrorMessage())
                            .append("\n\n");

                     if (expression.checkSyntax() == Expression.NO_SYNTAX_ERRORS) {

                        messageString
                                .append(expression.getExpressionString())
                                .append(" = ")
                                .append(expression.calculate());

                    } else if (function.checkSyntax() == Function.NO_SYNTAX_ERRORS) {

                        messageString
                                .append("Function saved:\n")
                                .append(function.getFunctionName())
                                .append(" = ")
                                .append(function.getFunctionExpressionString());


                         functions.removeIf( a ->
                             a.getFunctionName().equals(function.getFunctionName())
                        );

                         functions.add(function);

                        for (int i = 0; i < function.getArgumentsNumber(); i++) {
                            arguments.add(function.getArgument(i));
                        }

                    } else  if (argument.checkSyntax() == Argument.NO_SYNTAX_ERRORS) {

                        messageString
                                .append("Argument saved:\n")
                                .append(argument.getArgumentName())
                                .append(" = ")
                                .append(argument.getArgumentValue());

                        arguments.removeIf( a ->
                                a.getArgumentName().equals(argument.getArgumentName())
                        );

                        arguments.add(argument);

                    } else {

                        messageString.append(expression.getErrorMessage());

                    }
                } else {

                    switch (receivedMessage) {
                        case "/start":

                            messageString
                                    .append("Hey, ").append(update.getMessage().getFrom().getFirstName()).append("!\n")
                                    .append("I'm ").append(getMe().getFirstName()).append("! ")
                                    .append("A telegram bot able to parse mathematical expressions.\n")
                                    .append("My creator is @st4s1k.\n")
                                    .append("I am using the library mXparser to evaluate your expressions, ")
                                    .append("you can check it's abilities here:\n")
                                    .append("http://mathparser.org/mxparser-tutorial/\n")
                                    .append("Type /help for help.");
                            break;

                        case "/help":

                            messageString
                                    .append("/help - display this mesage\n\n")
                                    .append("/args - display list of saved arguments (variables)\n\n")
                                    .append("/funcs - display list of saved symbolic functions\n\n")
                                    .append("Declaring a function: \"f(x, y, z, ...) = ...\" \n\n")
                                    .append("Declaring an argument (variable):\n")
                                    .append("\"x\", \"y\", \"z\", etc. \n")
                                    .append("\"x = 10\", \"y = e (2,71..)\", \"z = pi (3.14..)\", etc. \n\n");

                            break;

                        case "/args":

                            arguments.sort(Comparator.comparing(Argument::getArgumentName));

                            messageString.append("Saved arguments:\n");

                            for (Argument a: arguments) {
                                messageString
                                        .append(a.getArgumentName())
                                        .append(" = ")
                                        .append(a.getArgumentValue()).append('\n');
                            }
                            break;

                        case "/funcs":

                            functions.sort(Comparator.comparing(Function::getFunctionName));

                            messageString.append("Saved functions:\n");
                            for (Function f: functions) {
                                messageString
                                        .append(f.getFunctionName())
                                        .append(" = ")
                                        .append(f.getFunctionExpressionString()).append('\n');
                            }
                            break;

                        default:

                            execute(new SendMessage()
                                    .setChatId(opChatId)
                                    .setText(
                                            "Firstname: " + update.getMessage().getFrom().getFirstName() +
                                                    "\nLastname: " + update.getMessage().getFrom().getLastName() +
                                                    "\nUsername: @" + update.getMessage().getFrom().getUserName() +
                                                    "\nMessage:\n" + receivedMessage
                                    )
                            );
                    }
                }
            } catch (Exception e) {
                messageString.append(e.getMessage());
            }

            // Sending

            SendMessage message = new SendMessage()
                    .setChatId(update.getMessage().getChatId())
                    .setText(messageString.toString());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
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