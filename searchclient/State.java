package searchclient;

import java.util.*;
import java.awt.Point;

public class State
{
    private static final Random RNG = new Random(1);

    /*
        The agent rows, columns, and colors are indexed by the agent number.
        For example, this.agentRows[0] is the row location of agent '0'.
    */
    public int[] agentRows;
    public int[] agentCols;
    public Color[] agentColors;

    /*
        The walls, boxes, and goals arrays are indexed from the top-left of the level, row-major order (row, col).
               Col 0  Col 1  Col 2  Col 3
        Row 0: (0,0)  (0,1)  (0,2)  (0,3)  ...
        Row 1: (1,0)  (1,1)  (1,2)  (1,3)  ...
        Row 2: (2,0)  (2,1)  (2,2)  (2,3)  ...
        ...

        For example, this.walls[2] is an array of booleans for the third row.
        this.walls[row][col] is true if there's a wall at (row, col).

        this.boxes and this.char are two-dimensional arrays of chars.
        this.boxes[1][2]='A' means there is an A box at (1,2).
        If there is no box at (1,2), we have this.boxes[1][2]=0 (null character).
        Simiarly for goals.

    */
    public boolean[][] walls;
    public char[][] boxes,goals;
    /*
        The box colors are indexed alphabetically. So this.boxColors[0] is the color of A boxes,
        this.boxColor[1] is the color of B boxes, etc.
    */
    public static Color[] boxColors;

    public final State parent;
    private int hash = 0;
    public final Action[] jointAction;
    public final int g;
    public HashMap<Point,Point> BoxPoint,PointBox;
    public Manage manage;


    // Constructs an initial state.
    // Arguments are not copied, and therefore should not be modified after being passed in.
    public State(int[] agentRows, int[] agentCols, Color[] agentColors, boolean[][] walls,
                 char[][] boxes, Color[] boxColors, char[][] goals
    )
    {
        this.agentRows = agentRows;
        this.agentCols = agentCols;
        this.agentColors = agentColors;
        this.walls = walls;
        this.boxes = boxes;
        State.boxColors = boxColors;
        this.goals = goals;
        this.parent = null;
        this.jointAction = null;
        this.g = 0;
        this.fillBoxesThatCanNotBeMoved();
        //Initiate supportive structures to store boxes. It is needed by the heristic.
        HashMap<Character,HashSet<Point>> bxs = getBoxesCoord();
        BoxPoint = new HashMap<>();
        PointBox = new HashMap<>();
        for(Character boxType: bxs.keySet()){
            for(Point box: bxs.get(boxType)){
                BoxPoint.put(box,box);
                PointBox.put(box,box);
                //System.err.println("Box " + box.toString() + " is in " + BoxPoint.get(box).toString());
            }
        }
        getNumOfBoxesWithSameColor();
        this.manage = new Manage();
    }


