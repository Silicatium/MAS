package Agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
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
    private Map<AID, String> computers = new HashMap<>();
    private Map<AID, Boolean> tasks = new HashMap<>(); // determining that all tasks have been sent to computers
    private List<Map.Entry<AID, Integer>> myTasks = new ArrayList<>();
    private Queue<ACLMessage> CFPs = new LinkedList<>();

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
        addBehaviour(new TaskSearchBehaviour());

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
        public void action() {
            // Receiving requests
            ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
            if (cfp != null) CFPs.add(cfp);
            else block();
        }
    }

    private class ProposeSendBehaviour extends CyclicBehaviour {
        public  void action() {
            // Sending information to complete tasks
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
        public void action() {
            // Receiving accept tasks
            ACLMessage accept = receive(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));
            if (accept != null) {
                AID taskAID = accept.getSender();
                myTasks.add(Map.entry(taskAID, Integer.parseInt(accept.getContent())));
                totalTime += Double.parseDouble(accept.getContent()) / capacity;
                tasks.put(taskAID, true);
                System.out.println("ComputerAgent " + computerName + " took the " + taskAID.getName() + ".");
                isBusy = false;
            }
            else block();
        }
    }

    private class RejectBehaviour extends CyclicBehaviour {
        public void action() {
            // Receiving reject tasks
            ACLMessage reject = receive(MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL));
            if (reject != null) {
                tasks.put(reject.getSender(), true);
                isBusy = false;
            }
            else block();
        }
    }

    private class TaskSearchBehaviour extends CyclicBehaviour {
        public void action() {
            // Search tasks in yellow pages
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("tasks");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                for (DFAgentDescription res : result)
                    if (!tasks.containsKey(res.getName())) tasks.put(res.getName(), false);
                if (!tasks.containsValue(false) && !tasks.isEmpty()) {
                    addBehaviour(new ComputerSearchBehaviour());
                    addBehaviour(new SortBehaviour());
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    private class ComputerSearchBehaviour extends OneShotBehaviour {
        public void action() {
            // Search computers in yellow pages
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("task-executing");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                for (DFAgentDescription res : result)
                    if (!computers.containsKey(res.getName())) computers.put(res.getName(), null);

                // Send info to computers
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent(String.valueOf(totalTime) + " " + String.valueOf(capacity));
                for (AID computer: computers.keySet()) msg.addReceiver(computer);
                send(msg);
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }
        }
    }

    private class SortBehaviour extends CyclicBehaviour {
        public void action() {
            // Receive info from computers
            ACLMessage msg = receive(MessageTemplate.MatchPerformative(ACLMessage.INFORM));
            if (msg != null) {
                computers.put(msg.getSender(), msg.getContent());
                if (!computers.containsValue(null)) {
                    // Вetermining the busiest and freest computer
                    Map.Entry<AID, String> maxLoaded = null;
                    Map.Entry<AID, String> minLoaded = null;
                    for (Map.Entry<AID, String> entry : computers.entrySet()) {
                        String[] infoEntry = entry.getValue().split(" ");
                        if (maxLoaded == null || minLoaded == null) {
                            maxLoaded = entry;
                            minLoaded = entry;
                        }
                        String[] infoMax = maxLoaded.getValue().split(" ");
                        String[] infoMin = minLoaded.getValue().split(" ");
                        if (Double.parseDouble(infoMax[0]) < Double.parseDouble(infoEntry[0])) maxLoaded = entry;
                        if (Double.parseDouble(infoMin[0]) > Double.parseDouble(infoEntry[0])) minLoaded = entry;
                        if ((Double.parseDouble(infoMin[0]) == Double.parseDouble(infoEntry[0])) &&
                                (Integer.parseInt(infoMin[1]) < Integer.parseInt(infoEntry[1]))) minLoaded = entry;
                    }
                    if (maxLoaded != null) {
                        String[] infoMax = maxLoaded.getValue().split(" ");
                        if (Double.parseDouble(infoMax[0]) == totalTime) {
                            // That computer the busiest computer
                            String[] infoMin = minLoaded.getValue().split(" ");
                            Map.Entry<AID, Integer> easyTask = null;
                            for (Map.Entry<AID, Integer> task : myTasks) {
                                if (easyTask == null) easyTask = task;
                                if (easyTask.getValue() > task.getValue()) easyTask = task;
                            }
                            if (easyTask != null) {
                                // Remove task from list
                                if (totalTime > (Double.parseDouble(infoMin[0]) + ((double) easyTask.getValue() / Integer.parseInt(infoMin[1])))) {
                                    myTasks.remove(easyTask);
                                    totalTime -= (double) easyTask.getValue() / capacity;
                                    tasks.put(easyTask.getKey(), false);
                                    System.out.println("ComputerAgent " + computerName + " remove " + easyTask.getKey().getName() + ".");
                                    ACLMessage info = new ACLMessage(ACLMessage.INFORM);
                                    info.addReceiver(easyTask.getKey());
                                    send(info);
                                }
                            }
                        }
                    }
                }
            }
            else block();
        }
    }
}
