import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.mXparser;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Charlie extends TelegramLongPollingBot {

    private static String name = "Charlie";
    private static String token = "767946678:AAGhduP0bRqNpubiS8h77qUXp7DzGdgB3p0";

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {

            String receivedMessage = update.getMessage().getText();

            StringBuilder messageString = new StringBuilder();

            messageString
                    .append("Hey, ")
                    .append(update
                            .getMessage()
                            .getFrom()
                            .getFirstName())
                    .append("!\n");

            Expression expression = new Expression(receivedMessage);

            try {
                if (expression.checkSyntax() || expression.checkLexSyntax()) {
                    messageString
                            .append(expression.getExpressionString())
                            .append(" = ")
                            .append(expression.calculate());
                } else {
                    messageString.append(expression.getErrorMessage());
                }
            } catch (Exception e) {
                messageString.append(e.getMessage());
            }

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
        return name;
    }

    @Override
    public String getBotToken() {
        return token;
    }
}