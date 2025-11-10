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

public class TaskAgent extends Agent {
    // Define variables
    private int complexity;
    private double finishTime;
    private long timer; // timer for choosing computer;
    private AID myComputer;
    private AID bestComputer;
    private AID[] computerAgents;

    protected void setup() {
        // Filling variables
        Object[] args = getArguments();
        if (args != null && args.length > 0)
            complexity = Integer.parseInt((String) args[0]);
        else complexity = 100;
        timer = System.currentTimeMillis();
        finishTime = Double.MAX_VALUE;

        // Add Behaviours
        addBehaviour(new SearchComputersBehaviour());
        addBehaviour(new ComputerConfirmationBehaviour());
        addBehaviour(new ComputerLeaveBehaviour());

        System.out.println("TaskAgent " + getAID().getName() + " is ready.");
    }

    protected void takeDown(){
        // Task leave
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setOntology("TerminateOntology");
        msg.setContent(String.valueOf(complexity));
        msg.addReceiver(myComputer);
        send(msg);
        myComputer = null;
        System.out.println("TaskAgent " + getAID().getName() + " is terminated.");
    }

    private  class SearchComputersBehaviour extends CyclicBehaviour {
        public void action() {
            if (myComputer == null) {
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
    }

    private class ComputerConfirmationBehaviour extends CyclicBehaviour {
        public void action() {
            // Computer set
            if (myComputer == null && bestComputer != null && System.currentTimeMillis() - timer >= 3000) {
                myComputer = bestComputer;
                ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                accept.addReceiver(myComputer);
                accept.setContent(String.valueOf(complexity));
                send(accept);
            }
            // Receive computer answers
            ACLMessage reply = receive(MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
            if (reply != null && myComputer == null) {
                double time = Double.parseDouble(reply.getContent());
                if (finishTime > time) {
                    finishTime = time;
                    bestComputer = reply.getSender();
                }
            }
        }
    }

    private class ComputerLeaveBehaviour extends CyclicBehaviour {
        // Computer is terminated
        public void action() {
            MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                    MessageTemplate.MatchOntology("TerminateOntology"));
            ACLMessage msg = receive(mt);
            if (msg != null) {
                myComputer = null;
                bestComputer = null;
                finishTime = Double.MAX_VALUE;
                timer = System.currentTimeMillis();
                System.out.println("TaskAgent " + getAID().getName() + " is free.");
            }
            else block();
        }
    }
}