    public State(State parent, Action[] jointAction)
    {
        // Copy parent
        this.agentRows = Arrays.copyOf(parent.agentRows, parent.agentRows.length);
        this.agentCols = Arrays.copyOf(parent.agentCols, parent.agentCols.length);
        this.boxes = new char[parent.boxes.length][];
        for (int i = 0; i < parent.boxes.length; i++)
        {
            this.boxes[i] = Arrays.copyOf(parent.boxes[i], parent.boxes[i].length);
        }
        this.goals = new char[parent.goals.length][];
        for (int i = 0; i < parent.goals.length; i++)
        {
            this.goals[i] = Arrays.copyOf(parent.goals[i], parent.goals[i].length);
        }
        this.walls = new boolean[parent.walls.length][];
        for (int i = 0; i < parent.walls.length; i++)
        {
            this.walls[i] = Arrays.copyOf(parent.walls[i], parent.walls[i].length);
        }
        this.agentColors = Arrays.copyOf(parent.agentColors,parent.agentColors.length);
        this.BoxPoint = new HashMap<>();
        for(Point boxInitPos: parent.BoxPoint.keySet()){
            this.BoxPoint.put(boxInitPos, parent.BoxPoint.get(boxInitPos));
        }
        PointBox = new HashMap<>();
        for(Point boxCurrPos: parent.PointBox.keySet()){
            this.PointBox.put(boxCurrPos, parent.PointBox.get(boxCurrPos));
        }

        // Set own parameters
        this.parent = parent;
        this.jointAction = Arrays.copyOf(jointAction, jointAction.length);
        this.g = parent.g + 1;
        this.manage = parent.manage;

        // Apply each action
        int numAgents = this.agentRows.length;
        for (int agent = 0; agent < numAgents; ++agent)
        {
            Action action = jointAction[agent];
            Point boxId;

            switch (action.type)
            {
                case NoOp:
                    break;

                case Move:
                    this.agentRows[agent] += action.agentRowDelta;
                    this.agentCols[agent] += action.agentColDelta;
                    break;

                case Push:
                    this.agentRows[agent] += action.agentRowDelta;
                    this.agentCols[agent] += action.agentColDelta;
                    this.boxes[this.agentRows[agent] + action.boxRowDelta][this.agentCols[agent] + action.boxColDelta] = this.boxes[this.agentRows[agent]][this.agentCols[agent]];
                    this.boxes[this.agentRows[agent]][this.agentCols[agent]] = 0;

                    //Get box's point-id from the HaspMap which has the current location as the key
                    boxId = this.PointBox.get(new Point(this.agentRows[agent], this.agentCols[agent]));
                    //Update the location in the HaspMap which has the point-id as the key
                    this.BoxPoint.put(boxId, new Point(this.agentRows[agent] + action.boxRowDelta, this.agentCols[agent] + action.boxColDelta));
                    //Update the HaspMap which has the current location as the key
                    this.PointBox.remove(new Point(this.agentRows[agent], this.agentCols[agent]));
                    this.PointBox.put(new Point(this.agentRows[agent] + action.boxRowDelta, this.agentCols[agent] + action.boxColDelta),boxId);
                    break;
                case Pull:
                    //Get box's point-id from the HaspMap which has the current location as the key
                    boxId = this.PointBox.get(new Point(this.agentRows[agent]-action.boxRowDelta, this.agentCols[agent]-action.boxColDelta));
                    //Update the location in the HaspMap which has the point-id as the key
                    this.BoxPoint.put(boxId, new Point(this.agentRows[agent], this.agentCols[agent]));
                    //Update the HaspMap which has the current location as the key
                    this.PointBox.remove(new Point(this.agentRows[agent]-action.boxRowDelta, this.agentCols[agent]-action.boxColDelta));
                    this.PointBox.put(new Point(this.agentRows[agent], this.agentCols[agent]),boxId);

                    //The box is in agent's place minus box-movement and it will be moved in agents place.
                    this.boxes[this.agentRows[agent]][this.agentCols[agent]] = this.boxes[this.agentRows[agent]-action.boxRowDelta][this.agentCols[agent]-action.boxColDelta];
                    this.boxes[this.agentRows[agent]-action.boxRowDelta][this.agentCols[agent]-action.boxColDelta] = 0;
                    this.agentRows[agent] += action.agentRowDelta;
                    this.agentCols[agent] += action.agentColDelta;
                    break;
            }
        }
    }
    public int g()
    {
        return this.g;
    }

    public State(State other) {
        this.agentRows = Arrays.copyOf(other.agentRows, other.agentRows.length);
        this.agentCols = Arrays.copyOf(other.agentCols, other.agentCols.length);
        this.agentColors = Arrays.copyOf(other.agentColors, other.agentColors.length);

        this.walls = new boolean[other.walls.length][];
        for (int i = 0; i < other.walls.length; i++) {
            this.walls[i] = Arrays.copyOf(other.walls[i], other.walls[i].length);
        }

        this.boxes = new char[other.boxes.length][];
        for (int i = 0; i < other.boxes.length; i++) {
            this.boxes[i] = Arrays.copyOf(other.boxes[i], other.boxes[i].length);
        }

        this.goals = new char[other.goals.length][];
        for (int i = 0; i < other.goals.length; i++) {
            this.goals[i] = Arrays.copyOf(other.goals[i], other.goals[i].length);
        }

        this.BoxPoint = new HashMap<>(other.BoxPoint);
        this.PointBox = new HashMap<>(other.PointBox);
        this.manage = new Manage(other.manage); // Assuming Manage has a copy constructor

        // These are immutable in the context of a single state transition, so shared references are safe
        this.parent = other.parent;
        this.jointAction = other.jointAction; // Typically null for a new state that's a copy
        this.g = other.g;
    }
    public boolean isGoalStateLastSubGoal() {
        // Check for any late-stage conflict in managing agents
        if (!this.manage.isEmpty()) {
            for (Character agent : this.manage.constrains.keySet()) {
                if (this.g <= this.manage.getMaxTimeFrame(agent)) {
                    return false;
                }
            }
        }
        // Check for alignment of goals with boxes and agents
        for (int row = 1; row < this.goals.length - 1; row++) {
            for (int col = 1; col < this.goals[row].length - 1; col++) {
                char goal = this.goals[row][col];
                if (isGoalMismatch(goal, row, col)) {
                    return false;
                }
            }
        }
        return true;
    }
    private boolean isGoalMismatch(char goal, int row, int col) {
        if ('A' <= goal && goal <= 'Z') {
            return this.boxes[row][col] != goal;
        } else if ('0' <= goal && goal <= '9') {
            return !(this.agentRows[goal - '0'] == row && this.agentCols[goal - '0'] == col);
        }
        return false;
    }
    public boolean isSubGoalState(SubGoal subGoal) {
        // Check if the agent is constrained at the current time and position
        if (this.manage.isAgentConstrained('0', this.g, new Point(agentRows[0], agentCols[0]))) {
            return false;
        }

        // Handle cases based on whether a box is associated with the subGoal
        if (subGoal.box == null) {
            return subGoal.goal.equals(new Point(this.agentRows[0], this.agentCols[0]));
        } else {
            Point currLocBox = this.BoxPoint.get(subGoal.box);
            return currLocBox != null && subGoal.goal.equals(currLocBox);
        }
    }

