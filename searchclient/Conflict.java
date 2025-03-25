package searchclient;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Conflict {
    static class Spacetime {
        int timeframe;
        Point point;

        public Spacetime(int timeframe, Point point) {
            this.timeframe = timeframe;
            this.point = point;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Spacetime other)) return false;
            return timeframe == other.timeframe && point.equals(other.point);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + timeframe;
            result = prime * result + point.hashCode();
            return result;
        }
    }

    private final Map<Character, State.Pair<String, Spacetime>> conflict;

    public Conflict(Character agent1, Character agent2, String type1, String type2, int timeframe1, int timeframe2, Point position1, Point position2) {
        conflict = new HashMap<>();
        conflict.put(agent1, new State.Pair<>(type1, new Spacetime(timeframe1, position1)));
        conflict.put(agent2, new State.Pair<>(type2, new Spacetime(timeframe2, position2)));
    }

    public Conflict(Character agent1, String type1, int timeframe1, Point position1) {
        conflict = new HashMap<>();
        conflict.put(agent1, new State.Pair<>(type1, new Spacetime(timeframe1, position1)));
    }

    public Set<Character> getAgents() {
        return conflict.keySet();
    }

    @Override
    public String toString() {
        StringBuilder msg = new StringBuilder();
        for (Character agent : conflict.keySet()) {
            State.Pair<String, Spacetime> pair = conflict.get(agent);
            msg.append("Agent: ").append(agent).append(" conflict type: ").append(pair.first).append(" position: ").append(pair.second.point.toString())
                    .append(" in timeframe: ").append(pair.second.timeframe).append(" conflicting with ");
        }
        return msg.toString();
    }

    public Point getConflictPosition(Character agent) {
        return conflict.get(agent).second.point;
    }

    public int getConflictTimeframe(Character agent) {
        return conflict.get(agent).second.timeframe;
    }
}
