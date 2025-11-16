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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class ComputerAgent extends Agent {
    // Define variables
    private String computerName;
    private int capacity; // op/sec
    private double busyTime = 0;
    private boolean busy = false; // can communicate only with 1 task until it receive an answer
    List<AID> myTasks = new ArrayList<>();
    Queue<ACLMessage> CFPs = new LinkedList<>();

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
        addBehaviour(new RequestReceiveBehaviour());
        addBehaviour(new ProposeSendBehaviour());
        addBehaviour(new AcceptBehaviour());
        addBehaviour(new RejectBehaviour());

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
        System.out.println("ComputerAgent " + computerName + " is terminated.");
    }

    private class RequestReceiveBehaviour extends CyclicBehaviour {
        // Receiving requests
        public void action() {
            ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
            if (cfp != null && !CFPs.contains(cfp)) CFPs.add(cfp);
            else block();
        }
    }

    private class ProposeSendBehaviour extends CyclicBehaviour {
        // Sending information to complete tasks
        public  void action() {
            if (!busy && !CFPs.isEmpty()) {
                ACLMessage cfp = CFPs.poll();
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(String.valueOf((busyTime + (Double.parseDouble(cfp.getContent()) / capacity))));
                send(propose);
                busy = true;
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
                busyTime = Double.parseDouble(msg.getContent());
                System.out.println("ComputerAgent " + computerName + " took the task" + taskAID.getName() + ".");
                busy = false;
            }
            else block();
        }
    }

    private class RejectBehaviour extends CyclicBehaviour {
        // Receiving tasks
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL));
            if (msg != null) busy = false;
            else block();
        }
    }
}
