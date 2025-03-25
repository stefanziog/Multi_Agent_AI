package searchclient;

import java.awt.Point;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Node {

    public Manage manage; // Assuming 'Manage' is a class that handles constraints and has an 'append' method.
    public HashMap<Character, Action[]> solution; // Solutions mapped by agent identifier.
    public int cost; // Cost associated with the node, presumably used in pathfinding heuristics.
    public int maxPadding; // Used to maintain uniform action array lengths across all agents.
    private int hash = 0; // Cache the hash code for the node.

    // Default constructor initializing with high cost and empty structures.
    public Node() {
        this.manage = new Manage();
        this.solution = new HashMap<>();
        this.cost = 100000000; // Initialize with a very high cost.
        this.maxPadding = 0; // No actions have been added yet.
    }

    // Constructor for creating a node based on another node (parent).
    public Node(Node parent) {
        this.manage = new Manage(parent.manage);
        this.solution = new HashMap<>();
        for (Character agent : parent.solution.keySet()) {
            // Copy each agent's action array from the parent node.
            Action[] tmpAction = Arrays.copyOf(parent.solution.get(agent), parent.solution.get(agent).length);
            this.solution.put(agent, tmpAction);
        }
        this.cost = parent.cost; // Inherit cost.
        this.maxPadding = parent.maxPadding; // Inherit maxPadding.
    }

    // Add a constraint for a specific agent.
    public void addConstrain(Character agent, int timeframe, Point point, String type) {
        this.manage.append(agent, timeframe, point, type);
    }

    // Set the action plan for a specific agent and adjust maxPadding accordingly.
    public void setAgentSolution(Character agent, Action[] plan) {
        if (this.maxPadding < plan.length) {
            this.maxPadding = plan.length; // Update maxPadding if the new plan is longer.
        }
        this.solution.put(agent, plan); // Store the action plan.
    }

    // Merge the action plans of all agents into a single two-dimensional array.
    public Action[][] mergePlans() {
        Action[][] merged = new Action[this.maxPadding][this.solution.size()];
        HashMap<Character, Action[]> extendedSolution = new HashMap<>();

        // Extend all action arrays to the length of maxPadding and fill with NoOp as necessary.
        for (Map.Entry<Character, Action[]> entry : this.solution.entrySet()) {
            Action[] actions = entry.getValue();
            if (actions.length < this.maxPadding) {
                Action[] extended = Arrays.copyOf(actions, this.maxPadding);
                Arrays.fill(extended, actions.length, this.maxPadding, Action.NoOp); // Fill remainder with NoOp.
                extendedSolution.put(entry.getKey(), extended);
            } else {
                extendedSolution.put(entry.getKey(), actions);
            }
        }

        // Populate the merged array with actions from each extended solution.
        for (int i = 0; i < this.maxPadding; i++) {
            int index = 0;
            for (Character agent : extendedSolution.keySet()) {
                merged[i][index++] = extendedSolution.get(agent)[i];
            }
        }

        return merged;
    }

    // Update the cost of this node based on the number of conflicts and the length of action plans.
    public void updateCost(int nConf) {
        int cost = 0;
        for (Action[] actions : this.solution.values()) {
            cost += actions.length; // Sum the lengths of all action plans.
        }
        this.cost = cost + nConf; // Add the number of conflicts to the cost.
    }

    // Efficiently generate hash code only once, caching the result.
    @Override
    public int hashCode() {
        if (this.hash == 0) {
            final int prime = 31;
            int result = 1;
            for (Action[] actions : this.solution.values()) {
                result = prime * result + Arrays.hashCode(actions);
            }
            result = prime * result + this.manage.hashCode();
            this.hash = result;
        }
        return this.hash;
    }

    // Check equality based on the manage structure and solutions for all agents.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Node)) return false;
        Node nodeObj = (Node) obj;

        if (!this.manage.equals(nodeObj.manage)) return false;
        for (Character agent : this.solution.keySet()) {
            if (!Arrays.equals(this.solution.get(agent), nodeObj.solution.get(agent))) return false;
        }
        return true;
    }
}