    public ArrayList<State> getExpandedStates()
    {
        int numAgents = this.agentRows.length;
        ArrayList<State> expandedStates = new ArrayList<>(16);

        // Determine list of applicable actions for each individual agent.
        Action[][] applicableActions = new Action[numAgents][];
        for (int agent = 0; agent < numAgents; ++agent)
        {
            ArrayList<Action> agentActions = new ArrayList<>(Action.values().length);
            for (Action action : Action.values())
            {
                if (this.isApplicable(agent, action))
                {
                    agentActions.add(action);
                }
            }
            //System.out.println(agentActions);
            if(agentActions.isEmpty()){
                return expandedStates;
            } else {
                applicableActions[agent] = agentActions.toArray(new Action[0]);
            }
        }

        // Iterate over joint actions, check conflict and generate child states.
        Action[] jointAction = new Action[numAgents];
        int[] actionsPermutation = new int[numAgents];
        while (true)
        {
            for (int agent = 0; agent < numAgents; ++agent)
            {
                jointAction[agent] = applicableActions[agent][actionsPermutation[agent]];
            }

            if (!this.isConflicting(jointAction))
            {
                expandedStates.add(new State(this, jointAction));
            }

            // Advance permutation
            boolean done = false;
            for (int agent = 0; agent < numAgents; ++agent)
            {
                if (actionsPermutation[agent] < applicableActions[agent].length - 1)
                {
                    ++actionsPermutation[agent];
                    break;
                }
                else
                {
                    actionsPermutation[agent] = 0;
                    if (agent == numAgents - 1)
                    {
                        done = true;
                    }
                }
            }

            // Last permutation?
            if (done)
            {
                break;
            }
        }

        Collections.shuffle(expandedStates, State.RNG);
        return expandedStates;
    }

