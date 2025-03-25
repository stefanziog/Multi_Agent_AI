package searchclient;
import java.awt.Point;

import java.util.Objects;

public class Constrain implements Comparable<Constrain>{
    String type;
    int timeframe;
    Point point;

    public Constrain(int timeframe, Point point, String type){
        this.type = type;
        this.timeframe = timeframe;
        this.point = point;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Constrain other)) return false;
        return timeframe == other.timeframe && Objects.equals(point, other.point) && Objects.equals(type, other.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeframe, point, type);
    }

    @Override
    public int compareTo(Constrain o) {
        return Integer.compare(timeframe, o.timeframe);
    }
}