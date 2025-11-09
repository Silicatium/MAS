package Agents;


import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class ComputerAgent extends Agent {
    private String computerName;
    private int capacity;
    private AID[] myTasks;
    protected void setup() {
        computerName = getAID().getName();
        System.out.println("ComputerAgent "+ computerName +" is ready.");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Integer.parseInt((String) args[0]);
        }
        else {
            capacity = 1000;
        }
        addBehaviour(new RequestBehaviour());
    }
    protected void takeDown() {
        addBehaviour(new LeaveBehaviour());
        System.out.println("ComputerAgent "+getAID().getName()+" is terminated.");
    }
    private class RequestBehaviour extends CyclicBehaviour {
        public void action() {
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

        }
    }
    private class LeaveBehaviour extends OneShotBehaviour {
        public void action() {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.setOntology("TerminateOntology");
            msg.setContent(computerName);
            for (AID myTask : myTasks) msg.addReceiver(myTask);
            send(msg);
        }
    }
}
