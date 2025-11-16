import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class Main {
    static void main() {
        Runtime rt = Runtime.instance();

        // Configuration
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        p.setParameter(Profile.GUI, "true"); // Включаем GUI
        AgentContainer mainContainer = rt.createMainContainer(p);

        try {
            AgentController computer1 = mainContainer.createNewAgent(
                    "Computer1",
                    "Agents.ComputerAgent",
                    new Object[]{"2000"}
            );
            computer1.start();
            AgentController computer2 = mainContainer.createNewAgent(
                    "Computer2",
                    "Agents.ComputerAgent",
                    new Object[]{"1000"}
            );
            computer2.start();
            AgentController task1 = mainContainer.createNewAgent(
                    "Task1",
                    "Agents.TaskAgent",
                    new Object[]{"20000"}
            );

            task1.start();
            AgentController task2 = mainContainer.createNewAgent(
                    "Task2",
                    "Agents.TaskAgent",
                    new Object[]{"20000"}
            );
            task2.start();
            AgentController task3 = mainContainer.createNewAgent(
                    "Task3",
                    "Agents.TaskAgent",
                    new Object[]{"20000"}
            );
            task3.start();
        } catch (StaleProxyException e) {
            throw new RuntimeException(e);
        }
    }
}
