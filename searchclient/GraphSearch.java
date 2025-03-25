package searchclient;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.*;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GraphSearch {

    public static Action[][] advancedSearch(State initialState) {
        int[][][][] realDistanceGrid = logElapsedTime("Compute with Floyd-Warshall.....", () -> preComputeRealDistance(initialState));


        Path path = new Path();

        HashMap<Character,List<SubGoal>> subGoals = path.path(initialState,realDistanceGrid);
        path.removeAndGetUnassigned(subGoals);

        // Split maps for the single agent problem based on the subGoals found
        // CAUTION: The subgoals contain also subGoal with boxes but no goals
        //   this is to assign with an optimal way also the boxes without goal to an agent.
        //   We need to remove them later or don't count them.
        State[] states = splitStates(initialState, subGoals);

        // Evaluate subGoals greedily. Closest first!
        // TODO: Scheduler needs to be improved.
        evaluateCostOfSubGoals(subGoals, initialState, realDistanceGrid);

        // Start CBS ROUTINE
        return initializeAndRunCBS(initialState, states, subGoals, realDistanceGrid);
    }

    private static int[][][][] logElapsedTime(String message, Supplier<int[][][][]> task) {
        System.err.println(message);
        long startT = System.nanoTime();
        int[][][][] result = task.get();
        double elapsedTime = (System.nanoTime() - startT) / 1_000_000_000d;
        System.err.println("The magic " + elapsedTime + "s");
        return result;  // Return the computed distance grid
    }

    private static Action[][] initializeAndRunCBS(State initialState, State[] states, HashMap<Character, List<SubGoal>> subGoals, int[][][][] realDistanceGrid) {
        Node root = new Node();
        FrontierCBS frontierCBS = new FrontierCBS(new HeuristicCBS(root));

        for (int agent = 0; agent < initialState.agentRows.length; agent++) {
            Action[][] solution = SerializedAstar(states[agent], subGoals.get((char) (agent + '0')), realDistanceGrid);
            assert solution != null;
            root.setAgentSolution((char) (agent + '0'), getAgentPlan(solution));
        }

        frontierCBS.add(root);
        return cbs(initialState, states, subGoals, frontierCBS, realDistanceGrid);
    }
    public static Action[][] cbs(State initialState, State[] singleAgentStates, HashMap<Character,List<SubGoal>> subGoals, FrontierCBS frontier, int[][][][] realDistanceGrid){
        HashSet<Node> expanded = new HashSet<>();
        while (!frontier.isEmpty()) {
            Node currentNode = frontier.pop();
            expanded.add(currentNode);
            ExecutionInfo<Integer,Conflict,State> executionInfo = executeSolution(initialState, currentNode);

            if (executionInfo.nConf == 0) {
                return currentNode.mergePlans();
            }

            handleConflicts(currentNode, executionInfo, frontier, expanded, singleAgentStates, subGoals, realDistanceGrid);
        }
        return null; // If the frontier is empty and no solution was found
    }

    private static void handleConflicts(Node currentNode, ExecutionInfo<Integer,Conflict,State> executionInfo, FrontierCBS frontier, Set<Node> expanded, State[] singleAgentStates, HashMap<Character,List<SubGoal>> subGoals, int[][][][] realDistanceGrid) {
        for (Character agent : executionInfo.firstConf.getAgents()) {
            Node newNode = resolveAgentConflict(agent, currentNode, executionInfo, singleAgentStates, subGoals, realDistanceGrid);
            if (newNode != null && !frontier.contains(newNode) && !expanded.contains(newNode)) {
                frontier.add(newNode);
            }
        }
    }

    private static Node resolveAgentConflict(Character agent, Node currentNode, ExecutionInfo<Integer,Conflict,State> executionInfo, State[] singleAgentStates, HashMap<Character,List<SubGoal>> subGoals, int[][][][] realDistanceGrid) {
        Node newNode = new Node(currentNode);
        newNode.addConstrain(agent, executionInfo.firstConf.getConflictTimeframe(agent), executionInfo.firstConf.getConflictPosition(agent), "agent");
        singleAgentStates[agent - '0'].copyConstrains(newNode.manage, agent);

        Action[][] solution = SerializedAstar(singleAgentStates[agent - '0'], subGoals.get(agent), realDistanceGrid);
        if (solution == null) {
            return null;
        }

        newNode.setAgentSolution(agent, getAgentPlan(solution));
        newNode.updateCost(executionInfo.nConf);
        return newNode;
    }
    public static Action[][] MBAstar(State initialState, List<SubGoal> subGoals, int[][][][] realDistanceGrid)
    {
        Action[][] tmpPlan = null;

        PriorityQueue<State> frontier = new PriorityQueue<>(new HeuristicMBA(initialState, realDistanceGrid, null));
        HashSet<State> expanded = new HashSet<>();

        for (SubGoal subGoal : subGoals) {
            initialState.setWall(subGoal);
            frontier.add(initialState);

            while (!frontier.isEmpty()) {
                State currentState = frontier.poll();
                expanded.add(currentState);

                if (currentState.isGoalStateLastSubGoal()) {
                    tmpPlan = currentState.extractPlan();
                    break;
                }

                for (State state : currentState.getExpandedStates()) {
                    if (!expanded.contains(state)) {
                        frontier.add(state);
                    }
                }
            }

            if (tmpPlan != null) {
                break;
            }
        }

        return tmpPlan;
    }
    public static Action[][] SerializedAstar(State initialState, List<SubGoal> subGoals, int[][][][] realDistanceGrid)
    {
        Action[][] tmpPlan = null;
        State currState = initialState;

        if(subGoals == null || subGoals.isEmpty()){
            Frontier frontier = new FrontierBestFirst(new HeuristicMBA(currState, realDistanceGrid, null));
            int iterations = 0;
            frontier.add(currState);
            HashSet<State> expanded = new HashSet<>();
            while (true) {
                if (++iterations % 200000 == 0) {
                    System.err.println("Searching.....");
                }
                if (frontier.isEmpty()) {
                    //System.err.println("Solution was not found! :'( ");
                    return null;
                }
                State currentState = frontier.pop();
                expanded.add(currentState);
                if(currentState.isGoalStateLastSubGoal()){
                    tmpPlan = currentState.extractPlan();
                    break;
                } else {
                    for(State state: currentState.getExpandedStates()){
                        if(frontier.contains(state) && !expanded.contains(state)){
                            frontier.add(state);
                        }
                    }
                }
            }
            return tmpPlan;
        }

        FrontierSubGoal subGoalFrontier = new FrontierSubGoal(new HeuristicSubGoal());
        for(SubGoal sub: subGoals){
            subGoalFrontier.add(sub);
        }

        //Maybe later a priority queue, pop and while not empty loop
        while(!subGoalFrontier.isEmpty()){
            SubGoal subGoal = subGoalFrontier.pop();
            //System.err.println(subGoal.toString());
            Frontier frontier = new FrontierBestFirst(new HeuristicMBA(currState, realDistanceGrid, subGoal));
            int iterations = 0;
            frontier.add(currState);
            HashSet<State> expanded = new HashSet<>();

            while (true) {
                if (++iterations % 200000 == 0) {
                    System.err.println("Searching.....");
                }
                if (frontier.isEmpty()) {
                    //System.err.println("Solution was not found! :'( ");
                    return null;
                }

                State currentState = frontier.pop();
                expanded.add(currentState);
                if(subGoalFrontier.isEmpty()){
                    if(currentState.isGoalStateLastSubGoal()){
                        //System.err.println("--------- Sub goal completed ---------");
                        //printSearchStatus(expanded, frontier);
                        //System.err.println("--------------------------------------");
                        tmpPlan = currentState.extractPlan();
                        currState = currentState;
                        currState.setWall(subGoal);
                        break;
                    } else {
                        for(State state: currentState.getExpandedStates()){
                            //System.err.println("ADDING NEW NODE!");
                            if(frontier.contains(state) && !expanded.contains(state)){
                                frontier.add(state);
                            }
                        }
                    }
                } else{
                    if(currentState.isSubGoalState(subGoal)){
                        //System.err.println("--------- Sub goal completed ---------");
                        //printSearchStatus(expanded, frontier);
                        //System.err.println("--------------------------------------");
                        tmpPlan = currentState.extractPlan();
                        currState = currentState;
                        currState.setWall(subGoal);
                        break;
                    } else {
                        for(State state: currentState.getExpandedStates()){
                            if(frontier.contains(state) && !expanded.contains(state)){
                                frontier.add(state);
                            }
                        }
                    }
                }
            }
        }
        return tmpPlan;
    }
    public static class ExecutionInfo<F, S, State> {
        public final F nConf;
        public final S firstConf;
        public final State finalState;

        public ExecutionInfo(F nConf, S firstConf, State finalState) {
            this.nConf = nConf;
            this.firstConf = firstConf;
            this.finalState = finalState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExecutionInfo<?, ?, ?> that)) return false;
            return Objects.equals(nConf, that.nConf) && Objects.equals(firstConf, that.firstConf) && Objects.equals(finalState, that.finalState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nConf, firstConf, finalState);
        }
    }
    private static ExecutionInfo<Integer, Conflict, State> executeSolution(State initialState, Node node) {
        State currentState = initialState;
        Action[][] solution = node.mergePlans();
        int totalConflicts = 0;
        Conflict firstConflict = null;

        for (int time = 0; time < node.maxPadding; time++) {
            State.Pair<Integer, Conflict> conflictInfo = currentState.returnFirstConflict(solution[time]);
            if (conflictInfo.first != 0) {
                if (firstConflict == null) {
                    firstConflict = conflictInfo.second;
                }
                totalConflicts += conflictInfo.first;
            }
            currentState = new State(currentState, solution[time]);
            // Debug: System.err.println("Found: " + conflictInfo.first + " in Action: " + solution[time][1]);
        }
        return new ExecutionInfo<>(totalConflicts, firstConflict, currentState);
    }

    public static void evaluateCostOfSubGoals(HashMap<Character, List<SubGoal>> agentSubGoals, State initialState, int[][][][] realDistanceGrid) {
        // Loop over each agent to evaluate their subGoals
        for (Character agent : agentSubGoals.keySet()) {
            List<SubGoal> subGoals = new ArrayList<>(agentSubGoals.get(agent));
            // Remove subGoals without boxes and update their costs
            subGoals.removeIf(GraphSearch::removeAndSetHighCost);

            Stack<SubGoal> assignedSubGoals = new Stack<>();

            // Evaluate and update the cost for remaining subGoals
            while (!subGoals.isEmpty()) {
                SubGoal nextSubGoal = findNextSubGoal(subGoals, assignedSubGoals, initialState, agent, realDistanceGrid);
                // Update the cost of the chosen subGoal and move it to the assigned stack
                subGoals.remove(nextSubGoal);
                assignedSubGoals.push(nextSubGoal);
            }
        }
    }
    private static boolean removeAndSetHighCost(SubGoal subGoal) {
        if (subGoal.box == null) {
            subGoal.updateCost(10000);
            return true;
        }
        return false;
    }
    private static SubGoal findNextSubGoal(List<SubGoal> subGoals, Stack<SubGoal> assignedSubGoals, State initialState, Character agent, int[][][][] realDistanceGrid) {
        List<State.Pair<Integer, SubGoal>> distances = new ArrayList<>();

        if (assignedSubGoals.isEmpty()) {
            for (SubGoal subGoal : subGoals) {
                int initialDistance = calculateInitialDistance(subGoal, initialState, agent, realDistanceGrid);
                distances.add(new State.Pair<>(initialDistance + subGoal.getCost(), subGoal));
            }
        } else {
            for (SubGoal subGoal : subGoals) {
                SubGoal lastAssigned = assignedSubGoals.peek();
                int subsequentDistance = calculateSubsequentDistance(lastAssigned, subGoal, realDistanceGrid);
                distances.add(new State.Pair<>(lastAssigned.getCost() + subsequentDistance + subGoal.getCost(), subGoal));
            }
        }

        // Sort the distances and return the subGoal with the minimal distance
        distances.sort(Comparator.comparingInt(o -> o.first));
        SubGoal selectedSubGoal = distances.getFirst().second;
        selectedSubGoal.updateCost(distances.getFirst().first);
        return selectedSubGoal;
    }
    private static int calculateInitialDistance(SubGoal subGoal, State initialState, Character agent, int[][][][] realDistanceGrid) {
        return realDistanceGrid[initialState.agentRows[agent - '0']][initialState.agentCols[agent - '0']][(int) subGoal.box.getX()][(int) subGoal.box.getY()];
    }
    private static int calculateSubsequentDistance(SubGoal lastAssigned, SubGoal current, int[][][][] realDistanceGrid) {
        return realDistanceGrid[(int) lastAssigned.goal.getX()][(int) lastAssigned.goal.getY()][(int) current.box.getX()][(int) current.box.getY()];
    }


    //Run Floyd-Warshall on map
    private static int[][][][] preComputeRealDistance(State initialState) {
        int N = initialState.walls.length;
        int M = initialState.walls[0].length;
        int[][][][] distanceGrid = new int[N][M][N][M];

        // Initialize distances
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < M; col++) {
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < M; j++) {
                        distanceGrid[row][col][i][j] = (i == row && j == col) ? 0 : Integer.MAX_VALUE;
                    }
                }
            }
        }

        // Set direct neighbors
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < M; col++) {
                if (!initialState.walls[row][col]) {
                    int[] dRow = {-1, 1, 0, 0};
                    int[] dCol = {0, 0, -1, 1};
                    for (int d = 0; d < 4; d++) {
                        int ni = row + dRow[d];
                        int nj = col + dCol[d];
                        if (ni >= 0 && ni < N && nj >= 0 && nj < M && !initialState.walls[ni][nj]) {
                            distanceGrid[row][col][ni][nj] = 1;
                        }
                    }
                }
            }
        }

        // Apply the parallel Floyd-Warshall algorithm
        parallelFloydWarshall(distanceGrid, N, M, initialState);

        return distanceGrid;
    }

    private static void parallelFloydWarshall(int[][][][] distanceGrid, int N, int M, State initialState) {
        IntStream.range(0, N).parallel().forEach(k -> {
            for (int l = 0; l < M; l++) {
                if (!initialState.walls[k][l]) {  // Ensure not to compute through walls
                    for (int i = 0; i < N; i++) {
                        for (int j = 0; j < M; j++) {
                            for (int m = 0; m < N; m++) {
                                for (int n = 0; n < M; n++) {
                                    if (distanceGrid[i][j][k][l] != Integer.MAX_VALUE && distanceGrid[k][l][m][n] != Integer.MAX_VALUE) {
                                        int newDist = distanceGrid[i][j][k][l] + distanceGrid[k][l][m][n];
                                        if (distanceGrid[i][j][m][n] > newDist) {
                                            distanceGrid[i][j][m][n] = newDist;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
    }
    public static State[] splitStates(State initialState, HashMap<Character, List<SubGoal>> subGoals) {
        int numAgents = initialState.agentRows.length;
        State[] states = new State[numAgents]; // Array to hold the state for each agent

        // Loop through each agent to create an individual state
        for (int agent = 0; agent < numAgents; agent++) {
            // Initialize parameters for the individual agent's state
            int[] agentRow = { initialState.agentRows[agent] };
            int[] agentCol = { initialState.agentCols[agent] };
            Color[] agentColor = { initialState.agentColors[agent] };
            char[][] goals = new char[initialState.goals.length][initialState.goals[0].length];
            char[][] boxes = new char[initialState.boxes.length][initialState.boxes[0].length];

            // Handle subGoals specifically assigned to this agent
            handleAgentSpecificSubGoals(subGoals, agent, initialState, boxes, goals);

            // Populate remaining boxes based on agent's color if no specific subGoals
            populateBoxesByAgentColor(agent, initialState, subGoals, boxes);

            // Assemble the new state for this agent
            states[agent] = new State(agentRow, agentCol, agentColor, initialState.walls, boxes, State.boxColors, goals);
        }

        return states;
    }
    private static void handleAgentSpecificSubGoals(HashMap<Character, List<SubGoal>> subGoals, int agent, State initialState, char[][] boxes, char[][] goals) {
        char agentChar = (char) (agent + '0');
        if (subGoals.containsKey(agentChar)) {
            for (SubGoal subGoal : subGoals.get(agentChar)) {
                if (subGoal.box != null) {
                    int boxX = (int) subGoal.box.getX();
                    int boxY = (int) subGoal.box.getY();
                    boxes[boxX][boxY] = initialState.boxes[boxX][boxY];
                    if (subGoal.goal != null) {
                        goals[(int) subGoal.goal.getX()][(int) subGoal.goal.getY()] = initialState.goals[(int) subGoal.goal.getX()][(int) subGoal.goal.getY()];
                    }
                } else if (subGoal.goal != null) {
                    goals[(int) subGoal.goal.getX()][(int) subGoal.goal.getY()] = '0';  // Set to '0' if goal exists without a corresponding box
                }
            }
        }
    }
    private static void populateBoxesByAgentColor(int agent, State initialState, HashMap<Character, List<SubGoal>> subGoals, char[][] boxes) {
        if (!subGoals.containsKey((char) (agent + '0'))) {  // Check if there are no specific subGoals for this agent
            for (int row = 0; row < initialState.boxes.length; row++) {
                for (int col = 0; col < initialState.boxes[row].length; col++) {
                    char box = initialState.boxes[row][col];
                    if (box >= 'A' && box <= 'Z' && State.boxColors[box - 'A'] == initialState.agentColors[agent]) {
                        boxes[row][col] = box;  // Assign box to this agent's state if the color matches
                    }
                }
            }
        }
    }

    public static Action[] getAgentPlan(Action[][] plan){
        Action[] out = new Action[plan.length];
        for(int i=0; i<plan.length; i++){
            out[i] = plan[i][0];
        }
        return out;
    }


    public static class Path {

        public HashMap<Character,List<SubGoal>> path(State initialState, int[][][][] realDistanceGrid)
        {
            //System.err.println("RUn PLANENER.....");
            HashMap<Character,List<SubGoal>> subGoalsByBoxType = getSubGoalsByBoxType(initialState,realDistanceGrid);
            HashMap<Color,List<SubGoal>> subGoalsByColor = getGoalByColor(subGoalsByBoxType);
            HashMap<Color,List<Character>> agentsByColor = getAgentByColor(initialState);

            /** Worst case scenario one agent needs to do all tasks
             *  Therefore we need a n_agents X n_task square matrix
             *  We only care about the distances of the agents to boxes
             *  the rest will be dummies (high values)
             */

            /** Initiate a graph for each color */
            HashMap<Color,double[][]> gridByColor = new HashMap<>();
            for(Color color: agentsByColor.keySet()){
                if(subGoalsByColor.containsKey(color)){
                    gridByColor.put(color, new double[agentsByColor.get(color).size()*subGoalsByColor.get(color).size()][agentsByColor.get(color).size()*subGoalsByColor.get(color).size()]);
                    for (double[] row: gridByColor.get(color)){
                        Arrays.fill(row, 10000);
                    }
                }
            }

            /** Compute realDistances */
            for(Color color: gridByColor.keySet()){
                int agentSize = agentsByColor.get(color).size();
                int count = 0;
                for(Character agent: agentsByColor.get(color)){
                    //System.err.println("Syste agent : " + agent);
                    int subGoalSize = subGoalsByColor.get(color).size();
                    double distance;
                    for(int taskId=0; taskId<subGoalSize;taskId++){
                        SubGoal subGoal = subGoalsByColor.get(color).get(taskId);
                        if(subGoal.goal!=null){
                            distance = realDistanceGrid[initialState.agentRows[agent-'0']][initialState.agentCols[agent-'0']][(int)subGoal.box.getX()][(int)subGoal.box.getY()]
                                    + realDistanceGrid[(int)subGoal.box.getX()][(int)subGoal.box.getY()][(int)subGoal.goal.getX()][(int)subGoal.goal.getY()];
                            //System.err.println("Must me in here...: " + distance);
                        } else {
                            distance = realDistanceGrid[initialState.agentRows[agent-'0']][initialState.agentCols[agent-'0']][(int)subGoal.box.getX()][(int)subGoal.box.getY()];
                        }
                        for(int i=count; i < agentSize*subGoalSize; i=i+agentSize){
                            gridByColor.get(color)[i][taskId] = distance;
                        }
                    }
                    count++;
                }
            }


            //System.err.println("Hunagarian.....");
            /** Run Hangarian Algorithm to get the optimal assingment of the tasks
             * based on the initial state of the agents
             */
            HashMap<Character,List<SubGoal>> subGoals = new HashMap<>();
            for(Color color: gridByColor.keySet()){
                //System.err.println("Color: " + color.toString() + " .... " );
                int subGoalSize = subGoalsByColor.get(color).size();
                int agentSize = agentsByColor.get(color).size();
                // for(int row = 0; row <agentSize*subGoalSize;row++){
                //     for(int col=0;col<agentSize*subGoalSize;col++){
                //         System.err.print(gridByColor.get(color)[row][col]+"|");
                //     }
                //     System.err.println();
                // }
                MinimumCostFlow matchingAlgorithm = new MinimumCostFlow(gridByColor.get(color));
                int[] result = matchingAlgorithm.solve();
                //System.err.println(Arrays.toString(result));
                for(int i=0; i<result.length;i++)
                    if (result[i] < subGoalSize) {
                        int agent =  i % agentSize;
                        if(subGoals.containsKey(agentsByColor.get(color).get(agent))){
                            subGoals.get(agentsByColor.get(color).get(agent)).add(subGoalsByColor.get(color).get(result[i]));
                        } else{
                            subGoals.put(agentsByColor.get(color).get(agent), new ArrayList<>());
                            subGoals.get(agentsByColor.get(color).get(agent)).add(subGoalsByColor.get(color).get(result[i]));
                        }
                        //System.err.println("Agent: " + agentsByColor.get(color).get(agent) + " SubGoal: " + subGoalsByColor.get(color).get(result[i]));
                    }
            }

            //Check if the agents has also a goal
            HashMap<Character,HashSet<Point>> goals = initialState.getGoals();
            for(int agent=0; agent<initialState.agentRows.length;agent++){
                if(goals.containsKey((char)(agent+'0'))){
                    //System.err.println("eeee: " + agent);
                    int size = 0;
                    if(subGoals.containsKey((char)(agent+'0'))){
                        size = subGoals.get((char)(agent+'0')).size();
                    } else {
                        subGoals.put((char)(agent+'0'), new ArrayList<>());
                    }
                    Point goal = goals.get((char)(agent+'0')).iterator().next();
                    int distance = realDistanceGrid[initialState.agentRows[agent]][initialState.agentCols[agent]][(int)goal.getX()][(int)goal.getY()];
                    //System.err.println("#From agent" + " to " + goal.toString() + " is " + distance);
                    subGoals.get((char)(agent+'0')).add(size, new SubGoal(null, goal,distance));
                }
            }


            return subGoals;

        }

        public HashMap<Character,List<SubGoal>> getSubGoalsByBoxType(State initialState, int[][][][] realDistanceGrid){
            HashMap<Character,List<SubGoal>> subGoalsByBoxType = new HashMap<>();
            HashMap<Character,HashSet<Point>> boxes = initialState.getBoxesCoord();
            HashMap<Character,HashSet<Point>> goals = initialState.getGoals();
            HashMap<Character,State.Pair<double[][],SubGoal[][]>> taskByBoxType = getDistanceOfBoxesToGoals(realDistanceGrid,boxes,goals);
            for(Character boxType: taskByBoxType.keySet()){
                if(!subGoalsByBoxType.containsKey(boxType)){
                    subGoalsByBoxType.put(boxType, new ArrayList<>());
                }
                MinimumCostFlow mathcingAlgorithm = new MinimumCostFlow(taskByBoxType.get(boxType).first);
                int[] result = mathcingAlgorithm.solve();
                // System.err.println(Arrays.toString(result));
                // System.err.println(Arrays.deepToString(taskByBoxType.get(boxType).first));
                // System.err.println(Arrays.deepToString(taskByBoxType.get(boxType).second));
                for(int i=0; i<result.length; i++){

                    subGoalsByBoxType.get(boxType).add(i, taskByBoxType.get(boxType).second[i][result[i]]);
                }
            }

            //Check for boxes without even a single goal
            for(Character boxType: boxes.keySet()){
                if(!goals.containsKey(boxType)){
                    for(Point point: boxes.get(boxType)){
                        if(!subGoalsByBoxType.containsKey(boxType)){
                            subGoalsByBoxType.put(boxType, new ArrayList<>());
                        }
                        subGoalsByBoxType.get(boxType).add(new SubGoal(point, null));
                    }
                }
            }

            return subGoalsByBoxType;
        }

        public HashMap<Character, State.Pair<double[][], SubGoal[][]>> getDistanceOfBoxesToGoals(int[][][][] realDistanceGrid, HashMap<Character, HashSet<Point>> boxes, HashMap<Character, HashSet<Point>> goals) {
            HashMap<Character, State.Pair<double[][], SubGoal[][]>> mathHash = new HashMap<>();
            // Iterate over all goal types that are uppercase letters (box goals).
            goals.keySet().stream()
                    .filter(goal -> 'A' <= goal && goal <= 'Z')
                    .forEach(goal -> {
                        int size = Math.max(goals.get(goal).size(), boxes.get(goal).size());
                        double[][] distanceGraph = new double[size][size];
                        SubGoal[][] goalMap = new SubGoal[size][size];

                        initializeMatrix(distanceGraph); // Initialize with a high value
                        initializeSubGoalMatrix(goalMap, boxes.get(goal)); // Initialize SubGoals with a high default cost

                        updateMatrices(realDistanceGrid, goal, boxes, goals, distanceGraph, goalMap);

                        mathHash.put(goal, new State.Pair<>(distanceGraph, goalMap));
                    });

            return mathHash;
        }

        private void initializeMatrix(double[][] matrix) {
            for (double[] row : matrix) {
                Arrays.fill(row, 1000.0);
            }
        }

        private void initializeSubGoalMatrix(SubGoal[][] matrix, HashSet<Point> points) {
            int i = 0;
            for (Point point : points) {
                for (int j = 0; j < matrix.length; j++) {
                    matrix[j][i] = new SubGoal(point, null, 10000);
                }
                i++;
            }
        }

        private void updateMatrices(int[][][][] realDistanceGrid, char goal, HashMap<Character, HashSet<Point>> boxes, HashMap<Character, HashSet<Point>> goals, double[][] distanceGraph, SubGoal[][] goalMap) {
            int i = 0;
            for (Point boxPoint : boxes.get(goal)) {
                int j = 0;
                for (Point goalPoint : goals.get(goal)) {
                    double distance = realDistanceGrid[(int) boxPoint.getX()][(int) boxPoint.getY()][(int) goalPoint.getX()][(int) goalPoint.getY()];
                    distanceGraph[j][i] = distance;
                    goalMap[j][i] = new SubGoal(boxPoint, goalPoint, (int) distance);
                    j++;
                }
                i++;
            }
        }

        public HashMap<Color,List<SubGoal>> getGoalByColor(HashMap<Character,List<SubGoal>> subGoalsByBoxType) {
            HashMap<Color,List<SubGoal>> subGoalsByColor = new HashMap<>();
            subGoalsByBoxType.forEach((boxType, subGoals) -> {
                Color color = State.boxColors[boxType - 'A'];
                subGoalsByColor.computeIfAbsent(color, k -> new ArrayList<>()).addAll(subGoals);
            });
            return subGoalsByColor;
        }
        public HashMap<Character, List<Point>> removeAndGetUnassigned(HashMap<Character, List<SubGoal>> subGoals) {
            HashMap<Character, List<Point>> unassignedByAgent = new HashMap<>();
            subGoals.forEach((agent, subGoalList) -> {
                List<Point> unassignedPoints = subGoalList.stream()
                        .filter(subGoal -> subGoal.goal == null)
                        .map(subGoal -> subGoal.box)
                        .collect(Collectors.toList());
                unassignedByAgent.put(agent, unassignedPoints);
                subGoalList.removeIf(subGoal -> subGoal.goal == null);
            });
            return unassignedByAgent;
        }
        public HashMap<Color, List<Character>> getAgentByColor(State initialState) {
            HashMap<Color, List<Character>> agentsByColor = new HashMap<>();
            for (int agent = 0; agent < initialState.agentRows.length; agent++) {
                Color color = initialState.agentColors[agent];
                agentsByColor.computeIfAbsent(color, k -> new ArrayList<>()).add((char) (agent + '0'));
            }
            return agentsByColor;
        }

        public static class MinimumCostFlow {

            private final int rows, cols, dim;
            private final double[][] costMatrix;
            private final double[] labelWorker, labelJob;
            private final int[] DeltaWorker, DeltaJob, parentWorkerJob;
            private final boolean[] Workerassigned;

            private final PriorityQueue<MinimumCostFlow.Slack> slackQueue;
            private static class Slack {
                int jobIndex;
                double value;
                boolean isValid;


                Slack(int jobIndex, double value) {
                    this.jobIndex = jobIndex;
                    this.value = value;

                }
            }

            public MinimumCostFlow(double[][] inputCostMatrix) {
                this.dim = Math.max(inputCostMatrix.length, inputCostMatrix[0].length);
                this.rows = inputCostMatrix.length;
                this.cols = inputCostMatrix[0].length;
                this.costMatrix = new double[this.dim][this.dim];

                for (int i = 0; i < this.dim; i++) {
                    Arrays.fill(this.costMatrix[i], Double.POSITIVE_INFINITY);
                    double minVal = Double.POSITIVE_INFINITY;
                    for (int j = 0; j < inputCostMatrix[i].length; j++) {
                        this.costMatrix[i][j] = inputCostMatrix[i][j];
                        if (inputCostMatrix[i][j] < minVal) {
                            minVal = inputCostMatrix[i][j];
                        }
                    }
                    // Subtract the minimum value from each element in the row
                    for (int j = 0; j < this.dim; j++) {
                        if (this.costMatrix[i][j] < Double.POSITIVE_INFINITY) {
                            this.costMatrix[i][j] -= minVal;
                        }
                    }
                }

                labelWorker = new double[this.dim];
                labelJob = new double[this.dim];
                Workerassigned = new boolean[this.dim];
                parentWorkerJob = new int[this.dim];
                DeltaWorker = new int[this.dim];
                Arrays.fill(DeltaWorker, -1);
                DeltaJob = new int[this.dim];
                Arrays.fill(DeltaJob, -1);
                slackQueue = new PriorityQueue<>(Comparator.comparingDouble(s -> s.value));
            }

            protected void executemincost() {
                while (!slackQueue.isEmpty()) {
                    MinimumCostFlow.Slack minSlack = slackQueue.poll();
                    if (!minSlack.isValid) continue; // Skip invalid slacks

                    if (minSlack.value > 0) {
                        updateLabeling(minSlack.value);
                    }

                    int minSlackJob = minSlack.jobIndex;
                    if (DeltaJob[minSlackJob] == -1) {
                        augmentPath(minSlackJob);
                        return;
                    } else {
                        expandWorkerassigned(minSlackJob);
                    }
                }
            }

            private void augmentPath(int minSlackJob) {
                int committedJob = minSlackJob;
                int parentWorker = parentWorkerJob[committedJob];
                while (true) {
                    int temp = DeltaWorker[parentWorker];
                    match(parentWorker, committedJob);
                    committedJob = temp;
                    if (committedJob == -1) {
                        break;
                    }
                    parentWorker = parentWorkerJob[committedJob];
                }
            }

            private void expandWorkerassigned(int minSlackJob) {
                int worker = DeltaWorker[minSlackJob];
                Workerassigned[worker] = true;
                for (int j = 0; j < dim; j++) {
                    if (parentWorkerJob[j] == -1) {
                        double slack = costMatrix[worker][j] - labelWorker[worker] - labelJob[j];
                        slackQueue.add(new MinimumCostFlow.Slack(j, slack)); // Add slack information to the queue
                    }
                }
            }
            protected void computeInitialFeasibleSolution() {
                // Set initial labels for workers
                for (int j = 0; j < dim; j++) {
                    labelJob[j] = Double.POSITIVE_INFINITY;
                }
                for (int w = 0; w < dim; w++) {
                    for (int j = 0; j < dim; j++) {
                        if (costMatrix[w][j] < labelJob[j]) {
                            labelJob[j] = costMatrix[w][j];
                        }
                    }
                }

                // Initialize labels for jobs
                for (int j = 0; j < dim; j++) {
                    for (int w = 0; w < dim; w++) {
                        if (costMatrix[w][j] - labelJob[j] < labelWorker[w]) {
                            labelWorker[w] = costMatrix[w][j] - labelJob[j];
                        }
                    }
                }
            }
            public int[] solve() {
                computeInitialFeasibleSolution();
                greedyMatch();
                int w = fetchUnmatchedWorker();
                while (w < dim) {
                    initializePhase(w);
                    executemincost();
                    w = fetchUnmatchedWorker();
                }
                int[] result = Arrays.copyOf(DeltaJob, rows);
                adjustResult(result);
                return result;
            }

            private void adjustResult(int[] result) {
                for (int w = 0; w < result.length; w++) {
                    if (result[w] >= cols) {
                        result[w] = -1;
                    }
                }
            }


            protected int fetchUnmatchedWorker()
            {
                int w;
                for (w = 0; w < dim; w++)
                {
                    if (DeltaWorker[w] == -1)
                    {
                        break;
                    }
                }
                return w;
            }

            protected void greedyMatch()
            {
                for (int w = 0; w < dim; w++)
                {
                    for (int j = 0; j < dim; j++)
                    {
                        if (DeltaWorker[w] == -1
                                && DeltaJob[j] == -1
                                && costMatrix[w][j] - labelWorker[w] - labelJob[j] == 0)
                        {
                            match(w, j);
                        }
                    }
                }
            }

            private void initializePhase(int worker) {
                Arrays.fill(Workerassigned, false);
                Arrays.fill(parentWorkerJob, -1);
                Workerassigned[worker] = true;

                for (int j = 0; j < dim; j++) {
                    double slack = costMatrix[worker][j] - labelWorker[worker] - labelJob[j];
                    slackQueue.add(new MinimumCostFlow.Slack(j, slack)); // Add slack information to the queue
                }
            }
            protected void match(int w, int j)
            {
                DeltaWorker[w] = j;
                DeltaJob[j] = w;
            }


            protected void updateLabeling(double slack) {
                for (int w = 0; w < dim; w++) {
                    if (Workerassigned[w]) {
                        labelWorker[w] += slack;
                    }
                }

                for (int j = 0; j < dim; j++) {
                    if (parentWorkerJob[j] != -1) {
                        labelJob[j] -= slack;
                    } else {
                        slackQueue.add(new MinimumCostFlow.Slack(j, slack)); // Add slack information to the queue
                    }
                }
            }
        }


    }

}
