import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;

public class Main {
    public static void main(String[] args) {
        // Получаем runtime JADE
        Runtime rt = Runtime.instance();

        // Создаем конфигурацию
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "true"); // Включаем GUI

        // Создаем главный контейнер
        AgentContainer mainContainer = rt.createMainContainer(p);
    }
}
