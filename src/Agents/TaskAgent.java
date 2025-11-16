package Agents;

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
import jade.util.leap.Iterator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TaskAgent extends Agent {
    // Define variables
    private int complexity;
    private AID myComputer;
    private AID[] computerAgents;
    private Map<AID, Double> finishTimes = new HashMap<>();;

    protected void setup() {
        // Filling variables
        Object[] args = getArguments();
        if (args != null && args.length > 0)
            complexity = Integer.parseInt((String) args[0]);
        else complexity = 100;

        // Add Behaviours
        addBehaviour(new SearchComputersBehaviour());
        addBehaviour(new AnswersGetBehaviour());
        addBehaviour(new ComputerConfirmationBehaviour());

        System.out.println("TaskAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown(){
        System.out.println("TaskAgent " + getAID().getName() + " is terminated.");
    }

    private  class SearchComputersBehaviour extends OneShotBehaviour {
        public void action() {
            // Search in yellow pages
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("task-executing");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                computerAgents = new AID[result.length];
                for (int i = 0; i < result.length; ++i)
                    computerAgents[i] = result[i].getName();
                for (AID computerAgent : computerAgents)
                    if (!finishTimes.containsKey(computerAgent))
                        finishTimes.put(computerAgent, null);
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }

            // Send task to computers
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (AID computer : computerAgents) cfp.addReceiver(computer);
            cfp.setContent(String.valueOf(complexity));
            send(cfp);
        }
    }

    private class AnswersGetBehaviour extends CyclicBehaviour {
        public void action() {
            // Getting execution times from computers
            ACLMessage reply = receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
            if (reply != null && myComputer == null)
                finishTimes.put(reply.getSender(), Double.parseDouble(reply.getContent()));
            else block();
        }
    }

    private class ComputerConfirmationBehaviour extends CyclicBehaviour {
        public void action() {
            if (!finishTimes.containsValue(null) && myComputer == null) {
                // Computer set
                double bestTime = Double.MAX_VALUE;
                AID bestComputer = null;
                for (Map.Entry<AID, Double> entry : finishTimes.entrySet()) {
                    if (entry.getValue() < bestTime) {
                        bestTime = entry.getValue();
                        bestComputer = entry.getKey();
                    }
                }
                myComputer = bestComputer;

                // Answers to computers
                ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                for (AID computerAgent : computerAgents) {
                    if (Objects.equals(computerAgent.getName(), myComputer.getName())) {
                        accept.addReceiver(computerAgent);
                        accept.setContent(String.valueOf(complexity));
                    }
                    else reject.addReceiver(computerAgent);
                }
                send(accept);
                send(reject);
            }
        }
    }
}
