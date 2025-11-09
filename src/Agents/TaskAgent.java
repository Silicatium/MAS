package Agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

public class TaskAgent extends Agent {
    private AID myComputer;
    protected void setup() {

    }
    protected void takeDown(){

    }
    private class OfferRequestsBehaviour extends CyclicBehaviour {
        public void action() {

        }
    }
    private class AcceptedRequestsBehaviour extends CyclicBehaviour {
        public void action() {

        }
    }
    private class EndBehaviour extends OneShotBehaviour {
        public void action() {

        }
    }
}