    private boolean isApplicable(int agent, Action action)
    {
        int agentRow = this.agentRows[agent];
        int agentCol = this.agentCols[agent];
        Color agentColor = this.agentColors[agent];
        int boxRow;
        int boxCol;
        int destinationRowAgent;
        int destinationColAgent;
        int destinationRowBox;
        int destinationColBox;
        switch (action.type)
        {

            case NoOp:
                if(this.manage.constrains.containsKey((char)(agent+'0'))){
                    for(Constrain con: this.manage.constrains.get((char)(agent+'0'))){
                        if(Objects.equals(con.type, "box") && this.g+1 == con.timeframe){
                            if(!this.cellIsFree((int) con.point.getX(), (int) con.point.getY())){
                                return false;
                            }
                        }
                    }
                }
                return this.isNotContrained((char)(agent + '0'),agentRow, agentCol);

            case Move:
                if(this.manage.constrains.containsKey((char)(agent+'0'))){
                    for(Constrain con: this.manage.constrains.get((char)(agent+'0'))){
                        if(Objects.equals(con.type, "box") && this.g+1 == con.timeframe){
                            if(!this.cellIsFree((int) con.point.getX(), (int) con.point.getY())){
                                return false;
                            }
                        }
                    }
                }
                destinationRowAgent = agentRow + action.agentRowDelta;
                destinationColAgent = agentCol + action.agentColDelta;
                //System.out.println(this.isNotContrained((char)(agent + '0'),destinationRowAgent, destinationColAgent));
                return this.cellIsFree(destinationRowAgent, destinationColAgent) && this.isNotContrained((char)(agent + '0'),destinationRowAgent, destinationColAgent);
            case Push:
                destinationRowAgent = agentRow + action.agentRowDelta;
                destinationColAgent = agentCol + action.agentColDelta;
                destinationRowBox = destinationRowAgent + action.boxRowDelta;
                destinationColBox = destinationColAgent + action.boxColDelta;

                if(this.manage.constrains.containsKey((char)(agent+'0'))){
                    for(Constrain con: this.manage.constrains.get((char)(agent+'0'))){
                        if(con.type.equals("box") && this.g+1 == con.timeframe){
                            if((destinationRowBox == (int) con.point.getX() && destinationColBox == (int) con.point.getY() )){
                                return false;
                            }
                        }
                    }
                }

                return !this.walls[destinationRowAgent][destinationColAgent]
                        && this.cellIsFree(destinationRowBox, destinationColBox)
                        && this.isBoxOfColor(destinationRowAgent,destinationColAgent,agentColor)
                        && this.isNotContrained((char)(agent + '0'),destinationRowAgent, destinationColAgent);

            case Pull:
                destinationRowAgent = agentRow + action.agentRowDelta;
                destinationColAgent = agentCol + action.agentColDelta;
                destinationRowBox = agentRow;
                destinationColBox = agentCol;

                if(this.manage.constrains.containsKey((char)(agent+'0'))){
                    for(Constrain con: this.manage.constrains.get((char)(agent+'0'))){
                        if(con.type.equals("box") && this.g+1 == con.timeframe){
                            if((destinationRowBox == (int) con.point.getX() && destinationColBox == (int) con.point.getY() )){
                                return false;
                            }
                        }
                    }
                }

                boxRow = agentRow-action.boxRowDelta;
                boxCol = agentCol-action.boxColDelta;
                return this.cellIsFree(destinationRowAgent, destinationColAgent)
                        && this.isBoxOfColor(boxRow,boxCol,agentColor)
                        && this.isNotContrained((char)(agent + '0'),destinationRowAgent, destinationColAgent);
        }

        // Unreachable:
        return false;
    }

