import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
    public static void main(String[] args) {

        // 1
        ApiContextInitializer.init();

        // 2
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();

        // 3
        try {
            telegramBotsApi.registerBot(
                    new Charlie(
                            "charlie12bot",
                            "767946678:AAGhduP0bRqNpubiS8h77qUXp7DzGdgB3p0",
                            397271841L
                            )
            );
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}