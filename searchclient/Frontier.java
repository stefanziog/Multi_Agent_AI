package searchclient;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Stack;
import java.util.PriorityQueue;

public interface Frontier
{
    void add(State state);
    State pop();
    boolean isEmpty();
    boolean contains(State state);
    String getName();
}


class FrontierBFS
        implements Frontier
{
    private final ArrayDeque<State> queue = new ArrayDeque<>(65536);
    private final HashSet<State> set = new HashSet<>(65536);

    @Override
    public void add(State state)
    {
        this.queue.addLast(state);
        this.set.add(state);
    }

    @Override
    public State pop()
    {
        State state = this.queue.pollFirst();
        this.set.remove(state);
        return state;
    }

    @Override
    public boolean isEmpty()
    {
        return this.queue.isEmpty();
    }



    @Override
    public boolean contains(State state)
    {
        return !this.set.contains(state);
    }

    @Override
    public String getName()
    {
        return "breadth-first search";
    }
}

class FrontierDFS
        implements Frontier
{
    private final Stack<State> stack = new Stack<>();
    private final HashSet<State> set = new HashSet<>(65536);

    @Override
    public void add(State state)
    {
        this.stack.push(state);
        this.set.add(state);
    }

    @Override
    public State pop()
    {
        State state = this.stack.pop();
        this.set.remove(state);
        return state;
    }

    @Override
    public boolean isEmpty()
    {
        return this.stack.isEmpty();
    }



    @Override
    public boolean contains(State state)
    {
        return !this.set.contains(state);
    }

    @Override
    public String getName()
    {
        return "depth-first search";
    }
}

class FrontierBestFirst
        implements Frontier
{
    private final Heuristic heuristic;
    private final PriorityQueue<State> pqueue;
    private final HashSet<State> set = new HashSet<>(65536);

    public FrontierBestFirst(Heuristic h)
    {
        this.heuristic = h;
        pqueue = new PriorityQueue<>(65536, this.heuristic);
    }

    @Override
    public void add(State state)
    {
        this.pqueue.add(state);
        this.set.add(state);
    }

    @Override
    public State pop()
    {
        State state = this.pqueue.poll();
        this.set.remove(state);
        return state;
    }

    @Override
    public boolean isEmpty()
    {
        return this.pqueue.isEmpty();
    }


    @Override
    public boolean contains(State state)
    {
        return !this.set.contains(state);
    }

    @Override
    public String getName()
    {
        return String.format("best-first search using %s", this.heuristic.toString());
    }
}

class FrontierSubGoal
{
    private final HeuristicSubGoal heuristic;
    private final PriorityQueue<SubGoal> pqueue;

    public FrontierSubGoal(HeuristicSubGoal h)
    {
        this.heuristic = h;
        pqueue = new PriorityQueue<>(65536, this.heuristic);
    }

    public void add(SubGoal state)
    {
        this.pqueue.add(state);
    }

    public SubGoal pop()
    {
        return this.pqueue.poll();
    }

    public boolean isEmpty()
    {
        return this.pqueue.isEmpty();
    }


}

class FrontierCBS
{
    private final HeuristicCBS heuristic;
    private final PriorityQueue<Node> pqueue;
    private final HashSet<Node> set = new HashSet<>(65536);

    public FrontierCBS(HeuristicCBS h)
    {
        this.heuristic = h;
        pqueue = new PriorityQueue<>(65536,this.heuristic);
    }

    public void add(Node state)
    {
        this.pqueue.add(state);
        this.set.add(state);
    }

    public Node pop()
    {
        Node state = this.pqueue.poll();
        this.set.remove(state);
        return state;
    }

    public boolean isEmpty()
    {
        return this.pqueue.isEmpty();
    }



    public boolean contains(Node state)
    {
        return this.set.contains(state);
    }


}