    public boolean isConflicting(Action[] jointAction)
    {
        int numAgents = this.agentRows.length;

        int[] destinationRows = new int[numAgents]; // row of new cell to become occupied by action
        int[] destinationCols = new int[numAgents]; // column of new cell to become occupied by action
        int[] boxRows = new int[numAgents]; // current row of box
        int[] boxCols = new int[numAgents]; // current column of box
        int[] destinationBoxRows = new int[numAgents]; // destination row of box moved by action
        int[] destinationBoxCols = new int[numAgents]; // destination column of box moved by action
        //Initialize box with negative values, different from each other.
        for(int i=-1000; i<numAgents-1000; i++){
            destinationBoxRows[i+1000] = i;
            destinationBoxCols[i+1000] = i;
            boxRows[i+1000] = -i;
            boxCols[i+1000] = -i;
        }

        // Collect cells to be occupied and boxes to be moved
        for (int agent = 0; agent < numAgents; ++agent)
        {
            Action action = jointAction[agent];
            int agentRow = this.agentRows[agent];
            int agentCol = this.agentCols[agent];

            switch (action.type)
            {
                case NoOp:
                    destinationRows[agent] = agentRow;
                    destinationCols[agent] = agentCol;
                    break;

                case Move:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    break;
                case Push:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    destinationBoxRows[agent] = destinationRows[agent] + action.boxRowDelta;
                    destinationBoxCols[agent] = destinationCols[agent] + action.boxColDelta;
                    boxRows[agent] = destinationRows[agent];
                    boxCols[agent] = destinationCols[agent];

                    break;
                case Pull:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    destinationBoxRows[agent] = agentRow;
                    destinationBoxCols[agent] = agentCol;
                    boxRows[agent] = destinationBoxRows[agent] - action.boxRowDelta;
                    boxCols[agent] = destinationBoxCols[agent] - action.boxColDelta;
                    break;
            }
        }

        for (int a1 = 0; a1 < numAgents; ++a1)
        {
            if (jointAction[a1] == Action.NoOp)
            {
                continue;
            }

            for (int a2 = a1 + 1; a2 < numAgents; ++a2)
            {
                if (jointAction[a2] == Action.NoOp)
                {
                    continue;
                }

                // Vertex conflict
                if ((destinationRows[a1] == destinationRows[a2] && destinationCols[a1] == destinationCols[a2]) ||
                        (destinationRows[a1] == destinationBoxRows[a2] && destinationCols[a1] == destinationBoxCols[a2]) ||
                        (destinationRows[a2] == destinationBoxRows[a1] && destinationCols[a2] == destinationBoxCols[a1]) ||
                        (destinationBoxRows[a1] == destinationBoxRows[a2] && destinationBoxCols[a1] == destinationBoxCols[a2])) {
                    return true;
                }
                // Edge conflict and Follow Conflict
                else if ((destinationRows[a1] == this.agentRows[a2] && destinationCols[a1] == this.agentCols[a2]) ||
                        (destinationRows[a2] == this.agentRows[a1] && destinationCols[a2] == this.agentCols[a1]) ||
                        (destinationBoxRows[a1] == boxRows[a2] && destinationBoxCols[a1] == boxCols[a2]) ||
                        (destinationBoxRows[a2] == boxRows[a1] && destinationBoxCols[a2] == boxCols[a1]) ||
                        (destinationRows[a1] == boxRows[a2] && destinationCols[a1] == boxCols[a2]) ||
                        (destinationRows[a2] == boxRows[a1] && destinationCols[a2] == boxCols[a1]) ||
                        (destinationBoxRows[a1] == this.agentRows[a2] && destinationBoxCols[a1] == this.agentCols[a2]) ||
                        (destinationBoxRows[a2] == this.agentRows[a1] && destinationBoxCols[a2] == this.agentCols[a1])) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean cellIsFree(int row, int col)
    {
        return !this.walls[row][col] && this.boxes[row][col] == 0 && this.agentAt(row, col) == 0;
    }


    private char agentAt(int row, int col)
    {
        for (int i = 0; i < this.agentRows.length; i++)
        {
            if (this.agentRows[i] == row && this.agentCols[i] == col)
            {
                return (char) ('0' + i);
            }
        }
        return 0;
    }

    public Action[][] extractPlan()
    {
        Action[][] plan = new Action[this.g][];
        State state = this;
        while (state.jointAction != null)
        {
            plan[state.g - 1] = state.jointAction;
            state = state.parent;
        }
        return plan;
    }

    private boolean isBoxOfColor(int row, int col, Color color){
        char box = this.boxes[row][col];
        //System.err.println("Box: " + box + " coords: " + row + " , " + col);
        return box >= 'A' && box <= 'Z' && boxColors[box - 'A'] == color;
    }

    public HashMap<Character, HashSet<Point>> getBoxesCoord() {
        HashMap<Character, HashSet<Point>> boxesCoord = new HashMap<>();
        for (int row = 1; row < this.boxes.length - 1; row++) {
            for (int col = 1; col < this.boxes[row].length - 1; col++) {
                char box = this.boxes[row][col];
                if ('A' <= box && box <= 'Z') {
                    boxesCoord.computeIfAbsent(box, k -> new HashSet<>()).add(new Point(row, col));
                }
            }
        }
        return boxesCoord;
    }

    public HashMap<Character, HashSet<Point>> getGoals() {
        HashMap<Character, HashSet<Point>> goals = new HashMap<>();

        for (int row = 1; row < this.goals.length - 1; row++) {
            for (int col = 1; col < this.goals[row].length - 1; col++) {
                char goal = this.goals[row][col];
                if (('A' <= goal && goal <= 'Z' && isExistAgentForBox(goal)) || ('0' <= goal && goal <= '9')) {
                    goals.computeIfAbsent(goal, k -> new HashSet<>()).add(new Point(row, col));
                }
            }
        }
        return goals;
    }
    private void fillBoxesThatCanNotBeMoved(){
        HashMap<Character,HashSet<Point>> boxes = this.getBoxesCoord();
        for (Character box : boxes.keySet() ){
            boolean exist = false;
            for (int a=0; a<this.agentRows.length;a++){
                if (boxColors[box-'A'] == this.agentColors[a]){
                    exist = true;
                    break;
                }
            }
            if (!exist){
                for (Point boxP : boxes.get(box)){
                    this.boxes[(int) boxP.getX()][(int) boxP.getY()]= 0;
                    this.walls[(int) boxP.getX()][(int) boxP.getY()] = true;
                }
            }
        }
        /** Uncomment to test it.*/
        // String wally;
        // for (int i = 0; i < this.walls.length; i++){
        //     for(int y = 0; y < this.walls[0].length; y++){
        //         if (this.walls[i][y]){
        //             wally = "wall";
        //         }
        //         else{
        //             wally = "blank";
        //         }
        //         System.err.print(wally);
        //     }
        //     System.err.println('\n');
        // }
    }


    public void setWall(SubGoal sub){
        this.boxes[(int) sub.goal.getX()][(int) sub.goal.getY()] = 0;
        this.walls[(int) sub.goal.getX()][(int) sub.goal.getY()] = true;
        this.goals[(int) sub.goal.getX()][(int) sub.goal.getY()] = 0;
    }


    public void copyConstrains(Manage conMg, Character agent){
        this.manage = new Manage();
        for(Constrain con: conMg.constrains.get(agent)){
            this.manage.append('0', con.timeframe , con.point, con.type);
        }
    }

    private boolean isExistAgentForBox(char box){
        boolean exist = false;
        for (int a=0; a<this.agentRows.length;a++){
            if (boxColors[box-'A'] == this.agentColors[a]){
                exist = true;
                break;
            }
        }
        return exist;
    }

    @Override
    public int hashCode()
    {
        if (this.hash == 0)
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(this.agentColors);
            result = prime * result + Arrays.hashCode(boxColors);
            result = prime * result + Arrays.deepHashCode(this.walls);
            result = prime * result + Arrays.deepHashCode(this.goals);
            result = prime * result + Arrays.hashCode(this.agentRows);
            result = prime * result + Arrays.hashCode(this.agentCols);
            for (int row = 0; row < this.boxes.length; ++row)
            {
                for (int col = 0; col < this.boxes[row].length; ++col)
                {
                    char c = this.boxes[row][col];
                    if (c != 0)
                    {
                        result = prime * result + (row * this.boxes[row].length + col) * c;
                    }
                }
            }
            if(this.agentRows.length == 1){ result = prime * result + this.g; }
            this.hash = result;
        }
        return this.hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (this.getClass() != obj.getClass())
        {
            return false;
        }
        State other = (State) obj;
        if(this.agentRows.length == 1){
            return Arrays.equals(this.agentRows, other.agentRows) &&
                    Arrays.equals(this.agentCols, other.agentCols) &&
                    Arrays.equals(this.agentColors, other.agentColors) &&
                    Arrays.deepEquals(this.walls, other.walls) &&
                    Arrays.deepEquals(this.boxes, other.boxes) &&
                    Arrays.equals(this.boxColors, boxColors) &&
                    Arrays.deepEquals(this.goals, other.goals)
                    && this.g == other.g
                    ;
        } else{
            return Arrays.equals(this.agentRows, other.agentRows) &&
                    Arrays.equals(this.agentCols, other.agentCols) &&
                    Arrays.equals(this.agentColors, other.agentColors) &&
                    Arrays.deepEquals(this.walls, other.walls) &&
                    Arrays.deepEquals(this.boxes, other.boxes) &&
                    Arrays.equals(this.boxColors, boxColors) &&
                    Arrays.deepEquals(this.goals, other.goals);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        for (int row = 0; row < this.walls.length; row++)
        {
            for (int col = 0; col < this.walls[row].length; col++)
            {
                if (this.boxes[row][col] > 0)
                {
                    s.append(this.boxes[row][col]);
                }
                else if (this.walls[row][col])
                {
                    s.append("+");
                }
                else if (this.agentAt(row, col) != 0)
                {
                    s.append(this.agentAt(row, col));
                }
                else
                {
                    s.append(" ");
                }
            }
            s.append("\n");
        }
        return s.toString();
    }

    public State.Pair<Integer,Conflict> returnFirstConflict(Action[] jointAction)
    {
        int numAgents = this.agentRows.length;

        int[] destinationRows = new int[numAgents]; // row of new cell to become occupied by action
        int[] destinationCols = new int[numAgents]; // column of new cell to become occupied by action
        int[] boxRows = new int[numAgents]; // current row of box
        int[] boxCols = new int[numAgents]; // current column of box
        int[] destinationBoxRows = new int[numAgents]; // destination row of box moved by action
        int[] destinationBoxCols = new int[numAgents]; // destination column of box moved by action

        //Initialize box with negative values, different from each other.
        for(int i=-100; i<numAgents-100; i++){
            destinationBoxRows[i+100] = i;
            destinationBoxCols[i+100] = i;
            boxRows[i+100] = -i;
            boxCols[i+100] = -i;
        }

        // Collect cells to be occupied and boxes to be moved
        for (int agent = 0; agent < numAgents; ++agent)
        {
            Action action = jointAction[agent];
            int agentRow = this.agentRows[agent];
            int agentCol = this.agentCols[agent];

            switch (action.type)
            {
                case NoOp:
                    destinationRows[agent] = agentRow;
                    destinationCols[agent] = agentCol;
                    break;

                case Move:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    break;
                case Push:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    destinationBoxRows[agent] = destinationRows[agent] + action.boxRowDelta;
                    destinationBoxCols[agent] = destinationCols[agent] + action.boxColDelta;
                    boxRows[agent] = destinationRows[agent];
                    boxCols[agent] = destinationCols[agent];

                    break;
                case Pull:
                    destinationRows[agent] = agentRow + action.agentRowDelta;
                    destinationCols[agent] = agentCol + action.agentColDelta;
                    destinationBoxRows[agent] = agentRow;
                    destinationBoxCols[agent] = agentCol;
                    boxRows[agent] = destinationBoxRows[agent] - action.boxRowDelta;
                    boxCols[agent] = destinationBoxCols[agent] - action.boxColDelta;
                    break;
            }
        }


//CHECK DA CONFUSED CHECK DA CONFLIKTEEED

        int counter = 0;
        Conflict first_Conflict = null;
        boolean first_time = true;
        for (int a1 = 0; a1 < numAgents; ++a1)
        {
            for (int a2 = a1 + 1; a2 < numAgents; ++a2)
            {
                /**
                 * There are three type of Conflicts: Vertex, Edge and Follow.
                 * We will create each conflict with respect of the constrain that
                 * we want to resolve it faster.
                 * First we find the conflicts between agents.
                 */
                // AGENT CONFLICTS!!!!
                //Vertex, edge, follow Conflict
                if ((destinationRows[a1] == destinationRows[a2] && destinationCols[a1] == destinationCols[a2]) ||
                        ((destinationRows[a1] == this.agentRows[a2] && destinationCols[a1] == this.agentCols[a2]) &&
                                (destinationRows[a2] == this.agentRows[a1] && destinationCols[a2] == this.agentCols[a1])) ||
                        ((destinationRows[a1] == this.agentRows[a2] && destinationCols[a1] == this.agentCols[a2]) ||
                                (destinationRows[a2] == this.agentRows[a1] && destinationCols[a2] == this.agentCols[a1]))){

                    if(first_time){
                        first_Conflict = new Conflict((char)(a1+'0'),(char)(a2+'0'),"agent","agent",this.g+1,this.g+1,new Point(destinationRows[a1],destinationCols[a1]),new Point(destinationRows[a2],destinationCols[a2]));
                        counter += 1;
                        first_time = false;
                    } else{
                        counter +=1;
                    }
                }
                /**
                 * Next find conflicts between boxes.
                 */
                //vertex edge follow
                else if ((destinationBoxRows[a1] == destinationBoxRows[a2] && destinationBoxCols[a1] == destinationBoxCols[a2]) ||
                        ((destinationBoxRows[a1] == boxRows[a2] && destinationBoxCols[a1] == boxCols[a2]) &&
                                (destinationBoxRows[a2] == boxRows[a1] && destinationBoxCols[a2] == boxCols[a1])) ||
                        ((destinationBoxRows[a1] == boxRows[a2] && destinationBoxCols[a1] == boxCols[a2]) ||
                                (destinationBoxRows[a2] == boxRows[a1] && destinationBoxCols[a2] == boxCols[a1]))){
                    if(first_time){
                        first_Conflict = new Conflict((char)(a1+'0'),(char)(a2+'0'),"agent","agent",this.g+1,this.g+1,new Point(destinationRows[a1],destinationCols[a1]),new Point(destinationRows[a2],destinationCols[a2]));
                        counter += 1;
                        first_time = false;
                    } else{
                        counter +=1;
                    }
                }
                /**
                 * Then, find conflicts between boxes and agents.
                 */
                //Vertex, edge, follow Conflict
                else if ((destinationBoxRows[a1] == destinationRows[a2] && destinationBoxCols[a1] == destinationCols[a2]) ||
                        (destinationBoxRows[a2] == destinationRows[a1] && destinationBoxCols[a2] == destinationCols[a1]) ||
                        (destinationBoxRows[a1] == this.agentRows[a2] && destinationBoxCols[a1] == this.agentCols[a2] &&
                                destinationRows[a2] == boxRows[a1] && destinationCols[a2] == boxCols[a1]) ||
                        (destinationBoxRows[a2] == this.agentRows[a1] && destinationBoxCols[a2] == this.agentCols[a1] &&
                                destinationRows[a1] == boxRows[a2] && destinationCols[a1] == boxCols[a2]) ||
                        (destinationBoxRows[a1] == this.agentRows[a2] && destinationBoxCols[a1] == this.agentCols[a2]) ||
                        (destinationRows[a2] == boxRows[a1] && destinationBoxCols[a2] == boxCols[a1]) ||
                        (destinationBoxRows[a2] == this.agentRows[a1] && destinationBoxCols[a2] == this.agentCols[a1]) ||
                        (destinationRows[a1] == boxRows[a2] && destinationBoxCols[a1] == boxCols[a2])){
                    //System.err.println("10");
                    //ConflicT ("FollowBoxAgent",a2,a1,timeframe)
                    //ConflicT ("FollowBoxAgent",a1,a2,timeframe)
                    if(first_time){
                        first_Conflict = new Conflict((char)(a1+'0'),(char)(a2+'0'),"agent","agent",this.g+1,this.g+1,new Point(destinationRows[a1],destinationCols[a1]),new Point(destinationRows[a2],destinationCols[a2]));
                        counter += 1;
                        first_time = false;
                    } else{
                        counter +=1;
                    }
                }
                /**
                 * Finally, check aplicability.
                 * We only care to see if there is a
                 * box there.
                 */
                else if(!isApplicable(a1, jointAction[a1]) ||!isApplicable(a2, jointAction[a2])){
                    ////System.err.println("[" + destinationRows[a1]  + ","  + destinationCols[a1] + "]" + " = " + this.boxes[destinationRows[a1]][destinationCols[a1]] + "... ACTION: " + jointAction[a1].toString());
                    //System.err.println("11");
                    if(!isApplicable(a1, jointAction[a1])){
                        if(first_Conflict == null){
                            first_Conflict = new Conflict((char)(a1+'0'),"agent",this.g+1,new Point(destinationRows[a1],destinationCols[a1]));
                            counter++;
                        }
                    }
                    if(!isApplicable(a2, jointAction[a2])) {
                        if(first_Conflict==null){
                            first_Conflict = new Conflict((char)(a2+'0'),"agent",this.g+1,new Point(destinationRows[a2],destinationCols[a2]));
                            counter++;
                        }
                    }
                }
            }
        }

        return new State.Pair<>(counter, first_Conflict);
    }


    private boolean isNotContrained(Character agent,int row, int col)
    {
        if(this.manage.isEmpty()){
            return true;
        } else{
            return !this.manage.isAgentConstrained(agent, this.g + 1, new Point(row,col) );
        }
    }

    public void getNumOfBoxesWithSameColor(){
        HashMap<Color,Integer> assinged = new HashMap<>();
        for (int row = 1; row < this.goals.length - 1; row++)
        {
            for (int col = 1; col < this.goals[row].length - 1; col++)
            {
                char goal = this.goals[row][col];
                if ('A' <= goal && goal <= 'Z')
                {
                    if(assinged.containsKey(boxColors[goal-'A'])){
                        int num = assinged.get(boxColors[goal-'A']);
                        assinged.put(boxColors[goal-'A'],num+1);
                    } else {
                        assinged.put(boxColors[goal-'A'],1);
                    }
                }
            }
        }

        for(Color color: assinged.keySet()){
            System.err.println("Color: " + color.toString() + " num of boxes " + assinged.get(color) + " lenght " + assinged.size());
        }
    }
    public static class Pair<F, S> {
        public final F first;
        public final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pair<?, ?> p)) {
                return false;
            }
            return this.first.equals(p.first) && this.second.equals(p.second);
        }

        @Override
        public int hashCode() {
            return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
        }
    }
}
