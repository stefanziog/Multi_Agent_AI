package searchclient;
import java.awt.Point;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class Manage {

    HashMap<Character, HashSet<Constrain>> constrains;
    private int hash = 0;

    public Manage(){
        this.constrains = new HashMap<>();
    }

    public Manage(Manage constrain) {
        this.constrains = new HashMap<>();
        for (Character agent : constrain.constrains.keySet()) {
            HashSet<Constrain> copiedSet = new HashSet<>(constrain.constrains.get(agent));
            this.constrains.put(agent, copiedSet);
        }
    }

    public int getMaxTimeFrame(Character agent){
        Constrain maxAgentValueInMap = (Collections.max(this.constrains.get(agent)));
        return maxAgentValueInMap.timeframe;
    }

    public boolean isAgentConstrained(Character agent, int timeframe, Point point){
        Constrain ts = new Constrain(timeframe, point, "agent");
        if(this.constrains.containsKey(agent)){
            //System.err.println("Agent: " + agent + " timeframe: " + timeframe + " Point: " + point.toString());
            return this.constrains.get(agent).contains(ts);
        }
        //System.err.println("Agent: " + agent + " timeframe: " + timeframe + " Point: " + point.toString());
        return false;
    }

    public boolean isEmpty(){
        return this.constrains.isEmpty();
    }

    public boolean append(Character agent, int timeframe, Point point, String type){
        Constrain ts = new Constrain(timeframe, point, type);
        if (!this.constrains.containsKey(agent)) {
            this.constrains.put(agent, new HashSet<>());
        }
        this.constrains.get(agent).add(ts);
        return true;
    }

    @Override
    public int hashCode()
    {
        if (this.hash == 0)
        {
            final int prime = 31;
            int result = 1;
            // for(Character agent: this.constrains.keySet()){
            //     result = prime * result + this.constrains.get(agent).hashCode();
            // }
            result = prime * result + this.constrains.hashCode();
            this.hash = result;
        }
        return this.hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj){ return true; }
        if (obj == null){ return false; }
        if (this.getClass() != obj.getClass()){ return false; }
        Manage nodeObj = (Manage) obj;
        for(Character agent: this.constrains.keySet())
        {
            if (!this.constrains.get(agent).equals(nodeObj.constrains.get(agent)))
            {
                return false;
            }
        }
        System.err.println("Equal objects.....");
        return true
        ;
    }
}
