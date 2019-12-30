import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
  public static void main(String[] args) {
    ApiContextInitializer.init();
    TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
    try {
      final String token = "767946678:AAGhduP0bRqNpubiS8h77qUXp7DzGdgB3p0";
      final String botUserName = "charlie12bot";
      final Charlie charlie = new Charlie(token, botUserName);
      telegramBotsApi.registerBot(charlie);
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }
}