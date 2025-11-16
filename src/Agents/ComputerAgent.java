package Agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ComputerAgent extends Agent {
    // Define variables
    private String computerName;
    private int capacity; // op/sec
    private double totalTime = 0;
    private boolean isBusy = false; // can communicate only with 1 task until it receive an answer
    List<Map.Entry<AID, Integer>> myTasks = new ArrayList<>();
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

        // Write info to file
        ObjectMapper mapper = new ObjectMapper();
        File resultsFile = new File("src/Files/results.json");
        ObjectNode root;

        try {
            if (resultsFile.exists()) root = (ObjectNode) mapper.readTree(resultsFile);
            else root = mapper.createObjectNode();

            ObjectNode computerNode = mapper.createObjectNode();
            computerNode.put("capacity", capacity); // значение по умолчанию
            computerNode.putArray("tasks");
            computerNode.put("total_time", totalTime);

            ArrayNode tasksArray = (ArrayNode) computerNode.get("tasks");
            for (Map.Entry<AID, Integer> entry : myTasks) {
                ObjectNode taskNode = mapper.createObjectNode();
                AID task = entry.getKey();
                int complexity = entry.getValue();
                taskNode.put("name", task.getName());
                taskNode.put("complexity", complexity);
                tasksArray.add(taskNode);
            }

            root.set(computerName, computerNode);
            mapper.writerWithDefaultPrettyPrinter().writeValue(resultsFile, root);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
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
            if (!isBusy && !CFPs.isEmpty()) {
                ACLMessage cfp = CFPs.poll();
                ACLMessage propose = cfp.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(String.valueOf((totalTime + (Double.parseDouble(cfp.getContent()) / capacity))));
                send(propose);
                isBusy = true;
            }
        }
    }

    private class AcceptBehaviour extends CyclicBehaviour {
        // Receiving tasks
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
            if (msg != null) {
                AID taskAID = msg.getSender();
                myTasks.add(Map.entry(taskAID, Integer.parseInt(msg.getContent())));
                totalTime += Double.parseDouble(msg.getContent()) / capacity;
                System.out.println("ComputerAgent " + computerName + " took the task" + taskAID.getName() + ".");
                isBusy = false;
            }
            else block();
        }
    }

    private class RejectBehaviour extends CyclicBehaviour {
        // Receiving tasks
        public void action() {
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL));
            if (msg != null) isBusy = false;
            else block();
        }
    }
}
