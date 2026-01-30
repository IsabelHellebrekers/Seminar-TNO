package Objects;

public class Centre {
    public final String centre;      // MSC, FSC 1, FSC 2
    public final Integer maxStorageCapCcls; // may be null/NA
    public final String source;      // source centre (e.g., MSC), may be null
    public final java.time.LocalTime orderTime; // may be null
    public final String timeWindow;  // may be null
    public final Integer drivingTimeToSourceSec; // may be null/NA

    public Centre(String centre, Integer maxStorageCapCcls, String source,
                  java.time.LocalTime orderTime, String timeWindow, Integer drivingTimeToSourceSec) {
        this.centre = centre;
        this.maxStorageCapCcls = maxStorageCapCcls;
        this.source = source;
        this.orderTime = orderTime;
        this.timeWindow = timeWindow;
        this.drivingTimeToSourceSec = drivingTimeToSourceSec;
    }

    @Override public String toString() {
        return "centre='" + centre + '\'' +
                ", maxStorageCapCcls=" + maxStorageCapCcls +
                ", source='" + source + '\'' +
                ", orderTime=" + orderTime +
                ", timeWindow='" + timeWindow + '\'' +
                ", drivingTimeSec=" + drivingTimeToSourceSec +
                '}';
    }
}
