import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
  public static void main(final String[] args) {
    ApiContextInitializer.init();
    final var telegramBotsApi = new TelegramBotsApi();
    try {
      final var token = "767946678:AAGhduP0bRqNpubiS8h77qUXp7DzGdgB3p0";
      final var botUserName = "charlie12bot";
      final var charlie = new Charlie(token, botUserName);
      telegramBotsApi.registerBot(charlie);
    } catch (TelegramApiException e) {
      e.printStackTrace();
    }
  }
}