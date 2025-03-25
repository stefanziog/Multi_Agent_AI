package searchclient;
import java.awt.Point;
import java.util.Objects;

public class SubGoal implements Comparable<SubGoal>{

    public Point box;
    public Point goal;
    private int cost;

    public SubGoal(Point box, Point goal){
        this.box = box;
        this.goal = goal;
        this.cost = 1000000;
    }

    public SubGoal(Point box, Point goal, int cost){
        this.box = box;
        this.goal = goal;
        this.cost = cost;
    }

    public void updateCost(int value){
        this.cost = value;
    }
    public int getCost(){
        return this.cost;
    }

    @Override

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        SubGoal other = (SubGoal) obj;
        return Objects.equals(this.box, other.box) && Objects.equals(this.goal, other.goal);
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        if (this.box != null) {
            result = prime * result + this.box.hashCode();
        }

        if (this.goal != null) {
            result = prime * result + this.goal.hashCode();
        }

        return result;
    }
    @Override
    public String toString() {
        String result = "";
        if (this.box != null) {
            result += "Box: " + this.box + " ------> ";
        }
        result += (this.goal != null) ? "Goal: " + this.goal : "null";
        result += " Cost: " + this.getCost();
        return result;
    }
    @Override 
    public int compareTo(SubGoal o)
    {
        return this.cost - o.cost ;
    }
}