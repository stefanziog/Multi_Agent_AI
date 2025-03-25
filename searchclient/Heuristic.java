package searchclient;

import java.awt.Point;
import java.util.Comparator;

public abstract class Heuristic
        implements Comparator<State>
{
    State initialState;
    int[][][][] realDistanceGrid;
    SubGoal subGoal;

    public Heuristic(State initialState)
    {
        // Here's a chance to pre-process the static parts of the level.
    }


    public Heuristic(State initialState, int[][][][] realDistanceGrid, SubGoal subGoal)
    {
        this.initialState = initialState;
        this.realDistanceGrid = realDistanceGrid;
        this.subGoal = subGoal;
        // Here's a chance to pre-process the static parts of the level.
    }

    public int h(State s) {
        int maxCost = 0; // Use maximum distance any agent needs to cover as the heuristic
        if (this.subGoal == null) {
            return maxCost;
        }

        if (this.subGoal.box != null) {
            Point boxLocation = s.BoxPoint.get(this.subGoal.box);
            int boxRow = (int) boxLocation.getX();
            int boxCol = (int) boxLocation.getY();
            int goalRow = (int) this.subGoal.goal.getX();
            int goalCol = (int) this.subGoal.goal.getY();
            int costToBox = this.realDistanceGrid[s.agentRows[0]][s.agentCols[0]][boxRow][boxCol];
            int costToGoal = this.realDistanceGrid[boxRow][boxCol][goalRow][goalCol];
            maxCost = costToBox + costToGoal;
        } else {
            int goalRow = (int) this.subGoal.goal.getX();
            int goalCol = (int) this.subGoal.goal.getY();
            maxCost = this.realDistanceGrid[s.agentRows[0]][s.agentCols[0]][goalRow][goalCol];
        }

        return maxCost;
    }

    public abstract int f(State s);

    @Override
    public int compare(State s1, State s2)
    {
        return this.f(s1) - this.f(s2);
    }
}

class HeuristicAStar
        extends Heuristic
{
    public HeuristicAStar(State initialState)
    {
        super(initialState);
    }



    @Override
    public int f(State s)
    {
        return s.g() + this.h(s);
    }

    @Override
    public String toString()
    {
        return "A* evaluation";
    }
}

class HeuristicSerializedAstar
        extends Heuristic
{


    public HeuristicSerializedAstar(State initialState, int[][][][] realDistanceGrid, SubGoal subGoal)
    {
        super(initialState,realDistanceGrid,subGoal);
    }

    @Override
    public int f(State s)
    {
        return s.g() + this.h(s);
    }

    @Override
    public String toString()
    {
        return "A* evaluation";
    }
}
class HeuristicMBA extends Heuristic {

    public HeuristicMBA(State initialState, int[][][][] realDistanceGrid, SubGoal subGoal) {
        super(initialState, realDistanceGrid, subGoal);
    }



    @Override
    public int f(State s) {
        return s.g() + this.h(s);
    }

    @Override
    public String toString() {
        return "Serialized A* evaluation";
    }
}
class HeuristicWeightedAStar
        extends Heuristic
{
    private final int w;

    public HeuristicWeightedAStar(State initialState, int w)
    {
        super(initialState);
        this.w = w;
    }


    @Override
    public int f(State s)
    {
        return s.g() + this.w * this.h(s);
    }

    @Override
    public String toString()
    {
        return String.format("WA*(%d) evaluation", this.w);
    }
}



class HeuristicGreedy
        extends Heuristic
{
    public HeuristicGreedy(State initialState)
    {
        super(initialState);
    }


    @Override
    public int f(State s)
    {
        return this.h(s);
    }

    @Override
    public String toString()
    {
        return "greedy evaluation";
    }
}

class HeuristicSubGoal
    implements Comparator<SubGoal>
{
    public HeuristicSubGoal()
    {
        super();
    }

    public int f(SubGoal s)
    {
        return s.getCost();
    }


    public String toString()
    {
        return "A* evaluation";
    }

    @Override
    public int compare(SubGoal s1, SubGoal s2)
    {
        return this.f(s1) - this.f(s2);
    }
}

class HeuristicCBS
        implements Comparator<Node>
{
    public Node root;

    public HeuristicCBS(Node initialNode)
    {
        this.root = initialNode;
    }

    public int h(Node node){
        return node.cost;
    }


    public int f(Node s)
    {
        return this.h(s);
    }

    @Override
    public int compare(Node s1, Node s2)
    {
        return this.f(s1) - this.f(s2);
    }

    @Override
    public String toString()
    {
        return "greedy evaluation";
    }
}
