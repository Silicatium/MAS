package Agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.ArrayList;
import java.util.List;

public class ComputerAgent extends Agent {
    // Define variables
    private String computerName;
    private int capacity; // op/sec
    private double busyTime = 0;
    List<AID> myTasks = new ArrayList<>();

    protected void setup() {
        // Filling variables
        computerName = getAID().getName();
        Object[] args = getArguments();
        if (args != null && args.length > 0)
            capacity = Integer.parseInt((String) args[0]);
        else capacity = 1000;

        // Register in yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("task-executing");
        sd.setName("JADE-Computer");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Add Behaviours
        addBehaviour(new RequestBehaviour());
        addBehaviour(new AcceptBehaviour());
        addBehaviour(new TaskLeaveBehaviour());

        System.out.println("ComputerAgent " + computerName + " is ready.");
    }

    protected void takeDown() {
        // Deregister in yellow pages
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Task returns
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setOntology("TerminateOntology");
        msg.setContent("I am completing my process");
        for (AID myTask : myTasks) msg.addReceiver(myTask);
        send(msg);
        busyTime = 0;
        System.out.println("ComputerAgent " + computerName + " is terminated.");
    }

    private class RequestBehaviour extends CyclicBehaviour {
        // Sending information to complete tasks
        public void action() {
            ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
            if (cfp != null) {
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(String.valueOf((busyTime + (Double.parseDouble(cfp.getContent()) / capacity))));
                send(propose);
            }
            else block();
        }
    }

    private class AcceptBehaviour extends CyclicBehaviour {
        // Receiving tasks
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
            if (msg != null) {
                AID taskAID = msg.getSender();
                myTasks.add(taskAID);
                busyTime += Double.parseDouble(msg.getContent()) / capacity;
                System.out.println("ComputerAgent " + computerName + " took the task" + taskAID.getName() + ".");
            }
            else block();
        }
    }

    private class TaskLeaveBehaviour extends CyclicBehaviour {
        // Task is terminated
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchOntology("TerminateOntology"));
            ACLMessage msg = receive(mt);
            if (msg != null) {
                busyTime -= Double.parseDouble(msg.getContent());
                myTasks.remove(msg.getSender());
                System.out.println("TaskAgent " + msg.getSender().getName() + " was removed from my list.");
            }
            else block();
        }
    }
}
